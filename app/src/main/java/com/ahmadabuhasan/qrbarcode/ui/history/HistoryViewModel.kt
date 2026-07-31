package com.ahmadabuhasan.qrbarcode.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.ahmadabuhasan.qrbarcode.data.AppDatabase
import com.ahmadabuhasan.qrbarcode.data.ScanHistoryEntity
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.get(application).scanHistoryDao()

    val history: LiveData<List<ScanHistoryEntity>> = dao.observeAll()

    fun delete(entity: ScanHistoryEntity) {
        viewModelScope.launch { dao.deleteById(entity.id) }
    }

    fun clearAll() {
        viewModelScope.launch { dao.clear() }
    }
}
