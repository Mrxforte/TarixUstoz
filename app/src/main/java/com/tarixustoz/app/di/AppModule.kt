package com.tarixustoz.app.di

import android.content.Context
import androidx.room.Room
import com.tarixustoz.app.data.local.AppDatabase
import com.tarixustoz.app.data.repository.ContentRepository
import com.tarixustoz.app.data.repository.MistakesRepository
import com.tarixustoz.app.data.repository.UserPreferencesRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "tarix_ustoz.db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    @Singleton
    fun provideContentRepository(
        @ApplicationContext context: Context,
        db: AppDatabase
    ): ContentRepository = ContentRepository(context, db)

    @Provides
    @Singleton
    fun provideMistakesRepository(db: AppDatabase, content: ContentRepository): MistakesRepository =
        MistakesRepository(db, content)

    @Provides
    @Singleton
    fun provideUserPreferencesRepository(@ApplicationContext context: Context): UserPreferencesRepository =
        UserPreferencesRepository(context)
}
