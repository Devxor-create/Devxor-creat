const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";

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

function validProfile(uid) {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    displayName: "Commander Alice",
    username: "alice_orbit",
    bio: "Exploring SpaceX AI",
    email: "alice@example.com",
    language: "English",
    responseStyle: "Balanced",
    memoryEnabled: true,
    darkMode: true,
    selectedModel: "gemini-3.8-flash",
    onboardingCompleted: true,
    tier: "FREE",
    createdAt: now,
    updatedAt: now,
  };
}

test("Unauthenticated user cannot read or write any SpaceX AI user data", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("users").doc(ALICE_UID).get());
  await assertFails(unauth.collection("users").doc(ALICE_UID).set(validProfile(ALICE_UID)));
  await assertFails(
    unauth.collection("users").doc(ALICE_UID).collection("conversations").get()
  );
  await assertFails(
    unauth.collection("users").doc(ALICE_UID).collection("memories").get()
  );
});

test("User profile create, partial update, and tier escalation guard", async () => {
  const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
  const profileRef = alice.collection("users").doc(ALICE_UID);

  // Valid create
  await assertSucceeds(profileRef.set(validProfile(ALICE_UID)));

  // Valid partial update including customSystemPrompt
  await assertSucceeds(
    profileRef.update({
      displayName: "Commander Alice V2",
      memoryEnabled: false,
      customSystemPrompt: "Respond concisely as an aerospace systems engineer.",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Clearing customSystemPrompt with empty string succeeds
  await assertSucceeds(
    profileRef.update({
      customSystemPrompt: "",
      updatedAt: new Date(Date.now() - 400),
    })
  );

  // Reject oversized customSystemPrompt (> 2000 chars)
  await assertFails(
    profileRef.update({
      customSystemPrompt: "a".repeat(2001),
      updatedAt: new Date(Date.now() - 300),
    })
  );

  // Reject tier escalation
  await assertFails(
    profileRef.update({
      tier: "PRO",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject ghost field
  await assertFails(
    profileRef.update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject @DocumentId "id" key on create
  const alice2 = testEnv.authenticatedContext("alice_uid_999").firestore();
  await assertFails(
    alice2.collection("users").doc("alice_uid_999").set({
      ...validProfile("alice_uid_999"),
      id: "alice_uid_999",
    })
  );
});

test("Cross-user isolation: Bob cannot read or mutate Alice's data", async () => {
  const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bob = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(alice.collection("users").doc(ALICE_UID).set(validProfile(ALICE_UID)));
  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).collection("conversations").doc("conv_1").set({
      userId: ALICE_UID,
      title: "Orbital Physics",
      modelId: "gemini-3.8-flash",
      pinned: true,
      archived: false,
      lastMessagePreview: "Delta-v formula",
      messageCount: 2,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertFails(bob.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bob.collection("users").doc(ALICE_UID).collection("conversations").get()
  );
  await assertFails(
    bob.collection("users").doc(ALICE_UID).collection("conversations").doc("conv_1").update({
      title: "Hijacked",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Conversations, messages, projects, memories, library_items, studio_projects CRUD", async () => {
  const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 2000);
  const userDoc = alice.collection("users").doc(ALICE_UID);

  // Empty collection list succeeds
  await assertSucceeds(userDoc.collection("conversations").get());
  await assertSucceeds(userDoc.collection("projects").get());
  await assertSucceeds(userDoc.collection("memories").get());
  await assertSucceeds(userDoc.collection("library_items").get());
  await assertSucceeds(userDoc.collection("studio_projects").get());

  // Conversation + Message
  const convRef = userDoc.collection("conversations").doc("conv_1");
  await assertSucceeds(
    convRef.set({
      userId: ALICE_UID,
      title: "Mission Plan",
      modelId: "gemini-3.8-flash",
      pinned: false,
      archived: false,
      lastMessagePreview: "Hello",
      messageCount: 1,
      createdAt: now,
      updatedAt: now,
    })
  );

  const msgRef = convRef.collection("messages").doc("msg_1");
  await assertSucceeds(
    msgRef.set({
      userId: ALICE_UID,
      role: "model",
      text: "Telemetry nominal.",
      modelId: "gemini-3.8-flash",
      feedback: "none",
      groundingSources: ["https://nasa.gov"],
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    msgRef.update({
      feedback: "like",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject future timestamp
  await assertFails(
    userDoc.collection("memories").doc("mem_bad").set({
      userId: ALICE_UID,
      content: "Prefers concise code",
      category: "Style",
      createdAt: new Date(Date.now() + 60000),
      updatedAt: now,
    })
  );

  // Memory valid
  await assertSucceeds(
    userDoc.collection("memories").doc("mem_1").set({
      userId: ALICE_UID,
      content: "Prefers Kotlin and Jetpack Compose",
      category: "Coding",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Project valid
  await assertSucceeds(
    userDoc.collection("projects").doc("proj_1").set({
      userId: ALICE_UID,
      name: "Mars Lander UI",
      category: "Website development",
      instructions: "Use futuristic aerospace terminology.",
      description: "Landing page project",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Library valid
  await assertSucceeds(
    userDoc.collection("library_items").doc("lib_1").set({
      userId: ALICE_UID,
      title: "Orbital Note",
      folder: "Research",
      itemType: "note",
      content: "Hohmann transfer calculations",
      language: "markdown",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Studio project valid
  await assertSucceeds(
    userDoc.collection("studio_projects").doc("studio_1").set({
      userId: ALICE_UID,
      name: "Starship Arcade",
      studioType: "game",
      language: "HTML5 Canvas",
      primaryCode: "<canvas id='game'></canvas>",
      cssCode: "body { background: #071426; }",
      jsCode: "console.log('launch');",
      notes: "2D thruster shooter",
      createdAt: now,
      updatedAt: now,
    })
  );
});
