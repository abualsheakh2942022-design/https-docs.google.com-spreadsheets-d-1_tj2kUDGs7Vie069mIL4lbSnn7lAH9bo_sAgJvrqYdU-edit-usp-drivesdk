package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.example.domain.CommunityComment
import com.example.domain.CommunityPost
import com.example.domain.CommunityPostCategory
import com.example.domain.ReportReason
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class CommunityRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("يجب تسجيل الدخول بحساب Google قبل الوصول إلى المجتمع.")
    }

    private fun requireUserName(): String {
        val user = auth.currentUser
            ?: throw IllegalStateException("يجب تسجيل الدخول بحساب Google قبل الوصول إلى المجتمع.")
        return user.displayName?.takeIf { it.isNotBlank() }?.take(80)
            ?: user.email?.substringBefore("@")?.takeIf { it.isNotBlank() }?.take(80)
            ?: "مهندس مدني"
    }

    fun observePosts(): Flow<List<CommunityPost>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection(POSTS_COLLECTION)
                .whereEqualTo("visibility", VISIBILITY_PUBLIC)
                .limit(80)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CommunityPost::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, POSTS_COLLECTION)
                    }
                    throw error
                }
        )
    }

    fun observeLikedPostIds(userId: String): Flow<Set<String>> = flow {
        val path = "$USERS_COLLECTION/$userId/$LIKED_POSTS_SUBCOLLECTION"
        emitAll(
            db.collection(USERS_COLLECTION)
                .document(userId)
                .collection(LIKED_POSTS_SUBCOLLECTION)
                .snapshots()
                .map { snapshot -> snapshot.documents.map { it.id }.toSet() }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeComments(postId: String): Flow<List<CommunityComment>> = flow {
        val uid = requireUserId()
        val path = "$POSTS_COLLECTION/$postId/$COMMENTS_SUBCOLLECTION"
        emitAll(
            db.collection(POSTS_COLLECTION)
                .document(postId)
                .collection(COMMENTS_SUBCOLLECTION)
                .whereEqualTo("visibility", VISIBILITY_PUBLIC)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CommunityComment::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun getPostById(postId: String): Result<CommunityPost?> {
        val docRef = db.collection(POSTS_COLLECTION).document(postId)
        return try {
            val snap = docRef.get().await()
            val post = snap.toObject(
                CommunityPost::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            Result.success(post)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun createPost(
        title: String,
        body: String,
        category: CommunityPostCategory,
        location: String?,
        photoBase64: String?
    ): Result<String> {
        val uid = requireUserId()
        val authorName = requireUserName()
        val docRef = db.collection(POSTS_COLLECTION).document()
        val cleanLocation = location?.trim()?.takeIf { it.isNotEmpty() }?.take(120)
        val cleanPhoto = photoBase64?.takeIf { it.isNotBlank() }

        val payload = mapOf(
            "authorId" to uid,
            "authorName" to authorName,
            "title" to title.trim().take(140),
            "body" to body.trim().take(4000),
            "category" to category.key,
            "location" to cleanLocation,
            "photoBase64" to cleanPhoto,
            "visibility" to VISIBILITY_PUBLIC,
            "likesCount" to 0L,
            "commentsCount" to 0L,
            "solved" to false,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null }

        return try {
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updatePostContent(
        postId: String,
        title: String,
        body: String,
        category: CommunityPostCategory,
        location: String?
    ): Result<Unit> {
        requireUserId()
        val docRef = db.collection(POSTS_COLLECTION).document(postId)
        val updates = mutableMapOf<String, Any>(
            "title" to title.trim().take(140),
            "body" to body.trim().take(4000),
            "category" to category.key,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        val cleanLocation = location?.trim()?.takeIf { it.isNotEmpty() }?.take(120)
        if (cleanLocation != null) {
            updates["location"] = cleanLocation
        }
        return try {
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun toggleLike(postId: String, currentlyLiked: Boolean): Result<Unit> {
        val uid = requireUserId()
        val postRef = db.collection(POSTS_COLLECTION).document(postId)
        val likeRef = db.collection(USERS_COLLECTION)
            .document(uid)
            .collection(LIKED_POSTS_SUBCOLLECTION)
            .document(postId)

        return try {
            val batch = db.batch()
            if (currentlyLiked) {
                batch.update(postRef, mapOf("likesCount" to FieldValue.increment(-1L)))
                batch.delete(likeRef)
            } else {
                batch.update(postRef, mapOf("likesCount" to FieldValue.increment(1L)))
                batch.set(
                    likeRef,
                    mapOf(
                        "userId" to uid,
                        "postId" to postId,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, postRef.path)
            Result.failure(e)
        }
    }

    suspend fun addComment(
        postId: String,
        text: String,
        photoBase64: String? = null
    ): Result<String> {
        val uid = requireUserId()
        val authorName = requireUserName()
        val postRef = db.collection(POSTS_COLLECTION).document(postId)
        val commentRef = postRef.collection(COMMENTS_SUBCOLLECTION).document()
        val cleanPhoto = photoBase64?.takeIf { it.isNotBlank() }

        val commentPayload = mapOf(
            "postId" to postId,
            "authorId" to uid,
            "authorName" to authorName,
            "text" to text.trim().take(2000),
            "photoBase64" to cleanPhoto,
            "visibility" to VISIBILITY_PUBLIC,
            "acceptedSolution" to false,
            "createdAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null }

        return try {
            val batch = db.batch()
            batch.set(commentRef, commentPayload)
            batch.update(
                postRef,
                mapOf(
                    "commentsCount" to FieldValue.increment(1L),
                    "lastCommentId" to commentRef.id,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(commentRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, commentRef.path)
            Result.failure(e)
        }
    }

    suspend fun markPostSolved(postId: String, solved: Boolean): Result<Unit> {
        requireUserId()
        val postRef = db.collection(POSTS_COLLECTION).document(postId)
        return try {
            postRef.update(
                mapOf(
                    "solved" to solved,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, postRef.path)
            Result.failure(e)
        }
    }

    suspend fun acceptCommentSolution(postId: String, commentId: String): Result<Unit> {
        requireUserId()
        val postRef = db.collection(POSTS_COLLECTION).document(postId)
        val commentRef = postRef.collection(COMMENTS_SUBCOLLECTION).document(commentId)
        return try {
            val batch = db.batch()
            batch.update(commentRef, mapOf("acceptedSolution" to true))
            batch.update(
                postRef,
                mapOf(
                    "solved" to true,
                    "acceptedCommentId" to commentId,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, postRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteComment(postId: String, commentId: String): Result<Unit> {
        requireUserId()
        val postRef = db.collection(POSTS_COLLECTION).document(postId)
        val commentRef = postRef.collection(COMMENTS_SUBCOLLECTION).document(commentId)
        return try {
            val batch = db.batch()
            batch.delete(commentRef)
            batch.update(
                postRef,
                mapOf(
                    "commentsCount" to FieldValue.increment(-1L),
                    "lastCommentId" to commentId
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, commentRef.path)
            Result.failure(e)
        }
    }

    suspend fun deletePost(postId: String): Result<Unit> {
        requireUserId()
        val postRef = db.collection(POSTS_COLLECTION).document(postId)
        return try {
            postRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, postRef.path)
            Result.failure(e)
        }
    }

    suspend fun reportContent(
        targetType: String,
        targetId: String,
        postId: String,
        reportedAuthorId: String,
        reason: ReportReason,
        details: String?
    ): Result<String> {
        val uid = requireUserId()
        val docRef = db.collection(REPORTS_COLLECTION).document()
        val cleanDetails = details?.trim()?.takeIf { it.isNotEmpty() }?.take(500)

        val payload = mapOf(
            "reporterId" to uid,
            "targetType" to targetType,
            "targetId" to targetId,
            "postId" to postId,
            "reportedAuthorId" to reportedAuthorId,
            "reason" to reason.key,
            "details" to cleanDetails,
            "status" to "OPEN",
            "createdAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null }

        return try {
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteAccountAndData(): AccountDeletionResult {
        val user = auth.currentUser
            ?: return AccountDeletionResult.Error("لا توجد جلسة حساب نشطة حالياً.")
        val uid = user.uid

        // Clean up the user's own authored community posts first while still authenticated
        try {
            val authoredPosts = db.collection(POSTS_COLLECTION)
                .whereEqualTo("visibility", VISIBILITY_PUBLIC)
                .get()
                .await()
                .documents
                .filter { it.getString("authorId") == uid }

            authoredPosts.chunked(350).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { doc -> batch.delete(doc.reference) }
                batch.commit().await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Continue with Firebase Auth deletion even if post cleanup fails partially
        }

        return try {
            user.delete().await()
            AccountDeletionResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException) {
            AccountDeletionResult.RequiresRecentLogin
        } catch (e: Exception) {
            AccountDeletionResult.Error(e.localizedMessage ?: "تعذر حذف الحساب في الوقت الحالي.")
        }
    }

    companion object {
        const val POSTS_COLLECTION = "community_posts"
        const val COMMENTS_SUBCOLLECTION = "comments"
        const val REPORTS_COLLECTION = "community_reports"
        const val USERS_COLLECTION = "users"
        const val LIKED_POSTS_SUBCOLLECTION = "liked_posts"
        const val VISIBILITY_PUBLIC = "PUBLIC"
    }
}

sealed interface AccountDeletionResult {
    data object Success : AccountDeletionResult
    data object RequiresRecentLogin : AccountDeletionResult
    data class Error(val message: String) : AccountDeletionResult
}
