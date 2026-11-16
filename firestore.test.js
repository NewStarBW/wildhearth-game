const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
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

function validSavePayload(uid) {
  const now = new Date();
  return {
    userId: uid,
    worldName: "Twilight Camp",
    dayCount: 3,
    timePhase: "DUSK",
    health: 88,
    hunger: 74,
    sanity: 92,
    payloadJson: "{\"seed\":42}",
    createdAt: now,
    updatedAt: now,
  };
}

function validSkinPayload(uid) {
  const now = new Date();
  return {
    userId: uid,
    name: "Ember Scout",
    archetype: "SCHOLAR",
    hatStyle: "LANTERN_CAP",
    coatColorHex: "#E87A3E",
    hairColorHex: "#2A2421",
    skinToneHex: "#F1C7A8",
    accessory: "BACKPACK",
    createdAt: now,
    updatedAt: now,
  };
}

test("Unauthenticated user cannot read or write saves", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("saves").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("saves").doc("slot_1").set(validSavePayload(ALICE_UID)));
});

test("Owner can create, update, list, and delete their own world save", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const saveRef = aliceDb.collection("users").doc(ALICE_UID).collection("saves").doc("slot_1");
  await assertSucceeds(saveRef.set(validSavePayload(ALICE_UID)));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("saves").get());
  await assertSucceeds(saveRef.update({ dayCount: 4, timePhase: "NIGHT", updatedAt: new Date() }));
  await assertSucceeds(saveRef.delete());
});

test("Cross-user access to saves and skins is denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("saves").doc("slot_1").set(validSavePayload(ALICE_UID)));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("skins").doc("skin_1").set(validSkinPayload(ALICE_UID)));

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("saves").doc("slot_1").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("saves").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("skins").doc("skin_1").set(validSkinPayload(BOB_UID)));
});

test("Shadow field or invalid timePhase is rejected by schema validation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const badPayload = { ...validSavePayload(ALICE_UID), isAdmin: true };
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).collection("saves").doc("slot_bad").set(badPayload));

  const badPhase = { ...validSavePayload(ALICE_UID), timePhase: "INVALID" };
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).collection("saves").doc("slot_bad2").set(badPhase));
});

test("Owner can manage their own user profile and skin presets", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const profileRef = aliceDb.collection("users").doc(ALICE_UID);
  const now = new Date();
  await assertSucceeds(profileRef.set({
    userId: ALICE_UID,
    nickname: "Lina",
    email: "lina@example.com",
    activeSkinId: "skin_1",
    createdAt: now,
    updatedAt: now,
  }));
  await assertSucceeds(profileRef.collection("skins").doc("skin_1").set(validSkinPayload(ALICE_UID)));
});
