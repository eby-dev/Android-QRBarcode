package com.ahmadabuhasan.qrbarcode

import android.app.Application
// import com.google.android.gms.ads.MobileAds

class MyApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Ads disabled while AdMob publisher pub-8638037215789792 is suspended
        // for invalid traffic (29-day suspension notified 2026-07-24).
        // MobileAds.initialize(this)
    }
}