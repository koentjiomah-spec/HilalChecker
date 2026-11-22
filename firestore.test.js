const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_falak_123";
const BOB_UID = "bob_falak_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validProfilePayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    displayName: "Pengamat Falak",
    email: "pengamat@example.com",
    preferredMarkazId: "pelabuhan_ratu",
    preferredMarkazName: "POS Observasi Pelabuhan Ratu",
    preferredCriteria: "MABIMS_BARU",
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validObservationPayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    hijriYear: 1447,
    hijriMonth: 9,
    hijriMonthName: "Ramadhan",
    observationDateLabel: "17 Feb 2026",
    markazName: "POS Observasi Pelabuhan Ratu",
    latitude: -6.9875,
    longitude: 106.5442,
    moonAltitudeMariDeg: 3.45,
    elongationTopoDeg: 6.82,
    criteriaCode: "MABIMS Baru",
    verdictSummary: "Imkanur Rukyat",
    visibilityStatus: "MEMENUHI",
    hilalSighted: true,
    notes: "Langit cerah, sabit tipis teramati dengan binokuler 10x50.",
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

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

test("Unauthenticated users are denied all profile and observation operations", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID))
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("observations").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("observations")
      .doc("obs_1")
      .set(validObservationPayload(ALICE_UID))
  );
});

test("Authenticated owner can create, read, and partially update own UserFalakProfile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const profileRef = aliceDb.collection("users").doc(ALICE_UID);

  await assertSucceeds(profileRef.set(validProfilePayload(ALICE_UID)));
  await assertSucceeds(profileRef.get());

  // Partial update allowed keys
  await assertSucceeds(
    profileRef.update({
      preferredMarkazId: "condrodipo",
      preferredMarkazName: "Bukit Condrodipo",
      preferredCriteria: "WUJUDUL_HILAL",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Listing /users collection is forbidden
  await assertFails(aliceDb.collection("users").get());

  // Reject immutable createdAt mutation or ghost field
  await assertFails(
    profileRef.update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    profileRef.update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Authenticated owner can create, list, update, and delete own RukyatObservation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const obsCol = aliceDb.collection("users").doc(ALICE_UID).collection("observations");
  const obsRef = obsCol.doc("obs_ramadhan_1447");

  // List empty subcollection succeeds
  await assertSucceeds(obsCol.orderBy("createdAt", "desc").get());

  // Create Kotlin-aligned payload (no `id` key)
  await assertSucceeds(obsRef.set(validObservationPayload(ALICE_UID)));

  // Reject payload containing extra `id` field (@DocumentId conflict guard)
  await assertFails(
    obsCol.doc("obs_bad_id").set(validObservationPayload(ALICE_UID, { id: "obs_bad_id" }))
  );

  // Reject future timestamp
  await assertFails(
    obsCol
      .doc("obs_future_ts")
      .set(validObservationPayload(ALICE_UID, { createdAt: new Date(Date.now() + 60000) }))
  );

  // Partial update of sighting & notes succeeds
  await assertSucceeds(
    obsRef.update({
      hilalSighted: false,
      notes: "Revisi catatan pengamatan.",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject mutating immutable userId or hijacking hijriYear during update
  await assertFails(
    obsRef.update({
      hijriYear: 1448,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Owner delete succeeds
  await assertSucceeds(obsRef.delete());
});

test("Cross-user isolation: Alice cannot read, write, update, or delete Bob's data", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(BOB_UID).set(validProfilePayload(BOB_UID));
    await db
      .collection("users")
      .doc(BOB_UID)
      .collection("observations")
      .doc("bob_obs_1")
      .set(validObservationPayload(BOB_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).get());
  await assertFails(
    aliceDb
      .collection("users")
      .doc(BOB_UID)
      .update({ displayName: "Hijacked", updatedAt: new Date(Date.now() - 500) })
  );
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("observations").get()
  );
  await assertFails(
    aliceDb
      .collection("users")
      .doc(BOB_UID)
      .collection("observations")
      .doc("bob_obs_1")
      .get()
  );
  await assertFails(
    aliceDb
      .collection("users")
      .doc(BOB_UID)
      .collection("observations")
      .doc("bob_obs_1")
      .delete()
  );
  // Spoofing userId under Alice's path must also fail
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("observations")
      .doc("spoof_1")
      .set(validObservationPayload(BOB_UID))
  );
});
