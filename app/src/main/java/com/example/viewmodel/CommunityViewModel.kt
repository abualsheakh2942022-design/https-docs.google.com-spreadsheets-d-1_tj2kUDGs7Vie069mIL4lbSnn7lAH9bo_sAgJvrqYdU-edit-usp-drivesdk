package com.example.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.AccountDeletionResult
import com.example.data.repository.CommunityRepository
import com.example.domain.CommunityComment
import com.example.domain.CommunityPost
import com.example.domain.CommunityPostCategory
import com.example.domain.ReportReason
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface CommunityUiState<out T> {
    data object Loading : CommunityUiState<Nothing>
    data class Success<T>(val data: T) : CommunityUiState<T>
    data class Error(val message: String) : CommunityUiState<Nothing>
}

class CommunityModerationStore(context: Context, userId: String) {
    private val blockedPrefs = context.applicationContext.getSharedPreferences(
        "community_blocked_users_$userId",
        Context.MODE_PRIVATE
    )
    private val hiddenPrefs = context.applicationContext.getSharedPreferences(
        "community_hidden_posts_$userId",
        Context.MODE_PRIVATE
    )

    fun loadBlockedAuthors(): Map<String, String> =
        blockedPrefs.all.entries
            .mapNotNull { (k, v) -> (v as? String)?.let { name -> k to name } }
            .toMap()

    fun loadHiddenPostIds(): Set<String> =
        hiddenPrefs.getStringSet(KEY_HIDDEN_POSTS, emptySet())?.toSet() ?: emptySet()

    fun blockAuthor(authorId: String, authorName: String): Map<String, String> {
        blockedPrefs.edit().putString(authorId, authorName).apply()
        return loadBlockedAuthors()
    }

    fun unblockAuthor(authorId: String): Map<String, String> {
        blockedPrefs.edit().remove(authorId).apply()
        return loadBlockedAuthors()
    }

    fun hidePost(postId: String): Set<String> {
        val next = loadHiddenPostIds() + postId
        hiddenPrefs.edit().putStringSet(KEY_HIDDEN_POSTS, next).apply()
        return next
    }

    fun clearAll() {
        blockedPrefs.edit().clear().apply()
        hiddenPrefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_HIDDEN_POSTS = "hidden_post_ids"
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CommunityViewModel(
    private val repository: CommunityRepository,
    private val moderationStore: CommunityModerationStore,
    val currentUserId: String,
    val currentUserName: String
) : ViewModel() {

    private val _selectedCategory = MutableStateFlow<CommunityPostCategory?>(null)
    val selectedCategory: StateFlow<CommunityPostCategory?> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedPostId = MutableStateFlow<String?>(null)
    val selectedPostId: StateFlow<String?> = _selectedPostId.asStateFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _blockedAuthors = MutableStateFlow(moderationStore.loadBlockedAuthors())
    val blockedAuthors: StateFlow<Map<String, String>> = _blockedAuthors.asStateFlow()

    private val _hiddenPostIds = MutableStateFlow(moderationStore.loadHiddenPostIds())

    private val _events = MutableSharedFlow<String>()
    val events: SharedFlow<String> = _events.asSharedFlow()

    private val rawPostsState: StateFlow<CommunityUiState<List<CommunityPost>>> =
        repository.observePosts()
            .map<List<CommunityPost>, CommunityUiState<List<CommunityPost>>> {
                CommunityUiState.Success(it)
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing community posts", rootError)
                emit(
                    CommunityUiState.Error(
                        rootError.localizedMessage ?: "تعذر تحميل منشورات المجتمع"
                    )
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = CommunityUiState.Loading
            )

    val filteredPostsState: StateFlow<CommunityUiState<List<CommunityPost>>> = combine(
        rawPostsState,
        _selectedCategory,
        _searchQuery,
        _blockedAuthors,
        _hiddenPostIds
    ) { state, category, query, blocked, hidden ->
        when (state) {
            is CommunityUiState.Loading -> CommunityUiState.Loading
            is CommunityUiState.Error -> state
            is CommunityUiState.Success -> {
                val trimmed = query.trim()
                val filtered = state.data.filter { post ->
                    val notBlocked = !blocked.containsKey(post.authorId)
                    val notHidden = !hidden.contains(post.id)
                    val matchesCategory = category == null || post.category == category.key
                    val matchesQuery = trimmed.isEmpty() ||
                        post.title.contains(trimmed, ignoreCase = true) ||
                        post.body.contains(trimmed, ignoreCase = true) ||
                        post.authorName.contains(trimmed, ignoreCase = true) ||
                        (post.location?.contains(trimmed, ignoreCase = true) == true)
                    notBlocked && notHidden && matchesCategory && matchesQuery
                }
                CommunityUiState.Success(filtered)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = CommunityUiState.Loading
    )

    val likedPostIds: StateFlow<Set<String>> = repository.observeLikedPostIds(currentUserId)
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing liked posts", firestoreError ?: error)
            emit(emptySet())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptySet()
        )

    val selectedPost: StateFlow<CommunityPost?> = combine(
        rawPostsState,
        _selectedPostId
    ) { state, postId ->
        if (postId == null || state !is CommunityUiState.Success) null
        else state.data.firstOrNull { it.id == postId }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = null
    )

    val commentsState: StateFlow<CommunityUiState<List<CommunityComment>>> = _selectedPostId
        .flatMapLatest { postId ->
            if (postId == null) {
                flowOf(CommunityUiState.Success(emptyList()))
            } else {
                combine(
                    repository.observeComments(postId),
                    _blockedAuthors
                ) { comments, blocked ->
                    CommunityUiState.Success(
                        comments.filter { !blocked.containsKey(it.authorId) }
                    ) as CommunityUiState<List<CommunityComment>>
                }.catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val rootError = firestoreError ?: error
                    Log.w(TAG, "Error observing comments", rootError)
                    emit(
                        CommunityUiState.Error(
                            rootError.localizedMessage ?: "تعذر تحميل التعليقات والحلول"
                        )
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = CommunityUiState.Loading
        )

    fun selectCategory(category: CommunityPostCategory?) {
        _selectedCategory.value = category
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun openPostDetail(postId: String?) {
        _selectedPostId.value = postId
    }

    fun createPost(
        title: String,
        body: String,
        category: CommunityPostCategory,
        location: String?,
        photoBase64: String?,
        onCreated: () -> Unit
    ) {
        if (title.trim().length < 3 || body.trim().length < 3) {
            viewModelScope.launch {
                _events.emit("يرجى إدخال عنوان وتفاصيل لا تقل عن 3 أحرف.")
            }
            return
        }
        viewModelScope.launch {
            _isSubmitting.value = true
            val result = repository.createPost(title, body, category, location, photoBase64)
            _isSubmitting.value = false
            result.onSuccess {
                _events.emit("تم نشر المشاركة في مجتمع المهندسين")
                onCreated()
            }.onFailure { err ->
                _events.emit("تعذر النشر: ${err.localizedMessage ?: "خطأ غير متوقع"}")
            }
        }
    }

    fun toggleLike(post: CommunityPost, currentlyLiked: Boolean) {
        viewModelScope.launch {
            val result = repository.toggleLike(post.id, currentlyLiked)
            result.onFailure { err ->
                _events.emit("تعذر تحديث الإعجاب: ${err.localizedMessage ?: "خطأ"}")
            }
        }
    }

    fun addComment(
        postId: String,
        text: String,
        photoBase64: String? = null,
        onAdded: () -> Unit
    ) {
        if (text.isBlank()) {
            viewModelScope.launch {
                _events.emit("يرجى كتابة تعليق أو حل هندسي أولاً.")
            }
            return
        }
        viewModelScope.launch {
            _isSubmitting.value = true
            val result = repository.addComment(postId, text, photoBase64)
            _isSubmitting.value = false
            result.onSuccess {
                _events.emit("تمت إضافة الحل / التعليق")
                onAdded()
            }.onFailure { err ->
                _events.emit("تعذر إضافة التعليق: ${err.localizedMessage ?: "خطأ"}")
            }
        }
    }

    fun acceptSolution(postId: String, commentId: String) {
        viewModelScope.launch {
            val result = repository.acceptCommentSolution(postId, commentId)
            result.onSuccess {
                _events.emit("تم اعتماد التعليق كحل هندسي موثوق")
            }.onFailure { err ->
                _events.emit("تعذر اعتماد الحل: ${err.localizedMessage ?: "خطأ"}")
            }
        }
    }

    fun togglePostSolved(post: CommunityPost) {
        viewModelScope.launch {
            val result = repository.markPostSolved(post.id, !post.solved)
            result.onSuccess {
                _events.emit(if (!post.solved) "تم تحديد الموضوع كمحلول" else "تمت إعادة فتح الموضوع")
            }.onFailure { err ->
                _events.emit("تعذر تحديث الحالة: ${err.localizedMessage ?: "خطأ"}")
            }
        }
    }

    fun deletePost(postId: String) {
        viewModelScope.launch {
            val result = repository.deletePost(postId)
            result.onSuccess {
                if (_selectedPostId.value == postId) {
                    _selectedPostId.value = null
                }
                _events.emit("تم حذف المنشور")
            }.onFailure { err ->
                _events.emit("تعذر حذف المنشور: ${err.localizedMessage ?: "خطأ"}")
            }
        }
    }

    fun deleteComment(postId: String, commentId: String) {
        viewModelScope.launch {
            val result = repository.deleteComment(postId, commentId)
            result.onSuccess {
                _events.emit("تم حذف التعليق")
            }.onFailure { err ->
                _events.emit("تعذر حذف التعليق: ${err.localizedMessage ?: "خطأ"}")
            }
        }
    }

    fun reportPost(
        post: CommunityPost,
        reason: ReportReason,
        details: String?,
        hideImmediately: Boolean
    ) {
        viewModelScope.launch {
            _isSubmitting.value = true
            val result = repository.reportContent(
                targetType = "POST",
                targetId = post.id,
                postId = post.id,
                reportedAuthorId = post.authorId,
                reason = reason,
                details = details
            )
            _isSubmitting.value = false
            result.onSuccess {
                if (hideImmediately) {
                    _hiddenPostIds.value = moderationStore.hidePost(post.id)
                    if (_selectedPostId.value == post.id) {
                        _selectedPostId.value = null
                    }
                }
                _events.emit("تم استلام البلاغ لمراجعة المحتوى. شكراً لحفاظك على جودة المجتمع.")
            }.onFailure { err ->
                _events.emit("تعذر إرسال البلاغ: ${err.localizedMessage ?: "خطأ"}")
            }
        }
    }

    fun reportComment(
        postId: String,
        comment: CommunityComment,
        reason: ReportReason,
        details: String?
    ) {
        viewModelScope.launch {
            _isSubmitting.value = true
            val result = repository.reportContent(
                targetType = "COMMENT",
                targetId = comment.id,
                postId = postId,
                reportedAuthorId = comment.authorId,
                reason = reason,
                details = details
            )
            _isSubmitting.value = false
            result.onSuccess {
                _events.emit("تم إرسال البلاغ عن التعليق للمراجعة.")
            }.onFailure { err ->
                _events.emit("تعذر إرسال البلاغ: ${err.localizedMessage ?: "خطأ"}")
            }
        }
    }

    fun hidePost(postId: String) {
        _hiddenPostIds.value = moderationStore.hidePost(postId)
        if (_selectedPostId.value == postId) {
            _selectedPostId.value = null
        }
        viewModelScope.launch {
            _events.emit("تم إخفاء المنشور من خلاصتك.")
        }
    }

    fun blockAuthor(authorId: String, authorName: String) {
        if (authorId == currentUserId) return
        _blockedAuthors.value = moderationStore.blockAuthor(authorId, authorName)
        if (selectedPost.value?.authorId == authorId) {
            _selectedPostId.value = null
        }
        viewModelScope.launch {
            _events.emit("تم حظر $authorName وإخفاء جميع منشوراته وتعليقاته.")
        }
    }

    fun unblockAuthor(authorId: String) {
        _blockedAuthors.value = moderationStore.unblockAuthor(authorId)
        viewModelScope.launch {
            _events.emit("تم إلغاء حظر المستخدم.")
        }
    }

    fun deleteMyAccount(
        onDeleted: () -> Unit,
        onRequiresReauth: () -> Unit
    ) {
        viewModelScope.launch {
            _isSubmitting.value = true
            val outcome = repository.deleteAccountAndData()
            _isSubmitting.value = false
            when (outcome) {
                is AccountDeletionResult.Success -> {
                    moderationStore.clearAll()
                    onDeleted()
                }
                is AccountDeletionResult.RequiresRecentLogin -> {
                    onRequiresReauth()
                }
                is AccountDeletionResult.Error -> {
                    _events.emit(outcome.message)
                }
            }
        }
    }

    private companion object {
        const val TAG = "CommunityVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
