// 실습용 시드 데이터. 이메일·해시·비밀번호는 모두 가짜 값이다.
export const SEED_POSTS = [
  { title: "공지", content: "실습 게시판입니다.", author: "admin" },
  { title: "첫 글", content: "안녕하세요.", author: "alice" },
  { title: "질문 있어요", content: "업로드가 안 돼요.", author: "alice" },
  { title: "공지 2", content: "점검 안내", author: "admin" },
  { title: "자유 글", content: "오늘 날씨", author: "bob" },
];

// 4-2 XSS: 공격자가 이미 저장해 둔 글 (/posts/77/view). 번호를 고정하려고 id 를 직접 넣는다.
// 새 글은 6번부터 매겨진다 (실습에서 글을 70개 넘게 만들면 77번과 겹칠 수 있다).
export const SEED_XSS_POST = {
  id: 77,
  title: "이벤트 안내",
  content: "<img src=x onerror=alert(document.cookie)>",
  author: "bob",
};

export const SEED_PRODUCTS = [
  { id: 7, name: "기계식 키보드", price: 39000 },
  { id: 8, name: "마우스 패드", price: 9000 },
  { id: 9, name: "USB 허브", price: 12000 },
  { id: 10, name: "모니터 받침대", price: 30000 },
];

// SQL 인젝션 UNION 실습에서 꺼내 보는 회원 테이블 (게시판 DB 쪽)
export const SEED_DB_USERS = [
  { email: "admin@example.com", passwordHash: "$2a$10$EXAMPLEHASH0EXAMPLEHASH0EXAMPLEHASH0", role: "ADMIN" },
  { email: "alice@example.com", passwordHash: "$2a$10$EXAMPLEHASH1EXAMPLEHASH1EXAMPLEHASH1", role: "USER" },
];

// 로그인 계정 (MongoDB users 컬렉션). 비밀번호는 시드할 때 scrypt 해시로 저장한다.
// nickname·bio 는 4-1 프로필 수정 대상, resetToken 은 "응답에 딸려 나가면 안 되는 내부 필드" 예시(가짜 값)
export const SEED_LOGIN_USERS = [
  { username: "admin", email: "admin@example.com", role: "ADMIN", password: "admin-lab-pass",
    nickname: "관리자", bio: "", resetToken: "rt_EXAMPLE_admin_0000" },
  { username: "alice", email: "alice@example.com", role: "USER", password: "alice-lab-pass",
    nickname: "alice", bio: "", resetToken: "rt_EXAMPLE_alice_0000" },
  { username: "bob", email: "bob@example.com", role: "USER", password: "bob-lab-pass",
    nickname: "bob", bio: "", resetToken: "rt_EXAMPLE_bob_0000" },
];

// MongoDB users 문서 모양 (비밀번호 원문 대신 해시)
export const loginUserDoc = ({ password: _password, ...u }, passwordHash) => ({ ...u, passwordHash });

// 3-4·3-7 주문 1000~1010. 1001 은 alice(USB 허브 2개 = 24,000원), 1002 는 bob(모니터 받침대 = 30,000원).
const ORDER_ROWS = [
  ["admin", 8, 1], ["alice", 9, 2], ["bob", 10, 1], ["alice", 8, 2], ["bob", 7, 1], ["alice", 9, 1],
  ["bob", 8, 3], ["alice", 7, 2], ["bob", 9, 3], ["alice", 10, 1], ["bob", 7, 1],
];
const PRICE = Object.fromEntries(SEED_PRODUCTS.map((p) => [p.id, p.price]));
export const SEED_ORDERS = ORDER_ROWS.map(([userId, productId, qty], i) => ({
  id: 1000 + i,
  userId,
  productId,
  qty,
  total: PRICE[productId] * qty,
}));

// 5-2·5-4·5-5 회원(가짜 연락처). 전화번호는 저장할 때 봉투 암호화 + 블라인드 인덱스로 바뀐다.
// carol 은 400일 전에 탈퇴한 회원 → 파기 배치(purgeExpired)가 지운다
export const SEED_MEMBERS = [
  { nickname: "alice", email: "alice@example.com", phone: "010-2345-6789" },
  { nickname: "bob", email: "bob@example.com", phone: "010-3456-7890" },
  { nickname: "carol", email: "carol@example.com", phone: "010-4567-8901", withdrawnDaysAgo: 400 },
];
