package com.vintows.app.core.di

import com.vintows.app.core.push.PushApi
import com.vintows.app.core.push.PushManager
import com.vintows.app.core.push.PushRegistrar
import com.vintows.app.feature.notifications.data.NotificationsApi
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
abstract class PushModule {

    @Binds
    abstract fun bindPushRegistrar(impl: PushManager): PushRegistrar

    companion object {
        @Provides
        @Singleton
        fun providePushApi(retrofit: Retrofit): PushApi = retrofit.create()

        @Provides
        @Singleton
        fun provideNotificationsApi(retrofit: Retrofit): NotificationsApi = retrofit.create()
    }
}
