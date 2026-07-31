package com.ahmadabuhasan.qrbarcode.utils

object AppConfig {

    init {
        System.loadLibrary("native-lib")
    }

    @JvmStatic
    external fun bannerAdId(): String

    @JvmStatic
    external fun interstitialAdId(): String
}
