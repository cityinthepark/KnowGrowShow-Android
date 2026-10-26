package com.stoneofhope.stoneofhope.data.local

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME).build()

    @Provides fun noteDao(db: AppDatabase) = db.noteDao()
    @Provides fun noteCommentDao(db: AppDatabase) = db.noteCommentDao()
    @Provides fun tagDao(db: AppDatabase) = db.tagDao()
    @Provides fun pinLogDao(db: AppDatabase) = db.pinLogDao()
    @Provides fun sermonSeriesDao(db: AppDatabase) = db.sermonSeriesDao()
    @Provides fun sermonDao(db: AppDatabase) = db.sermonDao()
    @Provides fun youTubeVideoDao(db: AppDatabase) = db.youTubeVideoDao()
    @Provides fun flashCardDao(db: AppDatabase) = db.flashCardDao()
    @Provides fun chapterLogDao(db: AppDatabase) = db.chapterLogDao()
    @Provides fun bookProgressDao(db: AppDatabase) = db.bookProgressDao()
    @Provides fun userVerseDao(db: AppDatabase) = db.userVerseDao()
    @Provides fun indexedTextDao(db: AppDatabase) = db.indexedTextDao()
    @Provides fun syncConflictDao(db: AppDatabase) = db.syncConflictDao()
    @Provides fun bibleCacheDao(db: AppDatabase) = db.bibleCacheDao()
}
