// 5-2 회원 저장·조회: 전화번호는 봉투 암호화해 저장하고, 검색은 블라인드 인덱스로만 한다.
import { fromColumns, toColumns } from "../lib/envelope.js";
import { SEED_MEMBERS } from "./seed-data.js";

export function createMemberRepo(prisma, fieldCrypto) {
  return {
    async create({ nickname, email, phone, withdrawnAt = null }) {
      const enc = await fieldCrypto.encrypt(phone);
      // 저장: phone_ct(+iv·tag·감싼 키), phone_idx. 평문 번호 컬럼은 없다
      return prisma.member.create({
        data: { nickname, email, ...toColumns(enc), phoneIdx: fieldCrypto.blindIndex(phone), withdrawnAt },
      });
    },
    // 조회: WHERE phone_idx = ? (탈퇴 회원 제외)
    findByPhone(phone) {
      return prisma.member.findMany({
        where: { phoneIdx: fieldCrypto.blindIndex(phone), withdrawnAt: null }, orderBy: { id: "asc" } });
    },
    findById(id) {
      return prisma.member.findFirst({ where: { id, withdrawnAt: null } });
    },
    async phoneOf(member) {
      return member.phoneCt ? fieldCrypto.decrypt(fromColumns(member)) : null;
    },
  };
}

export async function seedMembers(prisma, repo, now = Date.now()) {
  if ((await prisma.member.count()) > 0) return;
  for (const { withdrawnDaysAgo, ...m } of SEED_MEMBERS) {
    const withdrawnAt = withdrawnDaysAgo ? new Date(now - withdrawnDaysAgo * 86_400_000) : null;
    await repo.create({ ...m, withdrawnAt });
  }
}
