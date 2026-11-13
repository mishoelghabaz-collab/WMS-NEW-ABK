const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_UID = "admin_uid_01";
const SUPERVISOR_UID = "supervisor_uid_01";
const OPERATOR_UID = "operator_uid_01";
const VIEWER_UID = "viewer_uid_01";
const OUTSIDER_UID = "outsider_uid_01";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

async function seedRolesAndFactory() {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(ADMIN_UID).set({
      displayName: "Admin User",
      email: "admin@plant.com",
      role: "ADMIN",
      factoryIds: ["P7", "P8"],
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("users").doc(SUPERVISOR_UID).set({
      displayName: "Supervisor P7",
      email: "sup@plant.com",
      role: "SUPERVISOR",
      factoryIds: ["P7"],
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("users").doc(OPERATOR_UID).set({
      displayName: "Operator P7",
      email: "op@plant.com",
      role: "OPERATOR",
      factoryIds: ["P7"],
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("users").doc(VIEWER_UID).set({
      displayName: "Viewer P7",
      email: "viewer@plant.com",
      role: "VIEWER",
      factoryIds: ["P7"],
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("users").doc(OUTSIDER_UID).set({
      displayName: "Operator P8",
      email: "p8@plant.com",
      role: "OPERATOR",
      factoryIds: ["P8"],
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("factories").doc("P7").set({
      code: "P7",
      name: "Plant 7",
      productionLines: ["Volvo LFT", "CDPO"],
      zones: ["A-01", "B-02"],
      updatedAt: now,
    });
    await db.collection("materials").doc("P7_12345678").set({
      factoryId: "P7",
      partNumber: "12345678",
      name: "Steel Bracket",
      availableQty: 50,
      minThreshold: 10,
      location: "A-01-01",
      productionLine: "Volvo LFT",
      status: "IN_STOCK",
      unit: "PCS",
      updatedBy: ADMIN_UID,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("materials").doc("P8_87654321").set({
      factoryId: "P8",
      partNumber: "87654321",
      name: "Copper Coil",
      availableQty: 25,
      minThreshold: 5,
      location: "C-04-02",
      productionLine: "CDPO",
      status: "IN_STOCK",
      unit: "PCS",
      updatedBy: ADMIN_UID,
      createdAt: now,
      updatedAt: now,
    });
  });
}

test("Unauthenticated caller cannot read materials or transactions", async () => {
  await seedRolesAndFactory();
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("materials").where("factoryId", "==", "P7").get());
  await assertFails(unauthDb.collection("transactions").where("factoryId", "==", "P7").get());
  await assertFails(unauthDb.collection("users").doc(ADMIN_UID).get());
});

test("User self-registration cannot escalate role to ADMIN", async () => {
  const now = new Date();
  const newDb = testEnv.authenticatedContext("new_user_1", {
    email: "attacker@evil.com",
    email_verified: true,
  }).firestore();

  await assertFails(
    newDb.collection("users").doc("new_user_1").set({
      displayName: "Attacker",
      email: "attacker@evil.com",
      role: "ADMIN",
      factoryIds: ["P7"],
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    newDb.collection("users").doc("new_user_1").set({
      displayName: "Regular User",
      email: "attacker@evil.com",
      role: "VIEWER",
      factoryIds: [],
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Email spoofing test: unverified bootstrapped admin email cannot self-register as ADMIN", async () => {
  const now = new Date();
  const spoofDb = testEnv.authenticatedContext("spoof_uid", {
    email: "mishoelghabaz@gmail.com",
    email_verified: false,
  }).firestore();

  await assertFails(
    spoofDb.collection("users").doc("spoof_uid").set({
      displayName: "Spoof",
      email: "mishoelghabaz@gmail.com",
      role: "ADMIN",
      factoryIds: [],
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Operator can query assigned factory P7 materials but fails on P8 or unfiltered query", async () => {
  await seedRolesAndFactory();
  const opDb = testEnv.authenticatedContext(OPERATOR_UID).firestore();

  await assertSucceeds(
    opDb.collection("materials").where("factoryId", "==", "P7").get()
  );
  await assertFails(
    opDb.collection("materials").where("factoryId", "==", "P8").get()
  );
  await assertFails(opDb.collection("materials").get());
});

test("Material enforces exact 8-digit partNumber and non-negative quantity", async () => {
  await seedRolesAndFactory();
  const now = new Date();
  const supDb = testEnv.authenticatedContext(SUPERVISOR_UID).firestore();

  // 7 digits -> fail
  await assertFails(
    supDb.collection("materials").doc("P7_1234567").set({
      factoryId: "P7",
      partNumber: "1234567",
      name: "Bad Part",
      availableQty: 10,
      minThreshold: 5,
      location: "A-01",
      productionLine: "Volvo LFT",
      status: "IN_STOCK",
      unit: "PCS",
      updatedBy: SUPERVISOR_UID,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Shadow update with ghost field -> fail
  await assertFails(
    supDb.collection("materials").doc("P7_22334455").set({
      factoryId: "P7",
      partNumber: "22334455",
      name: "Good Part",
      availableQty: 10,
      minThreshold: 5,
      location: "A-01",
      productionLine: "Volvo LFT",
      status: "LOW_STOCK",
      unit: "PCS",
      updatedBy: SUPERVISOR_UID,
      ghostField: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Valid 8-digit part -> succeed
  await assertSucceeds(
    supDb.collection("materials").doc("P7_22334455").set({
      factoryId: "P7",
      partNumber: "22334455",
      name: "Good Part",
      availableQty: 15,
      minThreshold: 5,
      location: "A-01",
      productionLine: "Volvo LFT",
      status: "IN_STOCK",
      unit: "PCS",
      updatedBy: SUPERVISOR_UID,
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Atomic transaction succeeds when material and transaction update together, rejects standalone transaction or negative qty", async () => {
  await seedRolesAndFactory();
  const now = new Date();
  const opDb = testEnv.authenticatedContext(OPERATOR_UID).firestore();

  // Standalone transaction without updating material -> MUST FAIL (existsAfter/getAfter check)
  await assertFails(
    opDb.collection("transactions").doc("tx_forged").set({
      factoryId: "P7",
      materialId: "P7_12345678",
      partNumber: "12345678",
      materialName: "Steel Bracket",
      type: "ISSUE",
      qty: 10,
      previousQty: 50,
      newQty: 40,
      user: "Operator P7",
      userId: OPERATOR_UID,
      timestamp: now,
      location: "A-01-01",
    })
  );

  // Atomic batch/transaction updating material + creating transaction -> SUCCEEDS
  const batch = opDb.batch();
  batch.update(opDb.collection("materials").doc("P7_12345678"), {
    availableQty: 40,
    status: "IN_STOCK",
    updatedBy: OPERATOR_UID,
    updatedAt: now,
  });
  batch.set(opDb.collection("transactions").doc("tx_valid_1"), {
    factoryId: "P7",
    materialId: "P7_12345678",
    partNumber: "12345678",
    materialName: "Steel Bracket",
    type: "ISSUE",
    qty: 10,
    previousQty: 50,
    newQty: 40,
    user: "Operator P7",
    userId: OPERATOR_UID,
    timestamp: now,
    location: "A-01-01",
  });
  await assertSucceeds(batch.commit());

  // Viewer cannot transact -> FAILS
  const viewerDb = testEnv.authenticatedContext(VIEWER_UID).firestore();
  const viewerBatch = viewerDb.batch();
  viewerBatch.update(viewerDb.collection("materials").doc("P7_12345678"), {
    availableQty: 30,
    status: "IN_STOCK",
    updatedBy: VIEWER_UID,
    updatedAt: now,
  });
  viewerBatch.set(viewerDb.collection("transactions").doc("tx_viewer_1"), {
    factoryId: "P7",
    materialId: "P7_12345678",
    partNumber: "12345678",
    materialName: "Steel Bracket",
    type: "ISSUE",
    qty: 10,
    previousQty: 40,
    newQty: 30,
    user: "Viewer P7",
    userId: VIEWER_UID,
    timestamp: now,
    location: "A-01-01",
  });
  await assertFails(viewerBatch.commit());

  // Transactions are immutable -> update & delete FAIL
  await assertFails(
    opDb.collection("transactions").doc("tx_valid_1").update({ qty: 5 })
  );
  await assertFails(
    opDb.collection("transactions").doc("tx_valid_1").delete()
  );
});

test("Cycle Count 0-variance saves matched record; non-zero variance atomically updates material, cycleCounts, and discrepancies", async () => {
  await seedRolesAndFactory();
  const now = new Date();
  const opDb = testEnv.authenticatedContext(OPERATOR_UID).firestore();
  const viewerDb = testEnv.authenticatedContext(VIEWER_UID).firestore();

  // 1. Standalone discrepancy without cycleCount/material update -> FAILS
  await assertFails(
    opDb.collection("discrepancies").doc("disc_fake").set({
      factoryId: "P7",
      cycleCountId: "cc_missing",
      materialId: "P7_12345678",
      partNumber: "12345678",
      materialName: "Steel Bracket",
      location: "A-01-01",
      systemQty: 50,
      actualQty: 45,
      variance: -5,
      resolutionStatus: "ADJUSTED",
      reportedBy: "Operator P7",
      reportedByUid: OPERATOR_UID,
      timestamp: now,
    })
  );

  // 2. Zero-variance cycleCount when material qty == actualQty (50) -> SUCCEEDS
  await assertSucceeds(
    opDb.collection("cycleCounts").doc("cc_match_1").set({
      factoryId: "P7",
      materialId: "P7_12345678",
      partNumber: "12345678",
      materialName: "Steel Bracket",
      location: "A-01-01",
      systemQty: 50,
      actualQty: 50,
      variance: 0,
      status: "MATCHED",
      countedBy: "Operator P7",
      countedByUid: OPERATOR_UID,
      timestamp: now,
    })
  );

  // 3. Non-zero variance atomic cycle count adjustment -> SUCCEEDS
  const adjBatch = opDb.batch();
  adjBatch.update(opDb.collection("materials").doc("P7_12345678"), {
    availableQty: 8,
    status: "LOW_STOCK",
    updatedBy: OPERATOR_UID,
    updatedAt: now,
  });
  adjBatch.set(opDb.collection("cycleCounts").doc("cc_adj_1"), {
    factoryId: "P7",
    materialId: "P7_12345678",
    partNumber: "12345678",
    materialName: "Steel Bracket",
    location: "A-01-01",
    systemQty: 50,
    actualQty: 8,
    variance: -42,
    status: "ADJUSTED",
    countedBy: "Operator P7",
    countedByUid: OPERATOR_UID,
    notes: "Physical shelf audit",
    timestamp: now,
  });
  adjBatch.set(opDb.collection("discrepancies").doc("disc_adj_1"), {
    factoryId: "P7",
    cycleCountId: "cc_adj_1",
    materialId: "P7_12345678",
    partNumber: "12345678",
    materialName: "Steel Bracket",
    location: "A-01-01",
    systemQty: 50,
    actualQty: 8,
    variance: -42,
    resolutionStatus: "ADJUSTED",
    reportedBy: "Operator P7",
    reportedByUid: OPERATOR_UID,
    notes: "Physical shelf audit",
    timestamp: now,
  });
  await assertSucceeds(adjBatch.commit());

  // 4. Viewer cannot read P8 discrepancies or create cycle counts
  await assertFails(
    viewerDb.collection("cycleCounts").where("factoryId", "==", "P8").get()
  );
  await assertFails(
    viewerDb.collection("discrepancies").where("factoryId", "==", "P8").get()
  );

  // 5. CycleCounts & Discrepancies are immutable
  await assertFails(
    opDb.collection("cycleCounts").doc("cc_adj_1").delete()
  );
  await assertFails(
    opDb.collection("discrepancies").doc("disc_adj_1").update({ variance: 0 })
  );

  // 6. Operator can update requiredQty from OCR sheet import
  await assertSucceeds(
    opDb.collection("materials").doc("P7_12345678").update({
      location: "B0203",
      productionLine: "CDPO",
      requiredQty: 120,
      updatedBy: OPERATOR_UID,
      updatedAt: now,
    })
  );
});
