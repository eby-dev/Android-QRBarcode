package com.ahmadabuhasan.qrbarcode.ui.about

import android.os.Bundle
import android.view.MenuItem
import com.ahmadabuhasan.qrbarcode.BuildConfig
import com.ahmadabuhasan.qrbarcode.R
import com.ahmadabuhasan.qrbarcode.databinding.ActivityAboutBinding
import com.ahmadabuhasan.qrbarcode.utils.BaseActivity
import com.ahmadabuhasan.qrbarcode.utils.Utils
import com.ahmadabuhasan.qrbarcode.utils.Utils.Companion.interstitialAd
import com.google.android.gms.ads.AdRequest

class AboutActivity : BaseActivity() {

    private var binding: ActivityAboutBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding!!.root)

        supportActionBar?.apply {
            setHomeButtonEnabled(true)
            setDisplayHomeAsUpEnabled(true)
            setTitle(R.string.about)
        }

        binding?.version?.text = "%s%s".format(
            resources.getString(R.string.version),
            BuildConfig.VERSION_NAME
        )

        Utils().loadAd(this)
        showInterstitial()
        val adRequest = AdRequest.Builder().build()
        binding?.adViewAbout?.loadAd(adRequest)
    }

    private fun showInterstitial() {
        if (interstitialAd != null) {
            interstitialAd!!.show(this)
        } else {
            Utils().loadAd(this)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        binding = null
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            @Suppress("DEPRECATION")
            onBackPressed()
            finish()
        }
        return super.onOptionsItemSelected(item)
    }
}
