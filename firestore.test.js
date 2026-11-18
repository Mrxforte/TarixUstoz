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

function validUserProfile(uid, ts) {
  return {
    userId: uid,
    displayName: "Azizbek Karimov",
    email: "azizbek@example.com",
    bio: "Tarix bilimdoni",
    avatar: "AK",
    role: "Abituriyent",
    grade: 10,
    xp: 120,
    streak: 3,
    totalSolved: 30,
    correctCount: 25,
    timeSpentSeconds: 420,
    createdAt: ts,
    updatedAt: ts,
  };
}

test("Unauthenticated users cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("leaderboard").get());
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .set(validUserProfile(ALICE_UID, new Date(Date.now() - 1000)))
  );
});

test("User profile: owner create, partial update, immutable fields, and cross-user isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);

  // Valid create
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validUserProfile(ALICE_UID, created))
  );

  // Valid partial update
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      xp: 150,
      totalSolved: 40,
      correctCount: 33,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject createdAt tamper
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject shadow field injection
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject cross-user PII read or write
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).update({ xp: 9999 }));
});

test("Ticket progress subcollection: create, update lastOpenedTicket, list query, and isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("ticketProgress")
      .doc("uz_7")
      .set({
        categoryKey: "uz_7",
        passedTickets: [1, 2],
        scores: { "1": 24, "2": 27 },
        lastOpenedTicket: 3,
        highestUnlockedTicket: 3,
        createdAt: created,
        updatedAt: created,
      })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("ticketProgress")
      .doc("uz_7")
      .update({
        lastOpenedTicket: 4,
        highestUnlockedTicket: 4,
        passedTickets: [1, 2, 3],
        updatedAt: new Date(Date.now() - 1000),
      })
  );

  // Subcollection list query succeeds for owner
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("ticketProgress").get()
  );

  // Bob cannot read or list Alice's ticket progress
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("ticketProgress").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("ticketProgress").doc("uz_7").get()
  );
});

test("Attempts subcollection: create and ordered list succeed; update fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("attempts")
      .doc("att_1")
      .set({
        categoryKey: "EVENT",
        categoryTitle: "Tarixiy voqealar",
        grade: 7,
        solvedCount: 30,
        correctCount: 24,
        durationSeconds: 310,
        createdAt: created,
      })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("attempts")
      .orderBy("createdAt", "desc")
      .limit(50)
      .get()
  );

  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("attempts")
      .doc("att_1")
      .update({ correctCount: 30 })
  );
});

test("Public leaderboard: authenticated users can list; only owner can write; PII keys rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("leaderboard").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Azizbek Karimov",
      initials: "AK",
      xp: 120,
      streak: 3,
      totalSolved: 30,
      correctCount: 25,
      accuracy: 83,
      timeSpentMinutes: 7,
      grade: 10,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject PII injection into leaderboard
  await assertFails(
    bobDb.collection("leaderboard").doc(BOB_UID).set({
      userId: BOB_UID,
      displayName: "Bob",
      initials: "BO",
      email: "bob@example.com",
      xp: 50,
      streak: 1,
      totalSolved: 10,
      correctCount: 8,
      accuracy: 80,
      timeSpentMinutes: 3,
      grade: 8,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Signed-in users can query leaderboard
  await assertSucceeds(
    bobDb.collection("leaderboard").orderBy("xp", "desc").limit(50).get()
  );

  // Bob cannot overwrite Alice's leaderboard doc
  await assertFails(
    bobDb.collection("leaderboard").doc(ALICE_UID).update({ xp: 0, updatedAt: new Date(Date.now() - 1000) })
  );
});
