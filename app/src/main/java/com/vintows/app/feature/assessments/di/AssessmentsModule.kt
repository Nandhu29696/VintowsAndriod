package com.vintows.app.feature.assessments.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.vintows.app.feature.assessments.data.AssessmentsApi
import com.vintows.app.feature.assessments.data.AssessmentsRepositoryImpl
import com.vintows.app.feature.assessments.data.AttemptsDataStore
import com.vintows.app.feature.assessments.domain.AssessmentsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.create
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AssessmentsModule {

    @Binds
    abstract fun bindAssessmentsRepository(impl: AssessmentsRepositoryImpl): AssessmentsRepository

    companion object {
        @Provides
        @Singleton
        fun provideAssessmentsApi(retrofit: Retrofit): AssessmentsApi = retrofit.create()

        @Provides
        @Singleton
        @AttemptsDataStore
        fun provideAttemptsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("vintows_attempts") }
    }
}
