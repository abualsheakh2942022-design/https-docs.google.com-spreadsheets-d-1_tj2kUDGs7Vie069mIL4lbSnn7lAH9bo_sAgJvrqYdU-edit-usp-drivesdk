const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_eng_01";
const BOB_UID = "bob_eng_02";
const DATABASE_ID = "ai-studio-android-8fc13603-1bc4-4b34-984e-cb2f3f3c820a";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastTimestamp() {
  return new Date(Date.now() - 1000);
}

function validPostPayload(authorId, overrides = {}) {
  return {
    authorId,
    authorName: "م. أحمد عثمان",
    title: "استشارة حول تسليح عمود في الخرطوم",
    body: "هل نستخدم كانات كل 15 سم في منطقة التكثيف أم كل 10 سم حسب BS 8110؟",
    category: "CONSULTATION",
    location: "الخرطوم - الرياض",
    visibility: "PUBLIC",
    likesCount: 0,
    commentsCount: 0,
    solved: false,
    createdAt: pastTimestamp(),
    updatedAt: pastTimestamp(),
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

test("Unauthenticated user: cannot list or create community_posts", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("community_posts").where("visibility", "==", "PUBLIC").get()
  );
  await assertFails(
    unauthDb.collection("community_posts").doc("post_1").set(validPostPayload(ALICE_UID))
  );
});

test("Authenticated user: can create valid post and list with visibility filter", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("community_posts").doc("post_1").set(validPostPayload(ALICE_UID))
  );

  // Aligned list query succeeds
  await assertSucceeds(
    aliceDb.collection("community_posts").where("visibility", "==", "PUBLIC").get()
  );

  // Unfiltered list query fails
  await assertFails(aliceDb.collection("community_posts").get());
});

test("Shadow update & identity spoofing: rejected on create", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // Spoofed authorId
  await assertFails(
    aliceDb.collection("community_posts").doc("post_spoof").set(validPostPayload(BOB_UID))
  );

  // Ghost field
  await assertFails(
    aliceDb
      .collection("community_posts")
      .doc("post_ghost")
      .set(validPostPayload(ALICE_UID, { isVerifiedAdmin: true }))
  );

  // Initial likesCount != 0
  await assertFails(
    aliceDb
      .collection("community_posts")
      .doc("post_likes")
      .set(validPostPayload(ALICE_UID, { likesCount: 99 }))
  );

  // Oversized photoBase64 (> 450000 chars)
  await assertFails(
    aliceDb
      .collection("community_posts")
      .doc("post_huge_img")
      .set(validPostPayload(ALICE_UID, { photoBase64: "A".repeat(460000) }))
  );
});

test("Cross-user isolation: Bob cannot edit or delete Alice's post", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("community_posts").doc("post_alice").set(validPostPayload(ALICE_UID))
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("community_posts").doc("post_alice").update({
      title: "Hacked title",
      updatedAt: pastTimestamp(),
    })
  );
  await assertFails(bobDb.collection("community_posts").doc("post_alice").delete());
});

test("Atomic like & unlike batch succeeds while non-atomic likesCount tamper fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("community_posts").doc("post_like").set(validPostPayload(ALICE_UID))
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  // Standalone likesCount update without liked_posts doc MUST fail
  await assertFails(
    bobDb.collection("community_posts").doc("post_like").update({
      likesCount: 1,
    })
  );

  // Atomic WriteBatch for like succeeds
  const likeBatch = bobDb.batch();
  likeBatch.update(bobDb.collection("community_posts").doc("post_like"), {
    likesCount: 1,
  });
  likeBatch.set(bobDb.collection("users").doc(BOB_UID).collection("liked_posts").doc("post_like"), {
    userId: BOB_UID,
    postId: "post_like",
    createdAt: pastTimestamp(),
  });
  await assertSucceeds(likeBatch.commit());

  // Atomic WriteBatch for unlike succeeds
  const unlikeBatch = bobDb.batch();
  unlikeBatch.update(bobDb.collection("community_posts").doc("post_like"), {
    likesCount: 0,
  });
  unlikeBatch.delete(
    bobDb.collection("users").doc(BOB_UID).collection("liked_posts").doc("post_like")
  );
  await assertSucceeds(unlikeBatch.commit());
});

test("Atomic comment creation and author-only solution acceptance", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("community_posts").doc("post_q").set(validPostPayload(ALICE_UID))
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  // Non-atomic comment without updating post commentsCount MUST fail
  await assertFails(
    bobDb
      .collection("community_posts")
      .doc("post_q")
      .collection("comments")
      .doc("c_1")
      .set({
        postId: "post_q",
        authorId: BOB_UID,
        authorName: "م. خالد",
        text: "نستخدم كانات كل 10 سم في ثلث العمود العلوي والسفلي.",
        visibility: "PUBLIC",
        acceptedSolution: false,
        createdAt: pastTimestamp(),
      })
  );

  // Atomic batch comment creation succeeds
  const commentBatch = bobDb.batch();
  commentBatch.set(
    bobDb.collection("community_posts").doc("post_q").collection("comments").doc("c_1"),
    {
      postId: "post_q",
      authorId: BOB_UID,
      authorName: "م. خالد",
      text: "نستخدم كانات كل 10 سم في ثلث العمود العلوي والسفلي.",
      visibility: "PUBLIC",
      acceptedSolution: false,
      createdAt: pastTimestamp(),
    }
  );
  commentBatch.update(bobDb.collection("community_posts").doc("post_q"), {
    commentsCount: 1,
    lastCommentId: "c_1",
    updatedAt: pastTimestamp(),
  });
  await assertSucceeds(commentBatch.commit());

  // Bob (comment author, NOT post author) cannot mark his own comment as accepted solution
  await assertFails(
    bobDb
      .collection("community_posts")
      .doc("post_q")
      .collection("comments")
      .doc("c_1")
      .update({ acceptedSolution: true })
  );

  // Alice (post author) can accept Bob's comment as solution and mark post solved
  const acceptBatch = aliceDb.batch();
  acceptBatch.update(
    aliceDb.collection("community_posts").doc("post_q").collection("comments").doc("c_1"),
    { acceptedSolution: true }
  );
  acceptBatch.update(aliceDb.collection("community_posts").doc("post_q"), {
    solved: true,
    acceptedCommentId: "c_1",
    updatedAt: pastTimestamp(),
  });
  await assertSucceeds(acceptBatch.commit());

  // Bob can query comments with visibility filter
  await assertSucceeds(
    bobDb
      .collection("community_posts")
      .doc("post_q")
      .collection("comments")
      .where("visibility", "==", "PUBLIC")
      .get()
  );
});

test("UGC moderation: authenticated users can submit valid reports but cannot list or spoof", async () => {
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  await assertSucceeds(
    bobDb.collection("community_reports").doc("rep_1").set({
      reporterId: BOB_UID,
      targetType: "POST",
      targetId: "post_1",
      postId: "post_1",
      reportedAuthorId: ALICE_UID,
      reason: "SPAM",
      details: "محتوى دعائي غير هندسي",
      status: "OPEN",
      createdAt: pastTimestamp(),
    })
  );

  // Reporter cannot list all reports
  await assertFails(bobDb.collection("community_reports").get());
  // Another user cannot read Bob's report
  await assertFails(aliceDb.collection("community_reports").doc("rep_1").get());
  // Spoofed reporterId fails
  await assertFails(
    bobDb.collection("community_reports").doc("rep_spoof").set({
      reporterId: ALICE_UID,
      targetType: "POST",
      targetId: "post_1",
      postId: "post_1",
      reportedAuthorId: ALICE_UID,
      reason: "SPAM",
      status: "OPEN",
      createdAt: pastTimestamp(),
    })
  );
});
