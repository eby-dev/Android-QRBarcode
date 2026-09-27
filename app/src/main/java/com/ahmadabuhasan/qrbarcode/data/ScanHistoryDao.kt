package com.ahmadabuhasan.qrbarcode.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface ScanHistoryDao {

    @Query("SELECT * FROM scan_history ORDER BY scannedAt DESC")
    fun observeAll(): LiveData<List<ScanHistoryEntity>>

    @Insert
    suspend fun insert(entity: ScanHistoryEntity): Long

    @Query("SELECT id FROM scan_history WHERE content = :content AND format = :format LIMIT 1")
    suspend fun findIdByContent(content: String, format: String): Long?

    @Query("UPDATE scan_history SET scannedAt = :scannedAt WHERE id = :id")
    suspend fun touchScannedAt(id: Long, scannedAt: Long)

    // Insert-or-bump: if a row with the same content+format already exists,
    // bump its scannedAt so it moves to the top instead of adding a duplicate.
    @Transaction
    suspend fun upsert(entity: ScanHistoryEntity): Long {
        val existingId = findIdByContent(entity.content, entity.format)
        return if (existingId != null) {
            touchScannedAt(existingId, entity.scannedAt)
            existingId
        } else {
            insert(entity)
        }
    }

    @Query("DELETE FROM scan_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM scan_history")
    suspend fun clear()
}
