package com.example.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Architecture
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.repository.CommunityRepository
import com.example.domain.CommunityComment
import com.example.domain.CommunityPost
import com.example.domain.CommunityPostCategory
import com.example.domain.ReportReason
import com.example.ui.theme.BlueprintTeal
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.SafetyAmber
import com.example.ui.theme.SuccessGreen
import com.example.util.CommunityImageCodec
import com.example.util.GoogleSignInButton
import com.example.util.attemptAutoSignIn
import com.example.util.authStateFlow
import com.example.util.signOutGoogle
import com.example.viewmodel.CommunityModerationStore
import com.example.viewmodel.CommunityUiState
import com.example.viewmodel.CommunityViewModel
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun CommunityScreen(
    onBack: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val auth = Firebase.auth
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        CommunityAuthGateScreen(
            onBack = onBack,
            onShowMessage = onShowMessage
        )
    } else {
        key(user.uid) {
            val displayName = user.displayName?.takeIf { it.isNotBlank() }
                ?: user.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
                ?: "مهندس مدني"

            val communityViewModel: CommunityViewModel = viewModel(
                key = user.uid,
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY]) {
                            "APPLICATION_KEY missing from CreationExtras"
                        }
                        val databaseId = app.getString(R.string.firestore_database_id)
                        val db = FirebaseFirestore.getInstance(databaseId)
                        CommunityViewModel(
                            repository = CommunityRepository(db, auth),
                            moderationStore = CommunityModerationStore(app, user.uid),
                            currentUserId = user.uid,
                            currentUserName = displayName
                        )
                    }
                }
            )

            CommunityFeedScreen(
                viewModel = communityViewModel,
                onBack = onBack,
                onShowMessage = onShowMessage
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommunityAuthGateScreen(
    onBack: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    var authError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = { },
            onUnauthenticated = { },
            scope = coroutineScope
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "مجتمع المهندسين",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("community_auth_back")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NavyPrimary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = NavyPrimary.copy(alpha = 0.1f),
                modifier = Modifier.size(92.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Forum,
                        contentDescription = null,
                        tint = NavyPrimary,
                        modifier = Modifier.size(46.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "مجتمع المهندسين المدنيين",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "منصة تفاعلية لتداول صور المواقع، الأفكار التصميمية، حلول التنفيذ في السوق السوداني، ومناقشة تفاصيل التسليح والمخططات وفق الكود البريطاني.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 22.sp
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AuthFeatureRow(Icons.Default.PhotoCamera, "مشاركة صور الموقع ومراحل الصب والحدادة")
                    AuthFeatureRow(Icons.Default.Lightbulb, "تبادل الأفكار الهندسية وتوفير التكاليف")
                    AuthFeatureRow(Icons.Default.Build, "طرح المشاكل الإنشائية واعتماد الحلول الموثوقة")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            GoogleSignInButton(
                onAuthSuccess = {
                    authError = null
                    onShowMessage("تم تسجيل الدخول بنجاح")
                },
                onAuthError = { msg ->
                    authError = msg
                }
            )

            if (authError != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = authError ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "بالدخول إلى المجتمع، فإنك توافق على مشاركة محتوى هندسي محترم وخالٍ من الإساءة أو الإعلانات المضللة. يمكنك الإبلاغ عن أي مخالفة أو حظر أي حساب أو حذف حسابك في أي وقت.",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                ),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun AuthFeatureRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = SafetyAmber,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommunityFeedScreen(
    viewModel: CommunityViewModel,
    onBack: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val postsState by viewModel.filteredPostsState.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val likedIds by viewModel.likedPostIds.collectAsStateWithLifecycle()
    val selectedPost by viewModel.selectedPost.collectAsStateWithLifecycle()
    val commentsState by viewModel.commentsState.collectAsStateWithLifecycle()
    val isSubmitting by viewModel.isSubmitting.collectAsStateWithLifecycle()

    val blockedAuthors by viewModel.blockedAuthors.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var showCreateDialog by remember { mutableStateOf(false) }
    var zoomedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var postPendingDelete by remember { mutableStateOf<CommunityPost?>(null) }
    var showAccountMenu by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showBlockedUsersDialog by remember { mutableStateOf(false) }
    var postPendingReport by remember { mutableStateOf<CommunityPost?>(null) }
    var commentPendingReport by remember { mutableStateOf<Pair<String, CommunityComment>?>(null) }
    var authorPendingBlock by remember { mutableStateOf<Pair<String, String>?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { msg ->
            onShowMessage(msg)
        }
    }

    if (showCreateDialog || selectedPost != null || zoomedBitmap != null) {
        BackHandler {
            when {
                zoomedBitmap != null -> zoomedBitmap = null
                showCreateDialog -> showCreateDialog = false
                selectedPost != null -> viewModel.openPostDetail(null)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "مجتمع المهندسين",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "مرحباً م. ${viewModel.currentUserName}",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("community_back_button")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع"
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(
                            onClick = { showAccountMenu = true },
                            modifier = Modifier.testTag("community_account_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ManageAccounts,
                                contentDescription = "إدارة الحساب والسلامة"
                            )
                        }
                        DropdownMenu(
                            expanded = showAccountMenu,
                            onDismissRequest = { showAccountMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("المحظورون (${blockedAuthors.size})") },
                                leadingIcon = {
                                    Icon(Icons.Default.Block, contentDescription = null)
                                },
                                onClick = {
                                    showAccountMenu = false
                                    showBlockedUsersDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("تسجيل الخروج") },
                                leadingIcon = {
                                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                                },
                                onClick = {
                                    showAccountMenu = false
                                    signOutGoogle(
                                        credentialManager = credentialManager,
                                        scope = coroutineScope,
                                        onSignOutComplete = { onShowMessage("تم تسجيل الخروج") }
                                    )
                                },
                                modifier = Modifier.testTag("community_sign_out_button")
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "حذف الحساب وبياناتي",
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.DeleteForever,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    showAccountMenu = false
                                    showDeleteAccountDialog = true
                                },
                                modifier = Modifier.testTag("community_delete_account_menu_item")
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NavyPrimary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("مشاركة جديدة", fontWeight = FontWeight.Bold) },
                containerColor = SafetyAmber,
                contentColor = Color.White,
                modifier = Modifier.testTag("community_new_post_fab")
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Search box
            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                placeholder = { Text("ابحث في المشاركات أو الحلول أو المدن...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(Icons.Default.Close, contentDescription = "مسح البحث")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .testTag("community_search_input")
            )

            // Category filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { viewModel.selectCategory(null) },
                    label = { Text("الكل") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NavyPrimary,
                        selectedLabelColor = Color.White
                    ),
                    modifier = Modifier.testTag("filter_all")
                )
                CommunityPostCategory.entries.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = {
                            viewModel.selectCategory(if (selectedCategory == cat) null else cat)
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = categoryIcon(cat),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text(cat.labelAr) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = categoryColor(cat),
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White
                        ),
                        modifier = Modifier.testTag("filter_${cat.key.lowercase()}")
                    )
                }
            }

            when (val state = postsState) {
                is CommunityUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("جاري تحميل مشاركات المجتمع...")
                        }
                    }
                }

                is CommunityUiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = state.message,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                is CommunityUiState.Success -> {
                    if (state.data.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Forum,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                    modifier = Modifier.size(56.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "لا توجد مشاركات مطابقة حالياً",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "كن أول من يشارك صورة موقع أو فكرة هندسية أو استشارة إنشائية!",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = { showCreateDialog = true }) {
                                    Text("إضافة مشاركة")
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 8.dp,
                                bottom = 88.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(state.data, key = { it.id }) { post ->
                                val isLiked = likedIds.contains(post.id)
                                CommunityPostCard(
                                    post = post,
                                    isLiked = isLiked,
                                    isAuthor = post.authorId == viewModel.currentUserId,
                                    onLikeClick = { viewModel.toggleLike(post, isLiked) },
                                    onOpenDetail = { viewModel.openPostDetail(post.id) },
                                    onImageClick = { bmp -> zoomedBitmap = bmp },
                                    onDeleteClick = { postPendingDelete = post },
                                    onReportClick = { postPendingReport = post },
                                    onHideClick = { viewModel.hidePost(post.id) },
                                    onBlockAuthorClick = {
                                        authorPendingBlock = post.authorId to post.authorName
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateCommunityPostDialog(
            isSubmitting = isSubmitting,
            onDismiss = { showCreateDialog = false },
            onSubmit = { title, body, category, location, photoBase64 ->
                viewModel.createPost(title, body, category, location, photoBase64) {
                    showCreateDialog = false
                }
            }
        )
    }

    val activePost = selectedPost
    if (activePost != null) {
        PostDetailModal(
            post = activePost,
            isLiked = likedIds.contains(activePost.id),
            currentUserId = viewModel.currentUserId,
            commentsState = commentsState,
            isSubmitting = isSubmitting,
            onDismiss = { viewModel.openPostDetail(null) },
            onToggleLike = { viewModel.toggleLike(activePost, likedIds.contains(activePost.id)) },
            onToggleSolved = { viewModel.togglePostSolved(activePost) },
            onAddComment = { text, photoBase64, onClear ->
                viewModel.addComment(activePost.id, text, photoBase64, onClear)
            },
            onAcceptSolution = { commentId ->
                viewModel.acceptSolution(activePost.id, commentId)
            },
            onDeleteComment = { commentId ->
                viewModel.deleteComment(activePost.id, commentId)
            },
            onReportComment = { comment ->
                commentPendingReport = activePost.id to comment
            },
            onBlockCommentAuthor = { comment ->
                authorPendingBlock = comment.authorId to comment.authorName
            },
            onImageClick = { bmp -> zoomedBitmap = bmp }
        )
    }

    val pendingDelete = postPendingDelete
    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { postPendingDelete = null },
            title = { Text("حذف المشاركة", fontWeight = FontWeight.Bold) },
            text = { Text("هل أنت متأكد من حذف \"${pendingDelete.title}\" نهائياً؟") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePost(pendingDelete.id)
                        postPendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("حذف")
                }
            },
            dismissButton = {
                TextButton(onClick = { postPendingDelete = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    val reportPostTarget = postPendingReport
    if (reportPostTarget != null) {
        ReportContentDialog(
            targetTitle = reportPostTarget.title,
            isSubmitting = isSubmitting,
            showHideOption = true,
            onDismiss = { postPendingReport = null },
            onSubmit = { reason, details, hideImmediately ->
                viewModel.reportPost(reportPostTarget, reason, details, hideImmediately)
                postPendingReport = null
            }
        )
    }

    val reportCommentTarget = commentPendingReport
    if (reportCommentTarget != null) {
        ReportContentDialog(
            targetTitle = "تعليق ${reportCommentTarget.second.authorName}",
            isSubmitting = isSubmitting,
            showHideOption = false,
            onDismiss = { commentPendingReport = null },
            onSubmit = { reason, details, _ ->
                viewModel.reportComment(reportCommentTarget.first, reportCommentTarget.second, reason, details)
                commentPendingReport = null
            }
        )
    }

    val blockTarget = authorPendingBlock
    if (blockTarget != null) {
        AlertDialog(
            onDismissRequest = { authorPendingBlock = null },
            title = { Text("حظر المستخدم", fontWeight = FontWeight.Bold) },
            text = {
                Text("هل تريد حظر «${blockTarget.second}»؟ سيتم إخفاء جميع منشوراته وتعليقاته فوراً، ويمكنك إلغاء الحظر لاحقاً من قائمة إدارة الحساب.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.blockAuthor(blockTarget.first, blockTarget.second)
                        authorPendingBlock = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("حظر وإخفاء")
                }
            },
            dismissButton = {
                TextButton(onClick = { authorPendingBlock = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    if (showBlockedUsersDialog) {
        AlertDialog(
            onDismissRequest = { showBlockedUsersDialog = false },
            title = { Text("الحسابات المحظورة", fontWeight = FontWeight.Bold) },
            text = {
                if (blockedAuthors.isEmpty()) {
                    Text("لا توجد حسابات محظورة حالياً.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        blockedAuthors.forEach { (authorId, authorName) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = authorName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { viewModel.unblockAuthor(authorId) }) {
                                    Text("إلغاء الحظر")
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBlockedUsersDialog = false }) {
                    Text("إغلاق")
                }
            }
        )
    }

    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { if (!isSubmitting) showDeleteAccountDialog = false },
            title = {
                Text(
                    text = "حذف الحساب وبياناتي نهائياً",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Text(
                    "سيتم حذف حسابك في مجتمع المهندسين وجميع منشوراتك المرتبطة به نهائياً من السحابة، مع بقاء مشاريعك المحلية المحفوظة على جهازك."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteMyAccount(
                            onDeleted = {
                                showDeleteAccountDialog = false
                                signOutGoogle(
                                    credentialManager = credentialManager,
                                    scope = coroutineScope,
                                    onSignOutComplete = {
                                        onShowMessage("تم حذف الحساب وبياناته السحابية بنجاح.")
                                    }
                                )
                            },
                            onRequiresReauth = {
                                showDeleteAccountDialog = false
                                signOutGoogle(
                                    credentialManager = credentialManager,
                                    scope = coroutineScope,
                                    onSignOutComplete = {
                                        onShowMessage("لأسباب أمنية، يرجى تسجيل الدخول مجدداً ثم تأكيد حذف الحساب.")
                                    }
                                )
                            }
                        )
                    },
                    enabled = !isSubmitting,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("نعم، احذف الحساب")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteAccountDialog = false },
                    enabled = !isSubmitting
                ) {
                    Text("إلغاء")
                }
            }
        )
    }

    val fullBitmap = zoomedBitmap
    if (fullBitmap != null) {
        Dialog(
            onDismissRequest = { zoomedBitmap = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.92f))
                    .clickable { zoomedBitmap = null },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = fullBitmap.asImageBitmap(),
                    contentDescription = "عرض الصورة بالحجم الكامل",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                )
                IconButton(
                    onClick = { zoomedBitmap = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "إغلاق",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun CommunityPostCard(
    post: CommunityPost,
    isLiked: Boolean,
    isAuthor: Boolean,
    onLikeClick: () -> Unit,
    onOpenDetail: () -> Unit,
    onImageClick: (Bitmap) -> Unit,
    onDeleteClick: () -> Unit,
    onReportClick: () -> Unit,
    onHideClick: () -> Unit,
    onBlockAuthorClick: () -> Unit
) {
    val category = CommunityPostCategory.fromKey(post.category)
    val accentColor = categoryColor(category)
    val decodedBitmap = remember(post.photoBase64) {
        CommunityImageCodec.decodeBase64ToBitmap(post.photoBase64)
    }
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenDetail)
            .testTag("community_post_card_${post.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(
            width = if (post.solved) 1.5.dp else 1.dp,
            color = if (post.solved) SuccessGreen.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header row: Author + Badges + Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = NavyPrimary.copy(alpha = 0.12f),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = post.authorName.take(1).uppercase(),
                            fontWeight = FontWeight.Bold,
                            color = NavyPrimary
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = post.authorName,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = formatTimestampAr(post.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!post.location.isNullOrBlank()) {
                            Text(
                                text = " • ",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = SafetyAmber,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = post.location,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (isAuthor) {
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "حذف المنشور",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "خيارات المنشور",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("الإبلاغ عن المنشور") },
                                leadingIcon = {
                                    Icon(Icons.Default.Flag, contentDescription = null, tint = SafetyAmber)
                                },
                                onClick = {
                                    showMenu = false
                                    onReportClick()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("إخفاء هذا المنشور") },
                                leadingIcon = {
                                    Icon(Icons.Default.VisibilityOff, contentDescription = null)
                                },
                                onClick = {
                                    showMenu = false
                                    onHideClick()
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "حظر ${post.authorName}",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Block,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onBlockAuthorClick()
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Category & Solved chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = accentColor.copy(alpha = 0.14f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = categoryIcon(category),
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = category.labelAr,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = accentColor
                            )
                        )
                    }
                }

                if (post.solved) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = SuccessGreen.copy(alpha = 0.15f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = SuccessGreen,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "تم الحل",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = SuccessGreen
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = post.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = post.body,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )

            if (decodedBitmap != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Image(
                    bitmap = decodedBitmap.asImageBitmap(),
                    contentDescription = "صورة مرفقة بالمشاركة",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onImageClick(decodedBitmap) }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = onLikeClick,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "إعجاب",
                            tint = if (isLiked) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${post.likesCount}",
                            color = if (isLiked) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    TextButton(
                        onClick = onOpenDetail,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Comment,
                            contentDescription = "الحلول والتعليقات",
                            tint = NavyPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${post.commentsCount} حلول / تعليقات",
                            color = NavyPrimary
                        )
                    }
                }

                Text(
                    text = "عرض التفاصيل ←",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = BlueprintTeal
                    )
                )
            }
        }
    }
}

@Composable
private fun CreateCommunityPostDialog(
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (
        title: String,
        body: String,
        category: CommunityPostCategory,
        location: String?,
        photoBase64: String?
    ) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(CommunityPostCategory.SITE_PHOTO) }
    var photoBase64 by remember { mutableStateOf<String?>(null) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isEncodingPhoto by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            isEncodingPhoto = true
            localError = null
            scope.launch {
                val encoded = CommunityImageCodec.encodeUriToBoundedBase64(context, uri)
                if (encoded != null) {
                    photoBase64 = encoded
                    previewBitmap = CommunityImageCodec.decodeBase64ToBitmap(encoded)
                } else {
                    localError = "تعذر ضغط الصورة المحددة. يرجى اختيار صورة أخرى."
                }
                isEncodingPhoto = false
            }
        }
    }

    Dialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "مشاركة جديدة في المجتمع",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    IconButton(onClick = onDismiss, enabled = !isSubmitting) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق")
                    }
                }

                Text(
                    text = "التصنيف",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CommunityPostCategory.entries.forEach { item ->
                        FilterChip(
                            selected = category == item,
                            onClick = { category = item },
                            label = { Text(item.labelAr) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = categoryColor(item),
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(140) },
                    label = { Text("عنوان الموضوع أو الفكرة *") },
                    placeholder = { Text("مثال: معالجة تعشيش الخرسانة في رقاب الأعمدة") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_post_title_input")
                )

                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it.take(120) },
                    label = { Text("الموقع / المدينة (اختياري)") },
                    placeholder = { Text("مثال: الخرطوم، أم درمان، مدني، عطبرة") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it.take(4000) },
                    label = { Text("التفاصيل الهندسية والحل أو الاستشارة *") },
                    placeholder = { Text("اشرح الحالة، الأبعاد، نسبة الخلط، أو الفكرة المطلوب مناقشتها...") },
                    minLines = 4,
                    maxLines = 7,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_post_body_input")
                )

                OutlinedButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    enabled = !isSubmitting && !isEncodingPhoto,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_post_pick_photo_button")
                ) {
                    if (isEncodingPhoto) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("جاري ضغط الصورة...")
                    } else {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (previewBitmap == null) "إرفاق صورة موقع أو مخطط" else "تغيير الصورة المرفقة")
                    }
                }

                val bmp = previewBitmap
                if (bmp != null) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "معاينة الصورة المرفقة",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        IconButton(
                            onClick = {
                                photoBase64 = null
                                previewBitmap = null
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                .size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "إزالة الصورة",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                if (localError != null) {
                    Text(
                        text = localError ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Button(
                    onClick = {
                        onSubmit(title, body, category, location.ifBlank { null }, photoBase64)
                    },
                    enabled = !isSubmitting && !isEncodingPhoto && title.trim().length >= 3 && body.trim().length >= 3,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("create_post_submit_button")
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("نشر في المجتمع", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun PostDetailModal(
    post: CommunityPost,
    isLiked: Boolean,
    currentUserId: String,
    commentsState: CommunityUiState<List<CommunityComment>>,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleSolved: () -> Unit,
    onAddComment: (text: String, photoBase64: String?, onClear: () -> Unit) -> Unit,
    onAcceptSolution: (commentId: String) -> Unit,
    onDeleteComment: (commentId: String) -> Unit,
    onReportComment: (comment: CommunityComment) -> Unit,
    onBlockCommentAuthor: (comment: CommunityComment) -> Unit,
    onImageClick: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val category = CommunityPostCategory.fromKey(post.category)
    val postBitmap = remember(post.photoBase64) {
        CommunityImageCodec.decodeBase64ToBitmap(post.photoBase64)
    }

    var commentText by remember { mutableStateOf("") }
    var commentPhotoBase64 by remember { mutableStateOf<String?>(null) }
    var commentPhotoPreview by remember { mutableStateOf<Bitmap?>(null) }
    var isEncodingCommentPhoto by remember { mutableStateOf(false) }

    val commentPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            isEncodingCommentPhoto = true
            scope.launch {
                val encoded = CommunityImageCodec.encodeUriToBoundedBase64(context, uri)
                commentPhotoBase64 = encoded
                commentPhotoPreview = CommunityImageCodec.decodeBase64ToBitmap(encoded)
                isEncodingCommentPhoto = false
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Surface(color = NavyPrimary, shadowElevation = 4.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "إغلاق",
                                tint = Color.White
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = category.labelAr,
                                color = SafetyAmber,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = post.title,
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (post.authorId == currentUserId) {
                            TextButton(onClick = onToggleSolved) {
                                Text(
                                    text = if (post.solved) "إعادة فتح" else "تم الحل ✓",
                                    color = if (post.solved) Color.White else SafetyAmber,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Body + Comments
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = post.authorName,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    if (!post.location.isNullOrBlank()) {
                                        Text(
                                            text = " • ${post.location}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.weight(1f))
                                    if (post.solved) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = SuccessGreen.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = "تم الحل ✓",
                                                color = SuccessGreen,
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = post.title,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = post.body,
                                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp)
                                )

                                if (postBitmap != null) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Image(
                                        bitmap = postBitmap.asImageBitmap(),
                                        contentDescription = "صورة المشاركة",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 260.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { onImageClick(postBitmap) }
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(onClick = onToggleLike) {
                                        Icon(
                                            imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                            contentDescription = "إعجاب",
                                            tint = if (isLiked) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("${post.likesCount} إعجاب")
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "${post.commentsCount} مشاركة بالحل",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            text = "الحلول والمناقشات الهندسية",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                    }

                    when (commentsState) {
                        is CommunityUiState.Loading -> {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                                }
                            }
                        }

                        is CommunityUiState.Error -> {
                            item {
                                Text(
                                    text = commentsState.message,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        is CommunityUiState.Success -> {
                            if (commentsState.data.isEmpty()) {
                                item {
                                    Text(
                                        text = "لا توجد حلول أو تعليقات بعد. شارك بخبرتك الهندسية!",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                items(commentsState.data, key = { it.id }) { comment ->
                                    CommentItemCard(
                                        comment = comment,
                                        isPostAuthor = post.authorId == currentUserId,
                                        isCommentAuthor = comment.authorId == currentUserId,
                                        onAcceptSolution = { onAcceptSolution(comment.id) },
                                        onDeleteComment = { onDeleteComment(comment.id) },
                                        onReportComment = { onReportComment(comment) },
                                        onBlockAuthor = { onBlockCommentAuthor(comment) },
                                        onImageClick = onImageClick
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom composer
                Surface(
                    tonalElevation = 6.dp,
                    shadowElevation = 6.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        val cBmp = commentPhotoPreview
                        if (cBmp != null) {
                            Box(modifier = Modifier.padding(bottom = 8.dp)) {
                                Image(
                                    bitmap = cBmp.asImageBitmap(),
                                    contentDescription = "معاينة صورة التعليق",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                )
                                IconButton(
                                    onClick = {
                                        commentPhotoBase64 = null
                                        commentPhotoPreview = null
                                    },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(22.dp)
                                        .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "إزالة",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    commentPhotoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                enabled = !isEncodingCommentPhoto && !isSubmitting
                            ) {
                                if (isEncodingCommentPhoto) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.AddPhotoAlternate,
                                        contentDescription = "إرفاق صورة بالتعليق",
                                        tint = NavyPrimary
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = commentText,
                                onValueChange = { commentText = it.take(2000) },
                                placeholder = { Text("اكتب حلاً هندسياً أو تعليقاً...") },
                                maxLines = 3,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("comment_text_input")
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            IconButton(
                                onClick = {
                                    onAddComment(commentText, commentPhotoBase64) {
                                        commentText = ""
                                        commentPhotoBase64 = null
                                        commentPhotoPreview = null
                                    }
                                },
                                enabled = commentText.isNotBlank() && !isSubmitting && !isEncodingCommentPhoto,
                                modifier = Modifier.testTag("comment_send_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "إرسال التعليق",
                                    tint = if (commentText.isNotBlank()) SafetyAmber else Color.Gray
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentItemCard(
    comment: CommunityComment,
    isPostAuthor: Boolean,
    isCommentAuthor: Boolean,
    onAcceptSolution: () -> Unit,
    onDeleteComment: () -> Unit,
    onReportComment: () -> Unit,
    onBlockAuthor: () -> Unit,
    onImageClick: (Bitmap) -> Unit
) {
    val decoded = remember(comment.photoBase64) {
        CommunityImageCodec.decodeBase64ToBitmap(comment.photoBase64)
    }
    var showCommentMenu by remember { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (comment.acceptedSolution) SuccessGreen.copy(alpha = 0.08f)
            else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = if (comment.acceptedSolution) 1.5.dp else 1.dp,
            color = if (comment.acceptedSolution) SuccessGreen else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = comment.authorName,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatTimestampAr(comment.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isCommentAuthor) {
                    IconButton(onClick = onDeleteComment, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "حذف التعليق",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    Box {
                        IconButton(
                            onClick = { showCommentMenu = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "خيارات التعليق",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showCommentMenu,
                            onDismissRequest = { showCommentMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("الإبلاغ عن التعليق") },
                                leadingIcon = {
                                    Icon(Icons.Default.Flag, contentDescription = null, tint = SafetyAmber)
                                },
                                onClick = {
                                    showCommentMenu = false
                                    onReportComment()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("حظر ${comment.authorName}", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Block,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    showCommentMenu = false
                                    onBlockAuthor()
                                }
                            )
                        }
                    }
                }
            }

            if (comment.acceptedSolution) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = null,
                        tint = SuccessGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "الحل المعتمد من صاحب الموضوع",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = SuccessGreen
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = comment.text,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp)
            )

            if (decoded != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Image(
                    bitmap = decoded.asImageBitmap(),
                    contentDescription = "صورة مرفقة بالتعليق",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onImageClick(decoded) }
                )
            }

            if (isPostAuthor && !comment.acceptedSolution) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onAcceptSolution) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = SuccessGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "اعتماد كحل",
                            color = SuccessGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

private fun categoryIcon(category: CommunityPostCategory): ImageVector = when (category) {
    CommunityPostCategory.SITE_PHOTO -> Icons.Default.PhotoCamera
    CommunityPostCategory.ENGINEERING_IDEA -> Icons.Default.Lightbulb
    CommunityPostCategory.FIELD_SOLUTION -> Icons.Default.Build
    CommunityPostCategory.CONSULTATION -> Icons.Default.HelpOutline
    CommunityPostCategory.BLUEPRINT_DISCUSSION -> Icons.Default.Architecture
}

private fun categoryColor(category: CommunityPostCategory): Color = when (category) {
    CommunityPostCategory.SITE_PHOTO -> BlueprintTeal
    CommunityPostCategory.ENGINEERING_IDEA -> SafetyAmber
    CommunityPostCategory.FIELD_SOLUTION -> SuccessGreen
    CommunityPostCategory.CONSULTATION -> Color(0xFF7C3AED)
    CommunityPostCategory.BLUEPRINT_DISCUSSION -> NavyPrimary
}

private fun formatTimestampAr(timestamp: Timestamp?): String {
    val ts = timestamp ?: return "الآن"
    val formatter = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
    return formatter.format(ts.toDate())
}

@Composable
private fun ReportContentDialog(
    targetTitle: String,
    isSubmitting: Boolean,
    showHideOption: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (reason: ReportReason, details: String?, hideImmediately: Boolean) -> Unit
) {
    var selectedReason by remember { mutableStateOf(ReportReason.INAPPROPRIATE) }
    var details by remember { mutableStateOf("") }
    var hideImmediately by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = {
            Text(
                text = "الإبلاغ عن محتوى",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = targetTitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                ReportReason.entries.forEach { reason ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { selectedReason = reason }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedReason == reason,
                            onClick = { selectedReason = reason }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = reason.labelAr,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                OutlinedTextField(
                    value = details,
                    onValueChange = { details = it.take(500) },
                    label = { Text("تفاصيل إضافية (اختياري)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                if (showHideOption) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { hideImmediately = !hideImmediately },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.Checkbox(
                            checked = hideImmediately,
                            onCheckedChange = { hideImmediately = it }
                        )
                        Text(
                            text = "إخفاء هذا المنشور من خلاصتي فوراً",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(selectedReason, details.ifBlank { null }, hideImmediately) },
                enabled = !isSubmitting,
                colors = ButtonDefaults.buttonColors(containerColor = SafetyAmber)
            ) {
                Text("إرسال البلاغ", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text("إلغاء")
            }
        }
    )
}
