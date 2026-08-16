package com.gymmonth.app.di

import com.gymmonth.app.health.HealthConnectDataSource
import com.gymmonth.app.health.HealthConnectDataSourceImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    @Singleton
    abstract fun bindHealthConnectDataSource(
        impl: HealthConnectDataSourceImpl,
    ): HealthConnectDataSource
}
