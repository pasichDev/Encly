package com.pasich.encly.core.di

import com.pasich.encly.data.database.DatabaseProvider
import com.pasich.encly.data.database.SecureDatabaseManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DatabaseModule {
    @Binds
    @Singleton
    abstract fun bindDatabaseProvider(
        secureDatabaseManager: SecureDatabaseManager
    ): DatabaseProvider
}
