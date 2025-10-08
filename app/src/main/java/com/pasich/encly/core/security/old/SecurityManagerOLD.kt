package com.pasich.encly.core.security.old

import android.content.Context
import android.provider.Settings
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.core.content.edit
import com.pasich.encly.core.security.SeedPhraseManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurityManagerOLD @Inject constructor(
    private val context: Context,
    private val cryptoManager: CryptoManager,
    private val seedPhraseManager: SeedPhraseManager,
    private val authenticationManagerOLD: AuthenticationManagerOLD
) {
    
    companion object {
        private const val SECURITY_PREFS = "security_preferences"
        private const val MASTER_KEY_SALT_KEY = "master_key_salt"
        private const val ENCRYPTED_MASTER_KEY = "encrypted_master_key"
        private const val MASTER_KEY_IV = "master_key_iv"
        private const val IS_INITIALIZED_KEY = "is_initialized"
        private const val SECURITY_TYPE_KEY = "security_type"
        
        // Fallback сід-фраза для користувачів, які не хочуть керувати власною безпекою
        const val FALLBACK_SEED_PHRASE = "lazy chalk prefer parade enhance april panel wealth battle unlock paddle grit"
    }
    
    enum class SecurityType {
        /**
         * Користувач створив власну сід-фразу і керує нею самостійно.
         * Потребує введення сід-фрази для розблокування після перезапуску.
         */
        USER_MANAGED,
        
        /**
         * Використовується автоматична fallback сід-фраза.
         * Система автоматично розблоковується без участі користувача.
         */
        AUTO_MANAGED
    }

    private val securityPrefs = EncryptedSharedPreferences.create(
        context,
        SECURITY_PREFS,
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _securityState = MutableStateFlow<SecurityState>(SecurityState.NotInitialized)
    val securityState: Flow<SecurityState> = _securityState.asStateFlow()

    // Кешовані ключі (очищуються при logout)
    private var cachedMasterKey: ByteArray? = null
    private var cachedEncryptionKey: ByteArray? = null
    private var cachedAuthenticationKey: ByteArray? = null

    init {
        // Перевіряємо початковий стан системи
        if (isInitialized()) {
            if (!authenticationManagerOLD.isAuthEnabled()) {
                // Якщо автентифікація вимкнена, автоматично розблоковуємо систему
                Log.d("SecurityManager", "Автентифікація вимкнена, автоматично розблоковуємо систему")
                tryAutoUnlock()
            } else {
                _securityState.value = SecurityState.Locked
            }
        }
    }

    sealed class SecurityState {
        object NotInitialized : SecurityState()
        object Initialized : SecurityState()
        object Unlocked : SecurityState()
        object Locked : SecurityState()
    }

    /**
     * Ініціалізує систему безпеки з новою сід-фразою
     */
    suspend fun initializeWithSeedPhrase(seedPhrase: CharArray, securityType: SecurityType = SecurityType.USER_MANAGED): Result<Unit> {
        return try {
            Log.d("SecurityManager", "Ініціалізація системи безпеки з типом: $securityType")
            
            // Генеруємо сіль для master key
            val salt = cryptoManager.generateSalt()
            
            // Генеруємо master key з сід-фрази
            val masterKey = cryptoManager.deriveMasterKey(seedPhrase, salt)
            
            // Зберігаємо сід-фразу
                //   seedPhraseManager.storeSeedHash(seedPhrase)
            
            // Шифруємо та зберігаємо master key
            val deviceKey = generateDeviceKey()
            val encryptedMasterKey = cryptoManager.encrypt(masterKey, deviceKey)
            
            securityPrefs.edit {
                putString(MASTER_KEY_SALT_KEY, Base64.encodeToString(salt, Base64.DEFAULT))
                putString(ENCRYPTED_MASTER_KEY, Base64.encodeToString(encryptedMasterKey.data, Base64.DEFAULT))
                putString(MASTER_KEY_IV, Base64.encodeToString(encryptedMasterKey.iv, Base64.DEFAULT))
                putBoolean(IS_INITIALIZED_KEY, true)
                putString(SECURITY_TYPE_KEY, securityType.name)
            }
            
            // Кешуємо ключі
            Log.d("SecurityManager", "initializeWithSeedPhrase: кешування ключів")
            cacheMasterKey(masterKey)
            
            Log.d("SecurityManager", "initializeWithSeedPhrase: встановлення стану Unlocked")
            _securityState.value = SecurityState.Unlocked
            
            // Очищуємо чутливі дані
            Log.d("SecurityManager", "initializeWithSeedPhrase: очищення оригінальних ключів")
            cryptoManager.clearSensitiveData(masterKey)
            cryptoManager.clearSensitiveData(deviceKey)
            
            Result.success(Unit)
        } catch (e: Exception) {
            _securityState.value = SecurityState.NotInitialized
            Result.failure(e)
        }
    }

    /**
     * Ініціалізує систему з fallback сід-фразою (автоматичний режим)
     */
    suspend fun initializeWithFallbackSecurity(): Result<Unit> {
        Log.d("SecurityManager", "Ініціалізація з fallback безпекою")
        return initializeWithSeedPhrase(FALLBACK_SEED_PHRASE.toCharArray(), SecurityType.AUTO_MANAGED)
    }

    /**
     * Розблоковує систему з сід-фразою
     */
    suspend fun unlockWithSeedPhrase(seedPhrase: CharArray): Result<Unit> {
        return try {
            // Перевіряємо сід-фразу
            if (!seedPhraseManager.verifyMnemonic(seedPhrase)) {
                return Result.failure(SecurityException("Невірна сід-фраза"))
            }
            
            // Отримуємо збережену сіль
            val saltBase64 = securityPrefs.getString(MASTER_KEY_SALT_KEY, null)
                ?: return Result.failure(IllegalStateException("Сіль не знайдена"))
            val salt = Base64.decode(saltBase64, Base64.DEFAULT)
            
            // Генеруємо master key
            val masterKey = cryptoManager.deriveMasterKey(seedPhrase, salt)
            
            // Кешуємо ключі
            cacheMasterKey(masterKey)
            
            _securityState.value = SecurityState.Unlocked
            
            // Очищуємо чутливі дані
            cryptoManager.clearSensitiveData(masterKey)
            
            Result.success(Unit)
        } catch (e: Exception) {
            _securityState.value = SecurityState.Locked
            Result.failure(e)
        }
    }

    /**
     * Розблоковує систему через PIN/біометрію
     */
    suspend fun unlockWithLocalAuth(): Result<Unit> {
        return try {
            // Отримуємо зашифрований master key
            val encryptedKeyBase64 = securityPrefs.getString(ENCRYPTED_MASTER_KEY, null)
                ?: return Result.failure(IllegalStateException("Зашифрований ключ не знайдений"))
            val ivBase64 = securityPrefs.getString(MASTER_KEY_IV, null)
                ?: return Result.failure(IllegalStateException("IV не знайдений"))
            
            val encryptedKeyData = Base64.decode(encryptedKeyBase64, Base64.DEFAULT)
            val iv = Base64.decode(ivBase64, Base64.DEFAULT)
            
            // Дешифруємо master key
            val deviceKey = generateDeviceKey()
            val encryptedData = EncryptedData(encryptedKeyData, iv)
            val masterKey = cryptoManager.decrypt(encryptedData, deviceKey)
            
            // Кешуємо ключі
            cacheMasterKey(masterKey)
            
            _securityState.value = SecurityState.Unlocked
            
            // Очищуємо чутливі дані
            cryptoManager.clearSensitiveData(masterKey)
            cryptoManager.clearSensitiveData(deviceKey)
            
            Result.success(Unit)
        } catch (e: Exception) {
            _securityState.value = SecurityState.Locked
            Result.failure(e)
        }
    }

    /**
     * Блокує систему
     */
    fun lock() {
        clearCachedKeys()
        _securityState.value = SecurityState.Locked
    }

    /**
     * Перевіряє, чи ініціалізована система
     */
    fun isInitialized(): Boolean {
        return securityPrefs.getBoolean(IS_INITIALIZED_KEY, false)
    }

    /**
     * Отримує ключ шифрування для конкретної мети
     */
    fun getEncryptionKey(purpose: String): ByteArray? {
        Log.d("SecurityManager", "Запит ключа для цілі: $purpose")
        Log.d("SecurityManager", "cachedMasterKey != null: ${cachedMasterKey != null}")
        return cachedMasterKey?.let { masterKey ->
            Log.d("SecurityManager", "Генерація ключа шифрування для цілі: $purpose")
            cryptoManager.deriveEncryptionKey(masterKey, purpose)
        } ?: run {
            Log.e("SecurityManager", "cachedMasterKey є null")
            null
        }
    }

    /**
     * Отримує ключ автентифікації
     */
    fun getAuthenticationKey(): ByteArray? {
        return cachedAuthenticationKey
    }

    /**
     * Шифрує дані
     */
    fun encryptData(data: ByteArray, purpose: String): EncryptedData? {
        return getEncryptionKey(purpose)?.let { key ->
            cryptoManager.encrypt(data, key)
        }
    }

    /**
     * Дешифрує дані
     */
    fun decryptData(encryptedData: EncryptedData, purpose: String): ByteArray? {
        return getEncryptionKey(purpose)?.let { key ->
            cryptoManager.decrypt(encryptedData, key)
        }
    }

    private fun cacheMasterKey(masterKey: ByteArray) {
        Log.d("SecurityManager", "Кешування master key")
        cachedMasterKey = masterKey.copyOf()
        cachedEncryptionKey = cryptoManager.deriveEncryptionKey(masterKey, "encryption")
        cachedAuthenticationKey = cryptoManager.deriveEncryptionKey(masterKey, "authentication")
        Log.d("SecurityManager", "Master key закешовано, cachedMasterKey != null: ${cachedMasterKey != null}")
    }

    private fun clearCachedKeys() {
        Log.d("SecurityManager", "Очищення кешованих ключів")
        cachedMasterKey?.let { cryptoManager.clearSensitiveData(it) }
        cachedEncryptionKey?.let { cryptoManager.clearSensitiveData(it) }
        cachedAuthenticationKey?.let { cryptoManager.clearSensitiveData(it) }
        
        cachedMasterKey = null
        cachedEncryptionKey = null
        cachedAuthenticationKey = null
        Log.d("SecurityManager", "Кешовані ключі очищено")
    }


    private fun generateDeviceKey(): ByteArray {
        // Генеруємо ключ на основі характеристик пристрою
        val deviceId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )
        
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest("encly_$deviceId".toByteArray())
    }

    /**
     * Спробує автоматично розблокувати систему без автентифікації
     */
    private fun tryAutoUnlock() {
        try {
            Log.d("SecurityManager", "Спроба автоматичного розблокування...")
            
            // Отримуємо збережений зашифрований master key
            val encryptedKeyBase64 = securityPrefs.getString(ENCRYPTED_MASTER_KEY, null)
            val ivBase64 = securityPrefs.getString(MASTER_KEY_IV, null)
            
            Log.d("SecurityManager", "encryptedKeyBase64 != null: ${encryptedKeyBase64 != null}")
            Log.d("SecurityManager", "ivBase64 != null: ${ivBase64 != null}")
            
            if (encryptedKeyBase64 != null && ivBase64 != null) {
                val encryptedKeyData = Base64.decode(encryptedKeyBase64, Base64.DEFAULT)
                val iv = Base64.decode(ivBase64, Base64.DEFAULT)
                
                Log.d("SecurityManager", "Розшифровуємо master key...")
                
                // Розшифровуємо master key за допомогою device key
                val deviceKey = generateDeviceKey()
                val encryptedData = EncryptedData(encryptedKeyData, iv)
                val masterKey = cryptoManager.decrypt(encryptedData, deviceKey)
                
                Log.d("SecurityManager", "Master key розшифровано, розмір: ${masterKey.size}")
                
                // Кешуємо ключі
                cacheMasterKey(masterKey)
                
                _securityState.value = SecurityState.Unlocked
                Log.d("SecurityManager", "Автоматичне розблокування успішне")
                
                // Очищуємо чутливі дані
                cryptoManager.clearSensitiveData(masterKey)
                cryptoManager.clearSensitiveData(deviceKey)
            } else {
                Log.e("SecurityManager", "Не вдалося отримати збережений master key")
                _securityState.value = SecurityState.Locked
            }
        } catch (e: Exception) {
            Log.e("SecurityManager", "Помилка автоматичного розблокування", e)
            _securityState.value = SecurityState.Locked
        }
    }

    /**
     * Отримує поточний тип системи безпеки
     */
    fun getSecurityType(): SecurityType {
        val typeName = securityPrefs.getString(SECURITY_TYPE_KEY, SecurityType.USER_MANAGED.name)
        return try {
            SecurityType.valueOf(typeName ?: SecurityType.USER_MANAGED.name)
        } catch (e: IllegalArgumentException) {
            SecurityType.USER_MANAGED
        }
    }

    /**
     * Перевіряє чи використовується fallback безпека
     */
    fun isUsingFallbackSecurity(): Boolean {
        return getSecurityType() == SecurityType.AUTO_MANAGED
    }

    /**
     * Перевіряє чи користувач керує своєю сід-фразою
     */
    fun isUserManagedSecurity(): Boolean {
        return getSecurityType() == SecurityType.USER_MANAGED
    }

    /**
     * Скидає всю систему безпеки
     */
    fun reset() {
        clearCachedKeys()
        seedPhraseManager.clearStoredSeed()
        securityPrefs.edit().clear().apply()
        _securityState.value = SecurityState.NotInitialized
    }
}
