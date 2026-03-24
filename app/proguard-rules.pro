# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# ── SQLCipher ──
-keep class net.zetetic.** { *; }
-dontwarn net.zetetic.**

# ── AndroidX Security (EncryptedSharedPreferences) ──
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

# ── AndroidX Biometric ──
-keep class androidx.biometric.** { *; }
-dontwarn androidx.biometric.**

# ── Data models (used with Cursor column indices, reflection-sensitive) ──
-keep class com.gero.newpass.model.** { *; }

# ── daimajia animations ──
-keep class com.daimajia.** { *; }
-dontwarn com.daimajia.**

# ── View Binding generated classes ──
-keep class com.gero.newpass.databinding.** { *; }

# ── Keep line numbers for meaningful crash stack traces ──
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Strip Log calls in release builds ──
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# ── Fix Tink / Crypto missing annotations ──
-dontwarn javax.annotation.**