package com.stoneofhope.stoneofhope.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.stoneofhope.stoneofhope.data.local.entity.OnlineChurchEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OnlineChurchDao {
    @Upsert suspend fun upsert(church: OnlineChurchEntity)

    @Query("SELECT * FROM onlineChurches WHERE churchKey = :churchKey")
    suspend fun getByKey(churchKey: String): OnlineChurchEntity?

    /** Followed churches, primary first, then by first import. */
    @Query("SELECT * FROM onlineChurches WHERE deletedAt IS NULL ORDER BY isPrimary DESC, importedAt, churchKey")
    suspend fun active(): List<OnlineChurchEntity>

    @Query("SELECT * FROM onlineChurches WHERE deletedAt IS NULL ORDER BY isPrimary DESC, importedAt, churchKey")
    fun observeActive(): Flow<List<OnlineChurchEntity>>

    @Query("SELECT * FROM onlineChurches WHERE needsSync = 1")
    suspend fun dirty(): List<OnlineChurchEntity>

    /** Tombstones included: the cloud merge needs to know what was unfollowed here. */
    @Query("SELECT * FROM onlineChurches")
    suspend fun all(): List<OnlineChurchEntity>

    @Query("DELETE FROM onlineChurches WHERE churchKey = :churchKey")
    suspend fun hardDelete(churchKey: String)

    @Query("DELETE FROM onlineChurches WHERE deletedAt IS NOT NULL")
    suspend fun purgeAllTombstones(): Int

    @Query("UPDATE onlineChurches SET needsSync = 1")
    suspend fun markAllDirty()
}
