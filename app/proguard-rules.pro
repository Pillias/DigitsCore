# Proguard rules for DigitsCore

# Room Database
-keep class androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# DigitsCore Models & Entities
-keep class com.digitscore.app.model.** { *; }
-keep class com.digitscore.app.data.entity.** { *; }
-keep class com.digitscore.app.engine.** { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# Jetpack Compose
-keepclassmembers class * extends androidx.compose.ui.Modifier { *; }

# Android App Icons & Notification Bitmaps
-keepclassmembers class android.graphics.Bitmap { *; }
-keepclassmembers class android.graphics.Canvas { *; }

# DigitsCore - Room entities and models
-keep class com.digitscore.app.data.entity.** { *; }
-keep class com.digitscore.app.model.** { *; }
-keep class com.digitscore.app.engine.ScoreDetail { *; }
-keep class com.digitscore.app.engine.ScoreGrade { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
