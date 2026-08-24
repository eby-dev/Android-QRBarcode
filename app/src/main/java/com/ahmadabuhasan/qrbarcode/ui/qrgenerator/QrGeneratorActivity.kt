package com.ahmadabuhasan.qrbarcode.ui.qrgenerator

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.content.FileProvider
import com.ahmadabuhasan.qrbarcode.R
import com.ahmadabuhasan.qrbarcode.databinding.ActivityQrGeneratorBinding
import com.ahmadabuhasan.qrbarcode.utils.BaseActivity
import com.ahmadabuhasan.qrbarcode.utils.AppConfig
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import java.io.File
import java.io.FileOutputStream

class QrGeneratorActivity : BaseActivity() {

    private val viewModel: QrGeneratorViewModel by viewModels()
    private lateinit var binding: ActivityQrGeneratorBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQrGeneratorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.apply {
            setHomeButtonEnabled(true)
            setDisplayHomeAsUpEnabled(true)
            setTitle(R.string.qr_generator)
        }

        val adView = AdView(this).apply {
            adUnitId = AppConfig.bannerAdId()
            setAdSize(AdSize.BANNER)
        }
        binding.adViewQrGeneratorContainer.addView(adView)
        adView.loadAd(AdRequest.Builder().build())

        binding.btnGenerate.setOnClickListener {
            viewModel.generate(binding.editContent.text?.toString() ?: "")
        }

        binding.btnShare.setOnClickListener {
            val bitmap = viewModel.qrBitmap.value ?: return@setOnClickListener
            shareQrImage(bitmap)
        }

        observeViewModel()
    }

    private fun observeViewModel() {
        viewModel.qrBitmap.observe(this) { bitmap ->
            bitmap ?: return@observe
            binding.imgQr.setImageBitmap(bitmap)
            binding.imgQr.visibility = View.VISIBLE
            binding.btnShare.visibility = View.VISIBLE
        }

        viewModel.error.observe(this) { error ->
            error ?: return@observe
            Toast.makeText(this, getString(R.string.qr_generator_empty), Toast.LENGTH_SHORT).show()
            viewModel.onErrorShown()
        }
    }

    private fun shareQrImage(bitmap: Bitmap) {
        val qrDir = File(cacheDir, "qr_codes").also { it.mkdirs() }
        val file = File(qrDir, "qr_code.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri: Uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.qr_generator_share)))
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) finish()
        return super.onOptionsItemSelected(item)
    }
}
