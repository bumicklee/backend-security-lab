// 결제 웹훅 서명 실습 도우미: 서명된 curl 명령을 출력한다.
// 사용: node scripts/sign-webhook.js [이벤트ID] [기준 URL]
import { signWebhook } from "../src/routes/webhooks.js";

const id = process.argv[2] ?? `evt_${Date.now()}`;
const base = process.argv[3] ?? "http://localhost:3000";
const secret = process.env.WEBHOOK_SECRET ?? "whsec_lab_only_example";
const body = JSON.stringify({ id, type: "payment.succeeded", data: { orderId: 42, amount: 39000, cardNo: "4111-1111-1111-1111" } });
const ts = Math.floor(Date.now() / 1000);
const sig = signWebhook(secret, ts, body);

console.log(`curl -s ${base}/api/webhooks/payment \\
  -H "Content-Type: application/json" \\
  -H "X-Webhook-Timestamp: ${ts}" \\
  -H "X-Webhook-Signature: ${sig}" \\
  -d '${body}'`);
