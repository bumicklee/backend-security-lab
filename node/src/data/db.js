// DATA_BACKEND=db: 실제 PostgreSQL(Prisma) + MongoDB 에 연결한다.
import { hashScrypt } from "../lib/password.js";
import {
  SEED_DB_USERS, SEED_LOGIN_USERS, SEED_ORDERS, SEED_POSTS, SEED_PRODUCTS, SEED_XSS_POST, loginUserDoc,
} from "./seed-data.js";

export async function createDbPrisma(databaseUrl) {
  // 메모리 모드·테스트에서는 Prisma를 아예 불러오지 않도록 동적 import
  const { PrismaClient } = await import("@prisma/client");
  const { PrismaPg } = await import("@prisma/adapter-pg");
  const prisma = new PrismaClient({ adapter: new PrismaPg({ connectionString: databaseUrl }) });
  if ((await prisma.post.count()) === 0) {
    await prisma.post.createMany({ data: SEED_POSTS });
    await prisma.post.create({ data: SEED_XSS_POST }); // 4-2: id 77 고정 (자동 번호는 6부터 그대로)
    await prisma.product.createMany({ data: SEED_PRODUCTS, skipDuplicates: true });
    await prisma.user.createMany({ data: SEED_DB_USERS });
  }
  if ((await prisma.order.count({ where: { id: { gte: 1000, lte: 1010 } } })) === 0) {
    await prisma.order.createMany({ data: SEED_ORDERS, skipDuplicates: true });
    // 시드에 id 를 직접 넣었으므로 자동 번호를 그 뒤로 옮긴다
    await prisma.$executeRawUnsafe(
      "SELECT setval(pg_get_serial_sequence('orders', 'id'), GREATEST((SELECT MAX(id) FROM orders), 1))",
    );
  }
  return prisma;
}

export async function createDbMongo(mongoUrl, dbName) {
  const { MongoClient } = await import("mongodb");
  const client = new MongoClient(mongoUrl, { serverSelectionTimeoutMS: 5000 });
  await client.connect();
  const db = client.db(dbName);
  const users = db.collection("users");
  const labUsers = db.collection("lab_users");
  if ((await users.countDocuments()) === 0) {
    // 시드 비밀번호는 예전 형식(scrypt)으로 넣는다 → 로그인하면 Argon2id 로 바뀐다 (3-1)
    for (const u of SEED_LOGIN_USERS) {
      await users.insertOne(loginUserDoc(u, await hashScrypt(u.password)));
    }
    await users.createIndex({ username: 1 }, { unique: true });
  }
  if ((await labUsers.countDocuments()) === 0) {
    // 취약 실습용: 평문 비밀번호 컬렉션 ("나쁜 예")
    for (const { username, role, password } of SEED_LOGIN_USERS) {
      await labUsers.insertOne({ username, role, password });
    }
  }
  return { users, labUsers, close: () => client.close() };
}
