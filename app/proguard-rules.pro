# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# Keep Kotlin metadata
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }

# Jetpack Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Keep ViewModel and LiveData classes
-keep class androidx.lifecycle.ViewModel { *; }
-keep class androidx.lifecycle.LiveData { *; }

# Fix OAuth Drive API failure for release builds
-keepattributes Signature,RuntimeVisibleAnnotations,AnnotationDefault

-keepclassmembers class * {
  @com.google.api.client.util.Key <fields>;
}

-dontwarn com.google.api.client.extensions.android.**
-dontwarn com.google.api.client.googleapis.extensions.android.**
-dontwarn com.google.android.gms.**

# Suppress warnings for certain classes
-dontwarn androidx.lifecycle.**
-dontwarn com.squareup.okhttp3.**

-keep,includedescriptorclasses class net.sqlcipher.** { *; }
-keep,includedescriptorclasses interface net.sqlcipher.** { *; }

# Захист класів авторизації та безпеки
# Обфускація назв методів і полів для безпеки  
-keepclassmembers class com.pasich.encly.core.security.** {
    !private <fields>;
    !private <methods>;
}

-keepclassmembers class com.pasich.encly.presentation.viewmodel.NewAuthViewModel {
    !private <fields>;
    !private <methods>;
}

# Захист від reverse engineering
-obfuscationdictionary dictionary.txt
-classobfuscationdictionary dictionary.txt
-packageobfuscationdictionary dictionary.txt

# Видаляємо debug інформацію
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# Видаляємо println statements
-assumenosideeffects class java.io.PrintStream {
    public void println(%);
    public void println(**);
}

# Додаткова обфускація для критичних класів
-keep class com.pasich.encly.core.security.SecurityManager {
    public <methods>;
}

-keep class com.pasich.encly.core.security.AuthenticationManager {
    public <methods>;
}

# Захищаємо від відображення (reflection)
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Видаляємо метадані для безпеки
-keepattributes !SourceFile,!LineNumberTable

# Додатковий захист для security методів
-assumenosideeffects class com.pasich.encly.core.security.SecurityManager {
    private <methods>;
}

# Обфускуємо назви методів безпеки
-keepclassmembers class com.pasich.encly.core.security.** {
    !public <methods>;
}

# Захищаємо від рефлексії
-keepnames class com.pasich.encly.core.security.SecurityManager
-keepnames class com.pasich.encly.core.security.AuthenticationManager

# Видаляємо unused код для зменшення attack surface
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*