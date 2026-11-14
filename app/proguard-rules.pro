# Keep line numbers and source file info for clean Play Console crash deobfuscation
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep domain models serialized by Firebase Firestore (toObjects / toObject)
-keep class com.example.domain.CommunityPost { *; }
-keep class com.example.domain.CommunityComment { *; }
-keep class com.example.domain.LikedPost { *; }
-keep class com.example.domain.CommunityPostCategory { *; }
-keep class com.example.domain.ReportReason { *; }

# Keep Room Entities and Database classes
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface *

# Keep Firebase / AppCheck / Credential Manager classes
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepclassmembers class * {
    @androidx.annotation.Keep <methods>;
    @androidx.annotation.Keep <fields>;
}
-keep class com.google.firebase.ai.** { *; }
-keep class com.google.firebase.firestore.** { *; }
-keep class com.google.firebase.appcheck.** { *; }
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }

# Suppress optional reflection warnings from Kotlin Coroutines / AndroidX
-dontwarn kotlinx.coroutines.**
-dontwarn org.slf4j.**
