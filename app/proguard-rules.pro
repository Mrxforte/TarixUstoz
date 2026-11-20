# Add project specific ProGuard / R8 rules here.

-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*, ElementValueAttribute

# Keep Data Models, DTOs & Serialization
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
    @kotlinx.serialization.SerialName <fields>;
}

# Keep Kotlinx Serialization Generated Serializers
-keepclassmembers class * {
    *** Companion;
}
-keepclassmembers class * {
    public static *** serializer(...);
}

# Room Database Entities & DAOs
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>();
}
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keepclassmembers class * {
    @androidx.room.TypeConverter *;
    @androidx.room.PrimaryKey *;
    @androidx.room.ColumnInfo *;
}

# App Network Models & Data Entities
-keepclassmembers class com.tarixustoz.app.data.remote.** { *; }
-keepclassmembers class com.tarixustoz.app.data.local.** { *; }
-keepclassmembers class com.tarixustoz.app.domain.model.** { *; }

# Retrofit HTTP Interface
-keepclassmembers class * {
    @retrofit2.http.* <methods>;
}

# Kotlin Coroutines
-keepclassmembers class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# Dagger / Hilt
-keepclassmembers class * {
    @javax.inject.Inject <init>(...);
    @javax.inject.Inject <fields>;
}

# Firebase & App Check
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# AdMob (Google Mobile Ads)
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**

# Coil
-keep class io.coilkt.** { *; }
-dontwarn io.coilkt.**

# Vico Charts
-keep class com.patrykandpatrick.vico.** { *; }
-dontwarn com.patrykandpatrick.vico.**
