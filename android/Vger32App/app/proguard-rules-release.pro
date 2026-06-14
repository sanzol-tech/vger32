# ==========================================
# OPTIMIZATION
# ==========================================

-optimizationpasses 5
-allowaccessmodification

# ==========================================
# DEBUGGING (keep line numbers for crash reports)
# ==========================================

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ==========================================
# ENTRY POINTS — never obfuscate
# ==========================================

# Android components
-keep public class * extends android.app.Activity
-keep public class * extends androidx.fragment.app.Fragment
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.preference.Preference
-keep public class * extends androidx.preference.PreferenceFragmentCompat

# Custom views
-keepclassmembers class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# ==========================================
# VIEWMODELS (instantiated by reflection)
# ==========================================

-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>();
}

-keepclassmembers class * extends androidx.lifecycle.AndroidViewModel {
    <init>(android.app.Application);
}

# ==========================================
# NAVIGATION (destinations are referenced by class name)
# ==========================================

-keep class androidx.navigation.fragment.NavHostFragment
-keep class * extends androidx.navigation.NavDestination

# ==========================================
# SERIALIZATION — ModuleStore format
# ==========================================

-keepclassmembers class ar.vger32app.module.Module {
    public <init>(...);
    public java.lang.String getMid();
    public java.lang.String getIp();
    public java.lang.String getPid();
    public java.lang.String getChip();
    public java.lang.String getBrd();
    public java.lang.String getVer();
    public java.lang.String getSts();
    public long getLastSeenAt();
    public ar.vger32app.module.DiscoverySource getLastDiscoverySource();
}

-keep class ar.vger32app.module.DiscoverySource { *; }

# ==========================================
# WAYPOINT STORAGE (JSON serialization)
# ==========================================

-keepclassmembers class ar.vger32app.localizer.Waypoint {
    public java.lang.String getId();
    public java.lang.String getName();
    public long getTimestamp();
    public java.lang.String getSource();
}

-keepclassmembers class ar.vger32app.localizer.WifiNetwork {
    public java.lang.String getMac();
    public int getChannel();
    public int getRssi();
}

# ==========================================
# ENUMS (values() is called by valueOf)
# ==========================================

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ==========================================
# DATABINDING (generated classes)
# ==========================================

-keep class ar.vger32app.databinding.** { *; }
-keepclassmembers class ar.vger32app.databinding.** {
    public static ** inflate(...);
    public static ** bind(android.view.View);
}

# ==========================================
# OKHTTP
# ==========================================

-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**

# ==========================================
# PLAY SERVICES LOCATION
# ==========================================

-dontwarn com.google.android.gms.**
-keep class com.google.android.gms.** { *; }

# ==========================================
# ANDROIDX
# ==========================================

-keep class androidx.core.app.CoreComponentFactory { *; }
-keep class androidx.lifecycle.** { *; }

# ==========================================
# EXCEPTIONS (keep exception names in stack traces)
# ==========================================

-keepattributes Exceptions,InnerClasses,Signature,Deprecated,EnclosingMethod

# ==========================================
# REMOVE LOGGING IN RELEASE (optional, reduces size)
# ==========================================

-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(...);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}