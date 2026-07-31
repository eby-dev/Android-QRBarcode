package com.ahmadabuhasan.qrbarcode.ui.wadirect

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.viewModels
import com.ahmadabuhasan.qrbarcode.R
import com.ahmadabuhasan.qrbarcode.databinding.ActivityWaDirectBinding
import com.ahmadabuhasan.qrbarcode.utils.AppConfig
import com.ahmadabuhasan.qrbarcode.utils.BaseActivity
import com.google.android.gms.ads.AdRequest
import java.net.URLEncoder

class WaDirectActivity : BaseActivity() {

    private val viewModel: WaDirectViewModel by viewModels()
    private lateinit var binding: ActivityWaDirectBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWaDirectBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.apply {
            setHomeButtonEnabled(true)
            setDisplayHomeAsUpEnabled(true)
            setTitle(R.string.wa_direct)
        }

        binding.adViewWaDirect.adUnitId = AppConfig.bannerAdId()
        binding.adViewWaDirect.loadAd(AdRequest.Builder().build())

        binding.btnOpen.setOnClickListener {
            viewModel.onOpenClicked(
                phone = binding.editPhone.text?.toString() ?: "",
                countryCode = binding.ccp.selectedCountryCode,
                message = binding.editMessage.text?.toString() ?: ""
            )
        }

        observeViewModel()
    }

    private fun observeViewModel() {
        viewModel.openWhatsApp.observe(this) { params ->
            params ?: return@observe
            openWhatsApp(params.number, params.message)
            viewModel.onWhatsAppOpened()
        }

        viewModel.error.observe(this) { error ->
            error ?: return@observe
            Toast.makeText(this, getString(R.string.wa_direct_invalid), Toast.LENGTH_SHORT).show()
            viewModel.onErrorShown()
        }
    }

    private fun openWhatsApp(number: String, message: String) {
        val encodedMessage = URLEncoder.encode(message, "UTF-8")
        val url = if (message.isNotEmpty()) {
            "https://wa.me/$number?text=$encodedMessage"
        } else {
            "https://wa.me/$number"
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            Toast.makeText(this, getString(R.string.wa_direct_not_installed), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) finish()
        return super.onOptionsItemSelected(item)
    }
}
