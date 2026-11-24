-keep class cc.csxcl.xiaobuhabridge.** { *; }
-keep class io.github.libxposed.** { *; }
-keep class org.luckypray.dexkit.** { *; }

-adaptresourcefilecontents META-INF/xposed/java_init.list

-dontwarn io.github.libxposed.**
-dontwarn org.luckypray.dexkit.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
