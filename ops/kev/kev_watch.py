#!/usr/bin/env python3
"""kev_watch.py (강의 7-3 버전) - Trivy JSON 결과를 CISA KEV와 FIRST EPSS에 대조해 등급을 매긴다.

교재 18장 kev_watch.py와 같은 판정 규칙(P1 = KEV 등재, P2 = HIGH 이상 + EPSS 기준 이상)이며,
화면 폭(87자)에 맞게 출력 열을 줄였다. 표준 라이브러리만 쓴다.

  trivy rootfs -q --scanners vuln -f json -o /var/tmp/host.json /
  python3 kev_watch.py /var/tmp/host.json --state /var/lib/kev_watch/state.json
  (테스트) python3 kev_watch.py mock/host.json --kev-file mock/kev.json --epss-file mock/epss.json
"""
import argparse, json, os, sys, urllib.request

KEV_URL = "https://www.cisa.gov/sites/default/files/feeds/known_exploited_vulnerabilities.json"
EPSS_URL = "https://api.first.org/data/v1/epss?cve="
HIGH = {"CRITICAL", "HIGH"}


def get_json(url):
    req = urllib.request.Request(url, headers={"User-Agent": "kev-watch/1.0"})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.load(r)


def load_findings(path):
    doc = json.load(open(path))
    out = []
    for res in doc.get("Results") or []:
        for v in res.get("Vulnerabilities") or []:
            out.append(dict(cve=v["VulnerabilityID"], pkg=v.get("PkgName", ""),
                            fixed=v.get("FixedVersion", ""), severity=(v.get("Severity") or "").upper()))
    return out


def load_epss(cves, offline=None):
    if offline:
        return {d["cve"]: float(d["epss"]) for d in json.load(open(offline))["data"]}
    scores, cves = {}, sorted(c for c in cves if c.startswith("CVE-"))
    for i in range(0, len(cves), 100):   # 100개씩 묶어 조회
        data = get_json(EPSS_URL + ",".join(cves[i:i + 100]))
        scores.update({d["cve"]: float(d["epss"]) for d in data.get("data", [])})
    return scores


def priority(f, kev, epss, epss_min):
    if f["cve"] in kev:
        return "P1"   # 실제 악용 확인
    if f["severity"] in HIGH and epss.get(f["cve"], 0) >= epss_min:
        return "P2"   # 악용 가능성 높음
    if f["severity"] in HIGH:
        return "P3"
    return "P4"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("reports", nargs="+")
    ap.add_argument("--epss-min", type=float, default=0.1)
    ap.add_argument("--state")
    ap.add_argument("--kev-file")
    ap.add_argument("--epss-file")
    a = ap.parse_args()

    findings = [f for p in a.reports for f in load_findings(p)]
    kev_doc = json.load(open(a.kev_file)) if a.kev_file else get_json(KEV_URL)
    kev = {v["cveID"]: v for v in kev_doc["vulnerabilities"]}
    epss = load_epss({f["cve"] for f in findings}, a.epss_file)
    seen = set()
    if a.state and os.path.exists(a.state):
        seen = set(json.load(open(a.state)).get("kev_hits", []))

    rows = {}
    for f in findings:
        rows[(f["cve"], f["pkg"])] = (priority(f, kev, epss, a.epss_min), f, epss.get(f["cve"]))
    rows = sorted(rows.values(), key=lambda r: (r[0], -(r[2] or 0)))
    hits = {f["cve"] for p, f, _ in rows if p == "P1"}
    for p, f, e in rows:
        if p == "P4":
            continue
        new = "  NEW KEV" if p == "P1" and f["cve"] not in seen else ""
        es = f"{e:.3f}" if e is not None else "-"
        print(f"{p:<3} {f['cve']:<15} {es:>5}  {f['severity']:<9} {f['pkg'][:12]:<12} → {f['fixed'] or '없음'}{new}")
        if new:
            k = kev[f["cve"]]
            print(f"    KEV {k['dateAdded']} · CISA 기한 {k['dueDate']} · 랜섬웨어 {k['knownRansomwareCampaignUse']}")
    counts = {g: sum(1 for r in rows if r[0] == g) for g in ("P1", "P2", "P3", "P4")}
    print("요약:", ", ".join(f"{g} {n}건" for g, n in counts.items()))
    if a.state:
        os.makedirs(os.path.dirname(os.path.abspath(a.state)), exist_ok=True)
        json.dump({"kev_hits": sorted(hits | seen)}, open(a.state, "w"))
    sys.exit(2 if counts["P1"] else 0)   # P1이 있으면 종료 코드 2 → 알림


if __name__ == "__main__":
    main()
