const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const ts = () => new Date(Date.now() - 2000);

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

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

function validVaultDoc(uid, key = "full_name") {
  return {
    userId: uid,
    fieldKey: key,
    label: "Full Name",
    value: "Rahim Uddin",
    isSensitive: false,
    allowAutoFill: true,
    createdAt: ts(),
    updatedAt: ts(),
  };
}

function validBillDoc(uid) {
  return {
    userId: uid,
    provider: "DESCO Electricity",
    accountNo: "48219044",
    billingMonth: "November 2026",
    unitsKwh: 312,
    amount: 2450,
    currency: "BDT",
    dueDate: "2026-11-25",
    previousAmount: 2150,
    comparisonNote: "+14% vs Oct",
    status: "Paid",
    referenceId: "SAH-BILL-101",
    createdAt: ts(),
    updatedAt: ts(),
  };
}

test("Unauthenticated user: cannot read or write any user collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("vault").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("bills")
      .doc("b1")
      .set(validBillDoc(ALICE_UID))
  );
});

test("Cross-user isolation: Bob cannot read or write Alice's vault or bills", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db
      .collection("users")
      .doc(ALICE_UID)
      .collection("vault")
      .doc("full_name")
      .set({
        ...validVaultDoc(ALICE_UID),
        createdAt: new Date("2026-11-01T00:00:00Z"),
        updatedAt: new Date("2026-11-01T00:00:00Z"),
      });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("vault")
      .doc("full_name")
      .get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("bills").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("vault")
      .doc("phone")
      .set(validVaultDoc(ALICE_UID, "phone"))
  );
});

test("Shadow update / ghost field rejection on Vault", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("vault")
      .doc("full_name")
      .set({
        ...validVaultDoc(ALICE_UID),
        isAdmin: true,
      })
  );
});

test("Owner can create and update Vault field with valid payload", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ref = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("vault")
    .doc("full_name");
  await assertSucceeds(ref.set(validVaultDoc(ALICE_UID)));
  await assertSucceeds(
    ref.update({
      value: "Rahim Ahmed",
      allowAutoFill: false,
      updatedAt: ts(),
    })
  );
});

test("Owner can create Bill, Appointment, ShopTx, Inventory, Message, and AuditLog", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const userRef = aliceDb.collection("users").doc(ALICE_UID);

  await assertSucceeds(
    userRef.set({
      userId: ALICE_UID,
      languageMode: "en",
      strictApprovalMode: true,
      seededDefaults: false,
      createdAt: ts(),
      updatedAt: ts(),
    })
  );

  await assertSucceeds(
    userRef.collection("bills").doc("bill_1").set(validBillDoc(ALICE_UID))
  );

  await assertSucceeds(
    userRef.collection("appointments").doc("appt_1").set({
      userId: ALICE_UID,
      doctorOrOffice: "Dr. Nusrat Jahan",
      department: "General Medicine",
      date: "2026-11-18",
      time: "10:30 AM",
      patientName: "Rahim Uddin",
      notes: "Follow-up",
      status: "Confirmed",
      referenceId: "SAH-APT-101",
      createdAt: ts(),
      updatedAt: ts(),
    })
  );

  await assertSucceeds(
    userRef.collection("shop_transactions").doc("tx_1").set({
      userId: ALICE_UID,
      txType: "SALE",
      itemsSummary: "Rice 5kg x2",
      totalAmount: 960,
      currency: "BDT",
      notes: "Cash sale",
      referenceId: "SAH-SHP-101",
      createdAt: ts(),
      updatedAt: ts(),
    })
  );

  await assertSucceeds(
    userRef.collection("shop_inventory").doc("inv_1").set({
      userId: ALICE_UID,
      itemName: "Miniket Rice 5kg",
      quantity: 12,
      unit: "bags",
      lowStockThreshold: 5,
      unitPrice: 480,
      createdAt: ts(),
      updatedAt: ts(),
    })
  );

  await assertSucceeds(
    userRef.collection("messages").doc("msg_1").set({
      userId: ALICE_UID,
      sender: "assistant",
      text: "Ready for approval",
      attachmentLabel: "",
      stepState: "AWAITING_APPROVAL",
      cardType: "APPROVAL",
      actionType: "UTILITY_PAYMENT",
      portalTitle: "DESCO Portal",
      draftFields: { Amount: "2450 BDT" },
      maskedKeys: ["Account No"],
      requiresOtp: true,
      referenceId: "",
      status: "active",
      clientTimestampMs: 1700000000000,
      createdAt: ts(),
      updatedAt: ts(),
    })
  );

  await assertSucceeds(
    userRef.collection("audit_logs").doc("log_1").set({
      userId: ALICE_UID,
      eventType: "TOOL_CALL",
      title: "Checked Vault",
      details: "Read Full Name",
      vaultFieldsAccessed: ["full_name"],
      referenceId: "SAH-LOG-1",
      createdAt: ts(),
    })
  );
});

test("Audit logs are immutable (cannot update or delete)", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const logRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("audit_logs")
    .doc("log_immut");
  await assertSucceeds(
    logRef.set({
      userId: ALICE_UID,
      eventType: "ACTION_EXECUTED",
      title: "Paid Bill",
      details: "Paid DESCO bill",
      vaultFieldsAccessed: [],
      referenceId: "SAH-999",
      createdAt: ts(),
    })
  );
  await assertFails(logRef.update({ title: "Tampered" }));
  await assertFails(logRef.delete());
});

test("Terminal state locking: cancelled appointment cannot be updated", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const apptRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("appointments")
    .doc("appt_lock");
  await assertSucceeds(
    apptRef.set({
      userId: ALICE_UID,
      doctorOrOffice: "Dr. Karim",
      department: "Dental",
      date: "2026-11-20",
      time: "04:00 PM",
      patientName: "Rahim",
      notes: "",
      status: "Confirmed",
      referenceId: "SAH-APT-202",
      createdAt: ts(),
      updatedAt: ts(),
    })
  );
  await assertSucceeds(
    apptRef.update({
      status: "Cancelled",
      updatedAt: ts(),
    })
  );
  await assertFails(
    apptRef.update({
      status: "Confirmed",
      updatedAt: ts(),
    })
  );
});
