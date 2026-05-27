package com.ahmadabuhasan.qrbarcode.ui.main

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Vibrator
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.ahmadabuhasan.qrbarcode.R
import com.ahmadabuhasan.qrbarcode.databinding.ActivityMainBinding
import com.ahmadabuhasan.qrbarcode.model.ScanAction
import com.ahmadabuhasan.qrbarcode.ui.about.AboutActivity
import com.ahmadabuhasan.qrbarcode.utils.BaseActivity
import com.google.android.gms.ads.AdRequest
import com.google.android.material.snackbar.Snackbar
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.zxing.Result
import me.dm7.barcodescanner.zxing.ZXingScannerView

class MainActivity : BaseActivity(), ZXingScannerView.ResultHandler {

    companion object {
        private const val PERMISSION_CODE = 100
        private const val FLEXIBLE_APP_UPDATE_REQ_CODE = 123
        private var pressedTime: Long = 0
    }

    // ViewModel — semua state & logic bisnis ada di sini
    private val viewModel: MainViewModel by viewModels()

    private lateinit var zXingScannerView: ZXingScannerView
    private lateinit var appUpdateManager: AppUpdateManager
    private lateinit var installStateUpdatedListener: InstallStateUpdatedListener

    private var binding: ActivityMainBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding!!.root)

        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), PERMISSION_CODE)
        }

        appUpdateManager = AppUpdateManagerFactory.create(this)
        checkUpdate()
        installStateUpdatedListener = InstallStateUpdatedListener { state ->
            when (state.installStatus()) {
                InstallStatus.DOWNLOADED -> popupSnackBarForCompleteUpdate()
                InstallStatus.INSTALLED -> removeInstallStateUpdateListener()
                else -> Toast.makeText(
                    applicationContext,
                    "InstallStateUpdatedListener: state: ${state.installStatus()}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        val adRequest = AdRequest.Builder().build()
        binding?.adView?.loadAd(adRequest)

        zXingScannerView = ZXingScannerView(this)
        binding?.contentFrame?.addView(zXingScannerView)

        setupFlashButtons()
        observeViewModel()
    }

    // Observe perubahan dari ViewModel dan update UI
    private fun observeViewModel() {
        viewModel.flashEnabled.observe(this) { enabled ->
            zXingScannerView.setFlash(enabled)
            binding?.flashOn?.visibility = if (enabled) View.GONE else View.VISIBLE
            binding?.flashOff?.visibility = if (enabled) View.VISIBLE else View.GONE
        }

        viewModel.scanAction.observe(this) { action ->
            action ?: return@observe  // null = sudah dikonsumsi, skip
            when (action) {
                is ScanAction.OpenUrl -> {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(action.url))
                    startActivity(Intent.createChooser(intent, "Open with"))
                }
                is ScanAction.ShareText -> {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, action.text)
                    }
                    startActivity(Intent.createChooser(intent, "Share"))
                }
            }
            viewModel.onScanActionConsumed()
        }
    }

    // Activity hanya tahu "user tap flash" → delegasi ke ViewModel
    private fun setupFlashButtons() {
        binding?.flashOn?.setOnClickListener { viewModel.toggleFlash() }
        binding?.flashOff?.setOnClickListener { viewModel.toggleFlash() }
    }

    override fun onResume() {
        super.onResume()
        zXingScannerView.setResultHandler(this)
        zXingScannerView.setAspectTolerance(0.2f)
        zXingScannerView.startCamera()
    }

    override fun onDestroy() {
        super.onDestroy()
        zXingScannerView.stopCamera()
        binding = null
    }

    // Activity terima hasil scan → kirim ke ViewModel untuk diproses
    override fun handleResult(rawResult: Result) {
        Toast.makeText(this, rawResult.toString(), Toast.LENGTH_LONG).show()
        viewModel.handleScanResult(rawResult.toString())

        @Suppress("DEPRECATION")
        (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).vibrate(300)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_CODE) {
            val message = if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                "Camera permission granted"
            } else {
                "Camera permission denied"
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.optionmenu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.about) {
            startActivity(Intent(this, AboutActivity::class.java))
        }
        return super.onOptionsItemSelected(item)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FLEXIBLE_APP_UPDATE_REQ_CODE) {
            when (resultCode) {
                RESULT_CANCELED -> Toast.makeText(applicationContext, "Update canceled by user! ", Toast.LENGTH_LONG).show()
                RESULT_OK -> Toast.makeText(applicationContext, "Update success! ", Toast.LENGTH_LONG).show()
                else -> {
                    Toast.makeText(applicationContext, "Update failed! ", Toast.LENGTH_LONG).show()
                    checkUpdate()
                }
            }
        }
    }

    private fun checkUpdate() {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
            ) {
                startUpdateFlow(appUpdateInfo)
            } else if (appUpdateInfo.installStatus() == InstallStatus.DOWNLOADED) {
                popupSnackBarForCompleteUpdate()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun startUpdateFlow(appUpdateInfo: AppUpdateInfo) {
        try {
            appUpdateManager.startUpdateFlowForResult(
                appUpdateInfo,
                AppUpdateType.FLEXIBLE,
                this,
                FLEXIBLE_APP_UPDATE_REQ_CODE
            )
        } catch (e: IntentSender.SendIntentException) {
            e.printStackTrace()
        }
    }

    private fun popupSnackBarForCompleteUpdate() {
        Snackbar.make(
            findViewById(R.id.layout_activity_main),
            "An update has just been downloaded.",
            Snackbar.LENGTH_INDEFINITE
        ).apply {
            setAction("RESTART") { appUpdateManager.completeUpdate() }
            setActionTextColor(ContextCompat.getColor(this@MainActivity, R.color.red))
            show()
        }
    }

    private fun removeInstallStateUpdateListener() {
        appUpdateManager.unregisterListener(installStateUpdatedListener)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (pressedTime + 2000 > System.currentTimeMillis()) {
            finishAndRemoveTask()
        } else {
            Toast.makeText(this, "Press once again to exit", Toast.LENGTH_SHORT).show()
        }
        pressedTime = System.currentTimeMillis()
    }
}
