# ---------- 通用 ----------
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod

# ---------- Kotlin ----------
-keepclassmembers class **$WhenMappings { <fields>; }
-keepclassmembers class kotlin.Metadata { public <methods>; }
-dontwarn kotlinx.coroutines.**

# ---------- OkHttp ----------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# ---------- DataStore / 序列化 ----------
-keepclassmembers class * {
    @androidx.datastore.preferences.core.* <fields>;
}

# ---------- 保留行号以便线上排查 ----------
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
