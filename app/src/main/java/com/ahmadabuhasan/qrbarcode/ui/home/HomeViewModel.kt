package com.ahmadabuhasan.qrbarcode.ui.home

import android.app.Application
import androidx.lifecycle.LiveData
import com.ahmadabuhasan.qrbarcode.data.ScanHistoryEntity
import com.ahmadabuhasan.qrbarcode.ui.main.ScanViewModel

class HomeViewModel(application: Application) : ScanViewModel(application) {

    // Three rows keep the dashboard on one screen without crowding the banner.
    val recentScans: LiveData<List<ScanHistoryEntity>> = dao.observeRecent(RECENT_LIMIT)

    private companion object {
        const val RECENT_LIMIT = 3
    }
}
