# ============================================================================
# R8 rules for release builds.
# Libraries (Compose, OkHttp, Retrofit 2.11, Gson, Coil 3, Room, Firebase)
# ship their own consumer rules: do NOT add blanket "-keep class lib.** { *; }"
# rules here - they stop obfuscation of most of the app (Play Console flagged
# "Obfuscation 17%"). Only keep what our own code needs by name.
# ============================================================================

# Generic signatures + annotations: Retrofit reads suspend return types
# (ApiEnvelope<List<DealDto>>) and Gson reads @SerializedName.
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault
-keepattributes *Annotation*, Exceptions

# Retrofit service interfaces (Retrofit's own rules cover the rest)
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**

# Gson: official rules for TypeToken with R8 full mode
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

# API DTOs are read/written by Gson through reflection: keep their field
# names and constructors (small package; everything else stays obfuscated).
-keep class eg.deals.radar.network.** { *; }
# Room entities / domain models (small, also used by Gson in exports)
-keep class eg.deals.radar.db.** { *; }
-keep class eg.deals.domain.** { *; }
# Any other class of ours with @SerializedName fields
-keepclassmembers class eg.deals.radar.** {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ViewModels: only the constructors (default ViewModelProvider factories use them)
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# BuildConfig (read by name)
-keep class **.BuildConfig { *; }

# Room: generated DealDatabase_Impl is looked up by name (Room also ships this rule)
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# Remove ALL logging in release builds (including errors and warnings)
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
    public static *** wtf(...);
    public static *** println(...);
}

# Remove ALL SecureLogger methods in release builds
-assumenosideeffects class eg.deals.radar.util.SecureLogger {
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
    public static *** pii(...);
    public static *** network(...);
}

# Crash reports: keep file names + line numbers readable in Crashlytics
# (the mapping file uploaded at build time restores class/method names)
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
