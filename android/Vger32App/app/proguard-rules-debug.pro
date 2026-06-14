# proguard-rules-debug.pro

# Debug — no obfuscation, no optimizations that break debugging
-dontobfuscate
-dontoptimize

# Keep everything debuggable
-keepattributes SourceFile,LineNumberTable

# No stripping of debug info
-keepattributes LocalVariableTable,LocalVariableTypeTable

# Keep all classes and members for debugging
-keep class ar.vger32app.** { *; }
-keepclassmembers class ar.vger32app.** {
    *;
}