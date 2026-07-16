# Shizuku - keep ShizukuProvider
-keep class rikka.shizuku.** { *; }
-dontwarn rikka.shizuku.**

# Gson
-keepattributes Signature
-keep class com.google.gson.** { *; }
-keepclassmembers class com.example.genshinvulkan.config.VulkanConfig { *; }
