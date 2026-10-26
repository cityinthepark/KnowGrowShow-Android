package com.stoneofhope.stoneofhope.data.bible.text

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BibleTextModule {

    /** Room copies the asset on first open (off the main thread, the first time a verse is read). */
    @Provides
    @Singleton
    fun provideBibleTextDatabase(@ApplicationContext context: Context): BibleTextDatabase =
        Room.databaseBuilder(context, BibleTextDatabase::class.java, BibleTextDatabase.NAME)
            .createFromAsset(BibleTextDatabase.ASSET_PATH)
            // A newer bundled text (higher VERSION) replaces the old copy; nothing in it is user data.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides fun bundledVerseDao(db: BibleTextDatabase) = db.verseDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class OnDemandVerseModule {
    @Binds abstract fun onDemandVerseSource(impl: UnavailableOnDemandVerseSource): OnDemandVerseSource
}
