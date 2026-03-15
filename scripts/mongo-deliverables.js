/*
 * Deliverable 1 - MongoDB optimization script
 * Includes:
 *  - index creation
 *  - optimized queries for Query 1..4
 *
 * Usage example:
 * mongosh "mongodb://datahub:datahub@localhost:27018/admin?authSource=admin&directConnection=true" --file scripts/mongo-deliverables.js
 */

const DATABASE_NAME = "data_hub";
const USER_ID = "10001";
const FROM = ISODate("2026-01-01T00:00:00Z");
const TO = ISODate("2026-02-01T00:00:00Z");

const dbRef = db.getSiblingDB(DATABASE_NAME);

print("\n=== [1] CREATE INDEXES ===");

print("Create index: transactions { user_id: 1, created_at: -1 }");
dbRef.transactions.createIndex(
  { user_id: 1, created_at: -1 },
  { name: "idx_tx_user_created_at_desc" }
);

print("Create index: transactions { user_id: 1, status: 1, created_at: -1 }");
dbRef.transactions.createIndex(
  { user_id: 1, status: 1, created_at: -1 },
  { name: "idx_tx_user_status_created_at_desc" }
);

print("Create index: transactions { created_at: -1 }");
dbRef.transactions.createIndex(
  { created_at: -1 },
  { name: "idx_tx_created_at_desc" }
);

print("Create index: transaction_logs { transaction_id: 1, created_at: -1 }");
dbRef.transaction_logs.createIndex(
  { transaction_id: 1, created_at: -1 },
  { name: "idx_tx_logs_txid_created_at_desc" }
);

print("\n=== [2] OPTIMIZED QUERIES ===");

print("\n-- Query 1: Sum amount by user (optimized with status + time range)");
const query1 = [
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
printjson(dbRef.transactions.aggregate(query1).toArray());

print("\n-- Query 2: 20 latest transactions by user (optimized with projection)");
const query2 = dbRef.transactions
  .find(
    { user_id: USER_ID },
    { _id: 1, user_id: 1, amount: 1, currency: 1, status: 1, created_at: 1 }
  )
  .sort({ created_at: -1 })
  .limit(20)
  .toArray();
printjson(query2);

print("\n-- Query 3: Daily total report (optimized with match first)");
const query3 = [
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
printjson(dbRef.transactions.aggregate(query3, { allowDiskUse: true }).toArray());

print("\n-- Query 4: Transactions with logs (optimized lookup)");
const query4 = [
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
printjson(dbRef.transactions.aggregate(query4, { allowDiskUse: true }).toArray());

print("\n=== [3] OPTIONAL: EXPLAIN (executionStats) ===");
print("Use these commands manually to verify index usage:");
print("db.transactions.find({ user_id: \"10001\" }).sort({ created_at: -1 }).limit(20).explain(\"executionStats\")");
print("db.transactions.aggregate(query1).explain(\"executionStats\")");
print("db.transactions.aggregate(query4).explain(\"executionStats\")");
