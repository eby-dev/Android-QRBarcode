package com.ahmadabuhasan.qrbarcode.utils

import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

// Deliberately not wired to any trigger. Interstitials are the highest-risk
// format for invalid traffic, so attach this only behind a frequency cap
// (e.g. every N scans with a minimum time gap), never on every screen entry.
class Utils {

    companion object {
        private val TAG = Utils::class.java.simpleName
        var interstitialAd: InterstitialAd? = null
    }

    fun loadAd(context: Context) {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            AppConfig.interstitialAdId(),
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(mInterstitialAd: InterstitialAd) {
                    interstitialAd = mInterstitialAd
                    Log.i(TAG, "onAdLoaded")
                    interstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            interstitialAd = null
                            Log.d(TAG, "The ad was dismissed.")
                        }

                        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                            interstitialAd = null
                            Log.d(TAG, "The ad failed to show.")
                        }

                        override fun onAdShowedFullScreenContent() {
                            Log.d(TAG, "The ad was shown.")
                        }
                    }
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.i(TAG, loadAdError.message)
                    interstitialAd = null
                    val error = "domain: ${loadAdError.domain}, code: ${loadAdError.code}, message: ${loadAdError.message}"
                    Log.d(TAG, "onAdFailedToLoad() with error: $error")
                }
            }
        )
    }
}
