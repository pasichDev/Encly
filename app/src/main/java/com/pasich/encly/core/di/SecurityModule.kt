package com.pasich.encly.core.di

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.pasich.encly.core.security.AuthenticationManager
import com.pasich.encly.core.security.BiometricManager
import com.pasich.encly.core.security.SecurityManager
import com.pasich.encly.core.security.SeedPhraseManager
import com.pasich.encly.data.database.SecureDatabaseManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    @Provides
    @Singleton
    fun provideSecureStoragePrefs(@ApplicationContext context: Context): SharedPreferences {
        val masterKey =
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()

        return EncryptedSharedPreferences.create(
            context,
            "secure_prefs_v2",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    @Provides
    @Singleton
    fun provideSeedPhraseManager(
        @ApplicationContext context: Context
    ): SeedPhraseManager = SeedPhraseManager(context)

    @Provides
    @Singleton
    fun provideBiometricManager(
        @ApplicationContext context: Context
    ): BiometricManager = BiometricManager(context)

    @Provides
    @Singleton
    fun provideAuthenticationManager(
        secureStoragePrefs: SharedPreferences
    ): AuthenticationManager = AuthenticationManager(secureStoragePrefs)

    @Provides
    @Singleton
    fun provideSecurityManager(
        secureStoragePrefs: SharedPreferences,
        seedPhraseManager: SeedPhraseManager,
        secureDatabaseManager: SecureDatabaseManager,
        authenticationManager: AuthenticationManager,
        biometricManager: BiometricManager
    ): SecurityManager = SecurityManager(
        secureStoragePrefs,
        seedPhraseManager,
        secureDatabaseManager,
        authenticationManager,
        biometricManager
    )
}
