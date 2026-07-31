package com.ahmadabuhasan.qrbarcode.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ScanHistoryDao {

    @Query("SELECT * FROM scan_history ORDER BY scannedAt DESC")
    fun observeAll(): LiveData<List<ScanHistoryEntity>>

    @Insert
    suspend fun insert(entity: ScanHistoryEntity): Long

    @Query("DELETE FROM scan_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM scan_history")
    suspend fun clear()
}
