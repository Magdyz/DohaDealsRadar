# Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }

# OkHttp
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }
-dontwarn okio.**

# Gson - CRITICAL for JSON parsing
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# DATA CLASSES - Only keep what's needed for serialization
-keep class qa.deals.doha.network.** { *; }  # API DTOs (needed by Retrofit/Gson)
-keep class qa.deals.doha.db.** { *; }       # Database entities (needed by Room)
-keep class qa.deals.domain.** { *; }        # Domain models (small, safe to keep)

# ViewModels - Keep class names (used by reflection in ViewModelProvider.Factory)
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-keep class **.*ViewModel { *; }
-keep class **.*ViewModelFactory { *; }

# BuildConfig - Keep class name (used by Class.forName in ImageLoaderConfig)
-keep class **.BuildConfig { *; }

# Obfuscate everything else for security
-keepclassmembers class qa.deals.doha.** {
    @com.google.gson.annotations.SerializedName <fields>;
}

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
-assumenosideeffects class qa.deals.doha.util.SecureLogger {
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
    public static *** pii(...);
    public static *** network(...);
}

# Room Database
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Coil image loading
-keep class coil.** { *; }
-dontwarn coil.**

# Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**