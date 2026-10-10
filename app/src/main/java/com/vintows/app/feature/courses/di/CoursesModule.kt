package com.vintows.app.feature.courses.di

import com.vintows.app.feature.courses.data.CoursesApi
import com.vintows.app.feature.courses.data.CoursesRepositoryImpl
import com.vintows.app.feature.courses.domain.CoursesRepository
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
abstract class CoursesModule {

    @Binds
    abstract fun bindCoursesRepository(impl: CoursesRepositoryImpl): CoursesRepository

    companion object {
        @Provides
        @Singleton
        fun provideCoursesApi(retrofit: Retrofit): CoursesApi = retrofit.create()
    }
}
