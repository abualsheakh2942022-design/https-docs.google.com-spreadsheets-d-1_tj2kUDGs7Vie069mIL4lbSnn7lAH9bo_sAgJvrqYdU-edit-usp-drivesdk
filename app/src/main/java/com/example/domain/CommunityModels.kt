package com.example.domain

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class CommunityPostCategory(val key: String, val labelAr: String) {
    SITE_PHOTO("SITE_PHOTO", "صور موقع"),
    ENGINEERING_IDEA("ENGINEERING_IDEA", "أفكار هندسية"),
    FIELD_SOLUTION("FIELD_SOLUTION", "حلول وتنفيذ"),
    CONSULTATION("CONSULTATION", "استشارات"),
    BLUEPRINT_DISCUSSION("BLUEPRINT_DISCUSSION", "مناقشة مخططات");

    companion object {
        fun fromKey(key: String?): CommunityPostCategory =
            entries.firstOrNull { it.key == key } ?: SITE_PHOTO
    }
}

enum class ReportReason(val key: String, val labelAr: String) {
    INAPPROPRIATE("INAPPROPRIATE", "محتوى غير لائق أو مسيء"),
    MISLEADING_ENGINEERING("MISLEADING_ENGINEERING", "معلومات هندسية خطرة أو مضللة"),
    SPAM("SPAM", "إعلانات مزعجة أو محتوى غير متعلق"),
    PRIVACY_VIOLATION("PRIVACY_VIOLATION", "انتهاك الخصوصية أو بيانات شخصية")
}

@Keep
@IgnoreExtraProperties
data class CommunityPost(
    @DocumentId val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val title: String = "",
    val body: String = "",
    val category: String = CommunityPostCategory.SITE_PHOTO.key,
    val location: String? = null,
    val photoBase64: String? = null,
    val visibility: String = "PUBLIC",
    val likesCount: Long = 0L,
    val commentsCount: Long = 0L,
    val solved: Boolean = false,
    val acceptedCommentId: String? = null,
    val lastCommentId: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CommunityComment(
    @DocumentId val id: String = "",
    val postId: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val text: String = "",
    val photoBase64: String? = null,
    val visibility: String = "PUBLIC",
    val acceptedSolution: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class LikedPost(
    @DocumentId val id: String = "",
    val userId: String = "",
    val postId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)
