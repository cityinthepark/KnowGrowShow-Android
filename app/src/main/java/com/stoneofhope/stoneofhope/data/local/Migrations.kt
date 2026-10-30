package com.stoneofhope.stoneofhope.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Schema history; each step matches the exported `schemas/…AppDatabase/<n>.json`. */
object Migrations {
    /** 2: `onlineChurches` ("My churches", docs/CHURCH_IMPORT_PLAN.md §2.5). */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `onlineChurches` (`churchKey` TEXT NOT NULL, `isPrimary` INTEGER NOT NULL, " +
                    "`importedAt` INTEGER NOT NULL, `lastImportedVersion` TEXT, `updatedAt` INTEGER NOT NULL, " +
                    "`deletedAt` INTEGER, `needsSync` INTEGER NOT NULL, `lastSyncedAt` INTEGER, PRIMARY KEY(`churchKey`))"
            )
        }
    }

    val ALL = arrayOf(MIGRATION_1_2)
}
