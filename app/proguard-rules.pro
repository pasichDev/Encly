# ProGuard / R8 rules for Encly (offline encrypted notes).

# --- Debuggable, mapping-friendly stack traces ---
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Needed for Gson generics and (de)serialization annotations.
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

# --- Kotlin ---
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }

# --- Jetpack Compose ---
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# --- Lifecycle ---
-keep class androidx.lifecycle.ViewModel { *; }
-keep class androidx.lifecycle.LiveData { *; }
-dontwarn androidx.lifecycle.**

# --- SQLCipher ---
-keep,includedescriptorclasses class net.sqlcipher.** { *; }
-keep,includedescriptorclasses interface net.sqlcipher.** { *; }

# --- Room ---
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# --- Gson ---
# Models are (de)serialized (some by reflection via context.serialize), so keep their
# fields/names intact. Custom Block adapters read fields by name, so names must survive.
-keep class com.pasich.encly.data.model.** { *; }
-keep class com.pasich.encly.domain.model.** { *; }
-keep class com.pasich.encly.dynamicBlocks.Block { *; }
-keep class com.pasich.encly.dynamicBlocks.Block$* { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
# Gson generic type tokens.
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-dontwarn sun.misc.**

# --- kotlinx.serialization ---
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.pasich.encly.**$$serializer { *; }
-keepclassmembers class com.pasich.encly.** {
    *** Companion;
}
-if @kotlinx.serialization.Serializable class **
-keep class <1> { *; }

# --- Strip logging in release ---
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}
-assumenosideeffects class java.io.PrintStream {
    public void println(%);
    public void println(**);
}

# --- Obfuscation dictionaries (reduce readability of the release) ---
-obfuscationdictionary dictionary.txt
-classobfuscationdictionary dictionary.txt
-packageobfuscationdictionary dictionary.txt
