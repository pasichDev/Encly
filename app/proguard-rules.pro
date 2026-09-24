# R8 rules for Encly (offline encrypted notes).
#
# Deliberately almost empty: everything the app needs kept at runtime is already covered by the
# consumer rules the libraries ship, and the app has no reflective code paths of its own.
#   - SQLCipher (JNI classes and native methods): sqlcipher-android's proguard.txt.
#   - Room: the KSP-generated *_Impl classes, instantiated reflectively by class name, are
#     kept by room-runtime's rules.
#   - Hilt / Dagger, Lifecycle ViewModels, Navigation, Compose, DataStore, security-crypto
#     (Tink): their own consumer rules.
#   - Gson: gson.jar ships the TypeToken/Signature rules. Note blocks are written and read
#     field by field in BlockSerializer/BlockDeserializer, never by reflection, so no model
#     class needs its field names kept (BlockConverterTest pins the stored key names).
#   - kotlinx.serialization: backup classes use the plugin-generated serializers, called
#     explicitly (BackupPayload.serializer()); the key names are string constants in that
#     generated code, and the library ships its own rules (BackupPayloadCodecTest pins them).
#   - kotlin-bip39: the wordlist is compiled Kotlin code, not a resource loaded by name.
# Add a rule here only for a concrete reflective, JNI or by-name lookup, next to a comment
# saying which one.

# --- Readable stack traces through the mapping file (mapping.txt of each release build) ---
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- Strip logging in release ---
# The app itself never logs to logcat; this also silences library logging, which could carry
# database or file metadata.
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}
# AppLogger is a no-op, but its arguments (string templates, exception reads) would still be
# evaluated; this removes the calls together with the argument building.
-assumenosideeffects class com.pasich.encly.core.AppLogger {
    public *** w(...);
    public *** e(...);
}

# --- Obfuscation dictionaries (reduce readability of the release) ---
-obfuscationdictionary dictionary.txt
-classobfuscationdictionary dictionary.txt
-packageobfuscationdictionary dictionary.txt
