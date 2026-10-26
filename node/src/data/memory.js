// DATA_BACKEND=memory: DB 없이 실행·테스트하기 위한 대역.
// - prisma 대역: pg-mem(메모리 PostgreSQL 에뮬레이터) 위에 Prisma와 같은 모양의 API를 얹었다.
//   $queryRaw 는 값을 $1, $2 로 바인딩하고, $queryRawUnsafe 는 문자열을 그대로 실행한다.
//   그래서 SQL 인젝션 실습 결과가 실제 PostgreSQL과 같은 모양으로 재현된다.
// - mongo 대역: findOne 이 $ne·$gt 같은 연산자를 해석하는 작은 컬렉션.
import { newDb } from "pg-mem";
import { hashScrypt } from "../lib/password.js";
import {
  SEED_DB_USERS, SEED_LOGIN_USERS, SEED_ORDERS, SEED_POSTS, SEED_PRODUCTS, SEED_XSS_POST, loginUserDoc,
} from "./seed-data.js";

const DDL = `
CREATE TABLE posts (
  id SERIAL PRIMARY KEY,
  title VARCHAR(100) NOT NULL,
  content TEXT NOT NULL DEFAULT '',
  author VARCHAR(50) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE products (id SERIAL PRIMARY KEY, name VARCHAR(100) NOT NULL, price INTEGER NOT NULL);
CREATE TABLE orders (
  id SERIAL PRIMARY KEY,
  user_id VARCHAR(50) NOT NULL,
  product_id INTEGER NOT NULL,
  qty INTEGER NOT NULL,
  total INTEGER NOT NULL,
  coupon VARCHAR(8),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE users (
  id SERIAL PRIMARY KEY,
  email TEXT NOT NULL,
  password_hash TEXT NOT NULL,
  role VARCHAR(10) NOT NULL
);
CREATE TABLE members (
  id SERIAL PRIMARY KEY,
  nickname VARCHAR(30) NOT NULL,
  email VARCHAR(100) NOT NULL,
  phone_ct TEXT,
  phone_iv TEXT,
  phone_tag TEXT,
  phone_key TEXT,
  phone_idx CHAR(64),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  withdrawn_at TIMESTAMPTZ
);
`;

const snake = (s) => s.replace(/[A-Z]/g, (c) => `_${c.toLowerCase()}`);
const camel = (s) => s.replace(/_([a-z])/g, (_, c) => c.toUpperCase());
const toCamel = (row) => Object.fromEntries(Object.entries(row).map(([k, v]) => [camel(k), v]));

// Prisma 모델 API 중 이 서비스가 쓰는 부분만 흉내 낸다
function model(client, table) {
  // 값은 = 비교, null 은 IS NULL, { lt, lte, gt, gte } 는 범위 비교 (Prisma 와 같은 모양)
  const OPS = { lt: "<", lte: "<=", gt: ">", gte: ">=" };
  const whereSql = (where = {}, params) => {
    const parts = [];
    for (const [k, v] of Object.entries(where)) {
      if (v === null) {
        parts.push(`${snake(k)} IS NULL`);
      } else if (typeof v === "object" && !(v instanceof Date)) {
        for (const [op, val] of Object.entries(v)) {
          if (!OPS[op]) throw new Error(`지원하지 않는 조건: ${op}`);
          params.push(val);
          parts.push(`${snake(k)} ${OPS[op]} $${params.length}`);
        }
      } else {
        params.push(v);
        parts.push(`${snake(k)} = $${params.length}`);
      }
    }
    return parts.length ? ` WHERE ${parts.join(" AND ")}` : "";
  };
  // select: { id: true, nickname: true } → SELECT id, nickname
  const columns = (select) =>
    select ? Object.keys(select).filter((k) => select[k]).map((k) => snake(k)).join(", ") : "*";
  const q = async (sql, params) => (await client.query(sql, params)).rows.map(toCamel);
  return {
    async findMany({ where, select, orderBy, take, skip } = {}) {
      const params = [];
      let sql = `SELECT ${columns(select)} FROM ${table}${whereSql(where, params)}`;
      if (orderBy) {
        const [[k, dir]] = Object.entries(orderBy);
        sql += ` ORDER BY ${snake(k)} ${dir === "desc" ? "DESC" : "ASC"}`;
      }
      if (take) sql += ` LIMIT ${Number(take)}`;
      if (skip) sql += ` OFFSET ${Number(skip)}`;
      return q(sql, params);
    },
    async count({ where } = {}) {
      const params = [];
      const rows = await q(`SELECT count(*) AS n FROM ${table}${whereSql(where, params)}`, params);
      return Number(rows[0].n);
    },
    async findFirst({ where } = {}) {
      const params = [];
      const rows = await q(`SELECT * FROM ${table}${whereSql(where, params)} LIMIT 1`, params);
      return rows[0] ?? null;
    },
    async findUnique({ where }) {
      const params = [];
      const rows = await q(`SELECT * FROM ${table}${whereSql(where, params)} LIMIT 1`, params);
      return rows[0] ?? null;
    },
    async create({ data }) {
      const keys = Object.keys(data).filter((k) => data[k] !== undefined);
      const params = keys.map((k) => data[k]);
      const sql = `INSERT INTO ${table} (${keys.map(snake).join(", ")}) VALUES (${keys
        .map((_, i) => `$${i + 1}`)
        .join(", ")}) RETURNING *`;
      return (await q(sql, params))[0];
    },
    async update({ where, data }) {
      const keys = Object.keys(data).filter((k) => data[k] !== undefined);
      const params = keys.map((k) => data[k]);
      const set = keys.map((k, i) => `${snake(k)} = $${i + 1}`).join(", ");
      const rows = await q(`UPDATE ${table} SET ${set}${whereSql(where, params)} RETURNING *`, params);
      return rows[0] ?? null;
    },
    async delete({ where }) {
      const params = [];
      const rows = await q(`DELETE FROM ${table}${whereSql(where, params)} RETURNING *`, params);
      return rows[0] ?? null;
    },
    async deleteMany({ where } = {}) {
      const params = [];
      const rows = await q(`DELETE FROM ${table}${whereSql(where, params)} RETURNING id`, params);
      return { count: rows.length };
    },
    async updateMany({ where, data }) {
      const keys = Object.keys(data).filter((k) => data[k] !== undefined);
      const params = keys.map((k) => data[k]);
      const set = keys.map((k, i) => `${snake(k)} = $${i + 1}`).join(", ");
      const rows = await q(`UPDATE ${table} SET ${set}${whereSql(where, params)} RETURNING id`, params);
      return { count: rows.length };
    },
  };
}

export async function createMemoryPrisma() {
  const db = newDb();
  db.public.none(DDL);
  const { Client } = db.adapters.createPg();
  const client = new Client();
  await client.connect();

  const prisma = {
    // 태그드 템플릿: ${} 자리는 문자열로 이어 붙이지 않고 $1, $2 파라미터로 보낸다
    async $queryRaw(strings, ...values) {
      const sql = strings.reduce((acc, s, i) => acc + s + (i < values.length ? `$${i + 1}` : ""), "");
      return (await client.query(sql, values)).rows;
    },
    // 문자열을 그대로 실행한다 (취약 실습에서만 사용)
    async $queryRawUnsafe(sql, ...values) {
      return (await client.query(sql, values)).rows;
    },
    async $disconnect() {},
    post: model(client, "posts"),
    product: model(client, "products"),
    order: model(client, "orders"),
    member: model(client, "members"), // 5-2 암호화 컬럼 · 5-4 파기 · 5-5 내보내기
  };

  for (const p of SEED_POSTS) await prisma.post.create({ data: p });
  await prisma.post.create({ data: SEED_XSS_POST });
  for (const p of SEED_PRODUCTS) await prisma.product.create({ data: p });
  for (const o of SEED_ORDERS) await prisma.order.create({ data: o });
  for (const u of SEED_DB_USERS) {
    await client.query("INSERT INTO users (email, password_hash, role) VALUES ($1, $2, $3)", [
      u.email,
      u.passwordHash,
      u.role,
    ]);
  }
  return prisma;
}

// ---- MongoDB 대역 ----
function matchValue(actual, cond) {
  const isOperatorObject =
    typeof cond === "object" && cond !== null && !Array.isArray(cond) &&
    Object.keys(cond).some((k) => k.startsWith("$"));
  if (!isOperatorObject) return actual === cond;
  return Object.entries(cond).every(([op, v]) => {
    switch (op) {
      case "$eq": return actual === v;
      case "$ne": return actual !== v;
      case "$gt": return actual > v;
      case "$gte": return actual >= v;
      case "$lt": return actual < v;
      case "$lte": return actual <= v;
      case "$in": return Array.isArray(v) && v.includes(actual);
      case "$exists": return (actual !== undefined) === Boolean(v);
      case "$regex": return typeof actual === "string" && new RegExp(v).test(actual);
      default: throw new Error(`지원하지 않는 연산자: ${op}`);
    }
  });
}

export function createMemoryCollection(docs = []) {
  const rows = docs.map((d, i) => ({ _id: i + 1, ...d }));
  const matches = (doc, query) => Object.entries(query).every(([k, cond]) => matchValue(doc[k], cond));
  return {
    async findOne(query = {}) {
      return rows.find((d) => matches(d, query)) ?? null;
    },
    find(query = {}) {
      return { toArray: async () => rows.filter((d) => matches(d, query)) };
    },
    async insertOne(doc) {
      rows.push({ _id: rows.length + 1, ...doc });
      return { acknowledged: true };
    },
    // { $set: {...} } 만 지원한다 (3-1 해시 이전, 4-1 프로필 수정에서 사용)
    async updateOne(query, update) {
      const doc = rows.find((d) => matches(d, query));
      if (!doc) return { acknowledged: true, matchedCount: 0, modifiedCount: 0 };
      Object.assign(doc, update.$set ?? {});
      return { acknowledged: true, matchedCount: 1, modifiedCount: 1 };
    },
    async countDocuments() {
      return rows.length;
    },
  };
}

export async function createMemoryMongo() {
  // 시드 비밀번호는 예전 형식(scrypt)으로 넣는다 → 로그인하면 Argon2id 로 바뀐다 (3-1)
  const users = [];
  for (const u of SEED_LOGIN_USERS) {
    users.push(loginUserDoc(u, await hashScrypt(u.password)));
  }
  // 취약 실습용 컬렉션: 비밀번호를 평문으로 저장한 "나쁜 예"
  const labUsers = SEED_LOGIN_USERS.map(({ username, role, password }) => ({ username, role, password }));
  return {
    users: createMemoryCollection(users),
    labUsers: createMemoryCollection(labUsers),
    async close() {},
  };
}
