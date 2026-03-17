/*
 * Deliverable 1 - MongoDB script
 * Contains:
 *  [1] Model (schema validators)
 *  [2] Index strategy
 *  [3] Optimized queries (Q1..Q4)
 *
 * Usage:
 * mongosh "mongodb://datahub:datahub@localhost:27018/admin?authSource=admin&directConnection=true" --file scripts/mongo-deliverables.js
 */

const DATABASE_NAME = "data_hub_test";
const USER_ID = "10001";
const FROM = ISODate("2026-01-01T00:00:00Z");
const TO = ISODate("2026-02-01T00:00:00Z");

const dbRef = db.getSiblingDB(DATABASE_NAME);

function ensureCollectionWithValidator(name, validator) {
  const exists = dbRef.getCollectionInfos({ name }).length > 0;
  if (!exists) {
    dbRef.createCollection(name, {
      validator,
      validationLevel: "moderate"
    });
    print(`Created collection: ${name}`);
    return;
  }

  const result = dbRef.runCommand({
    collMod: name,
    validator,
    validationLevel: "moderate"
  });

  if (result.ok === 1) {
    print(`Updated validator: ${name}`);
  } else {
    print(`Skip validator update for ${name}: ${tojson(result)}`);
  }
}

print("\n=== [1] MODEL (SCHEMA VALIDATORS) ===");

ensureCollectionWithValidator("users", {
  $jsonSchema: {
    bsonType: "object",
    required: ["_id", "email", "name", "status", "created_at"],
    properties: {
      _id: { bsonType: "string" },
      email: { bsonType: "string" },
      name: { bsonType: "string" },
      status: { enum: ["active", "inactive", "blocked"] },
      created_at: { bsonType: "date" }
    }
  }
});

ensureCollectionWithValidator("transactions", {
  $jsonSchema: {
    bsonType: "object",
    required: ["_id", "user_id", "amount", "currency", "status", "created_at"],
    properties: {
      _id: { bsonType: "string" },
      user_id: { bsonType: "string" },
      amount: { bsonType: ["int", "long", "double", "decimal"] },
      currency: { bsonType: "string" },
      status: { enum: ["success", "failed", "pending", "cancelled"] },
      created_at: { bsonType: "date" },

      // Optional denormalized fields for fast read
      day_bucket: { bsonType: ["date", "null"] },
      log_count: { bsonType: ["int", "long", "null"] },
      last_event_type: { bsonType: ["string", "null"] },
      last_event_at: { bsonType: ["date", "null"] },
      last_error_code: { bsonType: ["string", "null"] }
    }
  }
});

ensureCollectionWithValidator("transaction_logs", {
  $jsonSchema: {
    bsonType: "object",
    required: ["_id", "transaction_id", "event_type", "payload", "created_at"],
    properties: {
      _id: { bsonType: "string" },
      transaction_id: { bsonType: "string" },
      event_type: { bsonType: "string" },
      payload: { bsonType: "object" },
      created_at: { bsonType: "date" }
    }
  }
});

// Optional summary collections for reporting
ensureCollectionWithValidator("daily_transaction_summary", {
  $jsonSchema: {
    bsonType: "object",
    required: ["day", "total_amount", "success_total", "failed_total", "tx_count"],
    properties: {
      day: { bsonType: "date" },
      total_amount: { bsonType: ["int", "long", "double", "decimal"] },
      success_total: { bsonType: ["int", "long", "double", "decimal"] },
      failed_total: { bsonType: ["int", "long", "double", "decimal"] },
      tx_count: { bsonType: ["int", "long"] }
    }
  }
});

ensureCollectionWithValidator("user_daily_summary", {
  $jsonSchema: {
    bsonType: "object",
    required: ["user_id", "day", "total_amount", "tx_count"],
    properties: {
      user_id: { bsonType: "string" },
      day: { bsonType: "date" },
      total_amount: { bsonType: ["int", "long", "double", "decimal"] },
      tx_count: { bsonType: ["int", "long"] }
    }
  }
});

print("\n=== [2] CREATE INDEXES ===");

print("transactions: { user_id: 1, created_at: -1 }");
dbRef.transactions.createIndex(
  { user_id: 1, created_at: -1 },
  { name: "idx_tx_user_created_at_desc" }
);

print("transactions: { user_id: 1, status: 1, created_at: -1 }");
dbRef.transactions.createIndex(
  { user_id: 1, status: 1, created_at: -1 },
  { name: "idx_tx_user_status_created_at_desc" }
);

print("transactions: { created_at: -1 }");
dbRef.transactions.createIndex(
  { created_at: -1 },
  { name: "idx_tx_created_at_desc" }
);

print("transaction_logs: { transaction_id: 1, created_at: -1 }");
dbRef.transaction_logs.createIndex(
  { transaction_id: 1, created_at: -1 },
  { name: "idx_tx_logs_txid_created_at_desc" }
);

print("users: { email: 1 } unique");
dbRef.users.createIndex(
  { email: 1 },
  { name: "uk_users_email", unique: true }
);

print("daily_transaction_summary: { day: 1 } unique");
dbRef.daily_transaction_summary.createIndex(
  { day: 1 },
  { name: "uk_daily_summary_day", unique: true }
);

print("user_daily_summary: { user_id: 1, day: 1 } unique");
dbRef.user_daily_summary.createIndex(
  { user_id: 1, day: 1 },
  { name: "uk_user_daily_summary_user_day", unique: true }
);

print("\n=== [3] OPTIMIZED QUERIES ===");

print("\n-- Q1: Sum amount by user (optimized with status + time range)");
const q1 = [
  {
    $match: {
      user_id: USER_ID,
      status: "success",
      created_at: { $gte: FROM, $lt: TO }
    }
  },
  {
    $group: {
      _id: null,
      total: { $sum: "$amount" }
    }
  }
];
printjson(dbRef.transactions.aggregate(q1).toArray());

print("\n-- Q2: 20 latest transactions by user (projection to reduce payload)");
const q2 = dbRef.transactions
  .find(
    { user_id: USER_ID },
    { _id: 1, user_id: 1, amount: 1, currency: 1, status: 1, created_at: 1 }
  )
  .sort({ created_at: -1 })
  .limit(20)
  .toArray();
printjson(q2);

print("\n-- Q3: Daily total report (match first, then group)");
const q3 = [
  {
    $match: {
      status: "success",
      created_at: { $gte: FROM, $lt: TO }
    }
  },
  {
    $group: {
      _id: { $dateTrunc: { date: "$created_at", unit: "day" } },
      total: { $sum: "$amount" }
    }
  },
  { $sort: { _id: 1 } }
];
printjson(dbRef.transactions.aggregate(q3, { allowDiskUse: true }).toArray());

print("\n-- Q4: Transactions with logs (limit transaction set before lookup)");
const q4 = [
  { $match: { user_id: USER_ID } },
  { $sort: { created_at: -1 } },
  { $limit: 20 },
  {
    $lookup: {
      from: "transaction_logs",
      let: { txId: "$_id" },
      pipeline: [
        { $match: { $expr: { $eq: ["$transaction_id", "$$txId"] } } },
        { $sort: { created_at: -1 } },
        { $limit: 50 },
        { $project: { _id: 1, transaction_id: 1, event_type: 1, created_at: 1 } }
      ],
      as: "logs"
    }
  },
  {
    $project: {
      _id: 1,
      user_id: 1,
      amount: 1,
      currency: 1,
      status: 1,
      created_at: 1,
      logs: 1
    }
  }
];
printjson(dbRef.transactions.aggregate(q4, { allowDiskUse: true }).toArray());

print("\n=== [4] OPTIONAL EXPLAIN COMMANDS ===");
print("db.transactions.find({ user_id: \"10001\" }).sort({ created_at: -1 }).limit(20).explain(\"executionStats\")");
print("db.transactions.aggregate(q1).explain(\"executionStats\")");
print("db.transactions.aggregate(q4).explain(\"executionStats\")");
