package com.pasich.encly.core.di

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.pasich.encly.core.security.AuthenticationManager
import com.pasich.encly.core.security.HmacIntegrityManager
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
            "secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    @Provides
    @Singleton
    fun provideHmacIntegrityManager(@ApplicationContext context: Context): HmacIntegrityManager =
        HmacIntegrityManager(context)


    @Provides
    @Singleton
    fun provideSeedPhraseManager(
        @ApplicationContext context: Context,
        hmacIntegrityManager: HmacIntegrityManager,
    ): SeedPhraseManager = SeedPhraseManager(context, hmacIntegrityManager)

    @Provides
    @Singleton
    fun provideAuthenticationManager(
        secureStoragePrefs: SharedPreferences,
        seedPhraseManager: SeedPhraseManager,
    ): AuthenticationManager = AuthenticationManager(secureStoragePrefs, seedPhraseManager)


    @Provides
    @Singleton
    fun provideSecurityManager(
        secureStoragePrefs: SharedPreferences,
        seedPhraseManager: SeedPhraseManager,
        secureDatabaseManager: SecureDatabaseManager,
        authenticationManager: AuthenticationManager,
    ): SecurityManager = SecurityManager(
        secureStoragePrefs, seedPhraseManager, secureDatabaseManager, authenticationManager
    )

}
