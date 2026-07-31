package com.ahmadabuhasan.qrbarcode.ui.about

import android.os.Bundle
import android.view.MenuItem
import com.ahmadabuhasan.qrbarcode.BuildConfig
import com.ahmadabuhasan.qrbarcode.R
import com.ahmadabuhasan.qrbarcode.databinding.ActivityAboutBinding
import com.ahmadabuhasan.qrbarcode.utils.BaseActivity

class AboutActivity : BaseActivity() {

    private lateinit var binding: ActivityAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.apply {
            setHomeButtonEnabled(true)
            setDisplayHomeAsUpEnabled(true)
            setTitle(R.string.about)
        }

        binding.version.text = getString(R.string.version) + BuildConfig.VERSION_NAME
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
