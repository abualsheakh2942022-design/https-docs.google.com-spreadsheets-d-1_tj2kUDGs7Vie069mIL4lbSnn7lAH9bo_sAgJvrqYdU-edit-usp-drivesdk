package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.domain.CommunityPostCategory
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class CommunityRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createPost_authenticatedUser_createsAndObservesPost() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repo = CommunityRepository(firestore, auth)

        val uniqueTitle = "خلطة خرسانية C25 ${UUID.randomUUID().toString().take(6)}"
        val createResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createPost(
                title = uniqueTitle,
                body = "تجربة صب سقف بسمك 15 سم مع مراعاة الخلطة بالكنكري مقاس 3/4.",
                category = CommunityPostCategory.ENGINEERING_IDEA,
                location = "الخرطوم",
                photoBase64 = null
            )
        }
        assertTrue("Expected createPost to succeed: ${createResult.exceptionOrNull()}", createResult.isSuccess)
        val postId = createResult.getOrThrow()

        val posts = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observePosts().first { list -> list.any { it.id == postId } }
        }
        val created = posts.first { it.id == postId }
        assertEquals(uniqueTitle, created.title)
        assertEquals(CommunityPostCategory.ENGINEERING_IDEA.key, created.category)
        assertFalse(created.solved)
    }

    @Test
    fun likeCommentAndAcceptSolution_multiUserWorkflow_succeeds() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val aliceRepo = CommunityRepository(firestore, auth)
        val postId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createPost(
                title = "استشارة تسليح قاعدة منفصلة ${UUID.randomUUID().toString().take(6)}",
                body = "ما هو الغطاء الخرساني الموصى به في التربة الرطبة؟",
                category = CommunityPostCategory.CONSULTATION,
                location = "أم درمان",
                photoBase64 = null
            ).getOrThrow()
        }

        // Bob likes the post and adds a solution comment
        val bobUid = signInTestUser(BOB_EMAIL)
        val bobRepo = CommunityRepository(firestore, auth)

        val likeRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.toggleLike(postId, currentlyLiked = false)
        }
        assertTrue("Expected Bob's like to succeed: ${likeRes.exceptionOrNull()}", likeRes.isSuccess)

        val likedIds = withTimeout(FLOW_TIMEOUT_MS) {
            bobRepo.observeLikedPostIds(bobUid).first { it.contains(postId) }
        }
        assertTrue(likedIds.contains(postId))

        val commentRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.addComment(
                postId = postId,
                text = "حسب الكود البريطاني BS 8110 يفضل غطاء لا يقل عن 50-75 مم للأساسات."
            )
        }
        assertTrue("Expected Bob's comment to succeed: ${commentRes.exceptionOrNull()}", commentRes.isSuccess)
        val commentId = commentRes.getOrThrow()

        // Bob cannot delete Alice's post
        val bobDeleteRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.deletePost(postId)
        }
        assertTrue(bobDeleteRes.isFailure)
        val deleteEx = bobDeleteRes.exceptionOrNull() as? FirebaseFirestoreException
        assertNotNull(deleteEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, deleteEx!!.code)

        // Alice accepts Bob's comment as solution
        signInTestUser(ALICE_EMAIL)
        val acceptRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.acceptCommentSolution(postId, commentId)
        }
        assertTrue("Expected Alice's acceptCommentSolution to succeed: ${acceptRes.exceptionOrNull()}", acceptRes.isSuccess)

        val updatedPost = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.getPostById(postId).getOrThrow()
        }
        assertNotNull(updatedPost)
        assertTrue(updatedPost!!.solved)
        assertEquals(commentId, updatedPost.acceptedCommentId)
        assertEquals(1L, updatedPost.likesCount)
        assertEquals(1L, updatedPost.commentsCount)
    }

    @Test
    fun observePosts_unauthenticatedCaller_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val snapshot = try {
            withTimeout(FLOW_TIMEOUT_MS) {
                firestore.collection(CommunityRepository.POSTS_COLLECTION)
                    .whereEqualTo("visibility", CommunityRepository.VISIBILITY_PUBLIC)
                    .get()
                    .let { com.google.android.gms.tasks.Tasks.await(it) }
            }
            null
        } catch (e: Exception) {
            e
        }
        assertNotNull("Expected unauthenticated query to fail", snapshot)
        val firestoreEx = snapshot!!.unwrapFirestoreException()
        assertNotNull("Expected FirebaseFirestoreException, got $snapshot", firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx!!.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_civil@test.com"
        const val BOB_EMAIL = "bob_civil@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
