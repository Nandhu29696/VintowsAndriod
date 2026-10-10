package com.vintows.app.feature.gamification.di

import com.vintows.app.feature.gamification.data.GamificationApi
import com.vintows.app.feature.gamification.data.GamificationRepositoryImpl
import com.vintows.app.feature.gamification.domain.GamificationRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.create
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class GamificationModule {

    @Binds
    abstract fun bindGamificationRepository(impl: GamificationRepositoryImpl): GamificationRepository

    companion object {
        @Provides
        @Singleton
        fun provideGamificationApi(retrofit: Retrofit): GamificationApi = retrofit.create()
    }
}
