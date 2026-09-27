package com.ahmadabuhasan.qrbarcode.ui.home

import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.format.DateUtils
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.ahmadabuhasan.qrbarcode.R
import com.ahmadabuhasan.qrbarcode.data.ScanHistoryEntity
import com.ahmadabuhasan.qrbarcode.databinding.ActivityHomeBinding
import com.ahmadabuhasan.qrbarcode.databinding.ItemHomeActionBinding
import com.ahmadabuhasan.qrbarcode.databinding.ItemRecentScanBinding
import com.ahmadabuhasan.qrbarcode.model.ScanContent
import com.ahmadabuhasan.qrbarcode.model.ScanContentParser
import com.ahmadabuhasan.qrbarcode.ui.about.AboutActivity
import com.ahmadabuhasan.qrbarcode.ui.history.HistoryActivity
import com.ahmadabuhasan.qrbarcode.ui.main.MainActivity
import com.ahmadabuhasan.qrbarcode.ui.main.observeScanResults
import com.ahmadabuhasan.qrbarcode.ui.main.showScanResultSheet
import com.ahmadabuhasan.qrbarcode.ui.main.typeLabelRes
import com.ahmadabuhasan.qrbarcode.ui.qrgenerator.QrGeneratorActivity
import com.ahmadabuhasan.qrbarcode.ui.wadirect.WaDirectActivity
import com.ahmadabuhasan.qrbarcode.utils.AppConfig
import com.ahmadabuhasan.qrbarcode.utils.AppPrefs
import com.ahmadabuhasan.qrbarcode.utils.BaseActivity
import com.ahmadabuhasan.qrbarcode.utils.ConsentManager
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.material.snackbar.Snackbar
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability

// Launcher screen: a Scan button, shortcuts to the other features, and the last
// few scans. Also owns the app-wide startup work (consent, in-app update) and
// the double-Back exit, since it is the root of the task.
class HomeActivity : BaseActivity() {

    companion object {
        private const val FLEXIBLE_APP_UPDATE_REQ_CODE = 123
        private var pressedTime: Long = 0
    }

    private val viewModel: HomeViewModel by viewModels()
    private lateinit var binding: ActivityHomeBinding

    private lateinit var appUpdateManager: AppUpdateManager
    private lateinit var installStateUpdatedListener: InstallStateUpdatedListener

    // True while the scanner was opened straight from launch. The consent form
    // and the update prompt need this screen visible, so they wait until the user
    // comes back to it instead of popping up behind the camera.
    private var openedCameraOnLaunch = false
    private var startupChecksDone = false

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { viewModel.decodeImageFromUri(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null && AppPrefs.openCameraOnLaunch(this)) {
            openedCameraOnLaunch = true
            openScanner(animate = false)
        }

        appUpdateManager = AppUpdateManagerFactory.create(this)
        installStateUpdatedListener = InstallStateUpdatedListener { state ->
            when (state.installStatus()) {
                InstallStatus.DOWNLOADED -> popupSnackBarForCompleteUpdate()
                InstallStatus.INSTALLED -> removeInstallStateUpdateListener()
                else -> Unit
            }
        }
        // Without registering, the "update downloaded → RESTART" snackbar only
        // appeared on the next launch.
        appUpdateManager.registerListener(installStateUpdatedListener)

        setupActions()
        observeRecentScans()
        observeScanResults(viewModel)
    }

    override fun onRestart() {
        super.onRestart()
        openedCameraOnLaunch = false
    }

    override fun onResume() {
        super.onResume()
        if (!openedCameraOnLaunch && !startupChecksDone) {
            startupChecksDone = true
            // GDPR: the banner is only built once consent has been resolved.
            ConsentManager.gatherConsent(this) { showBanner() }
            checkUpdate()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        removeInstallStateUpdateListener()
    }

    private fun setupActions() {
        binding.cardScan.setOnClickListener { openScanner(animate = true) }

        bindAction(binding.actionGallery, R.drawable.ic_image, R.string.scan_from_gallery) {
            pickImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        bindAction(binding.actionHistory, R.drawable.ic_history, R.string.history) {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        bindAction(binding.actionWaDirect, R.drawable.ic_chat, R.string.wa_direct) {
            startActivity(Intent(this, WaDirectActivity::class.java))
        }
        bindAction(binding.actionQrGenerator, R.drawable.ic_qr_code, R.string.qr_generator) {
            startActivity(Intent(this, QrGeneratorActivity::class.java))
        }

        binding.btnSeeAll.setOnClickListener { startActivity(Intent(this, HistoryActivity::class.java)) }
    }

    private fun bindAction(action: ItemHomeActionBinding, @DrawableRes icon: Int, @StringRes label: Int, onClick: () -> Unit) {
        action.icon.setImageResource(icon)
        action.label.setText(label)
        action.root.setOnClickListener { onClick() }
    }

    private fun openScanner(animate: Boolean) {
        startActivity(Intent(this, MainActivity::class.java))
        if (!animate) {
            // Launching straight into the camera should feel like the app opened
            // there, not like a screen sliding over the dashboard.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            } else {
                @Suppress("DEPRECATION")
                overridePendingTransition(0, 0)
            }
        }
    }

    private fun observeRecentScans() {
        viewModel.recentScans.observe(this) { items ->
            binding.recentSection.isVisible = items.isNotEmpty()
            binding.recentList.removeAllViews()
            items.forEach { item ->
                val row = ItemRecentScanBinding.inflate(layoutInflater, binding.recentList, false)
                val content = ScanContentParser.parse(item.content)
                row.textContent.text = recentTitle(content)
                row.textMeta.text = getString(
                    R.string.history_meta_format,
                    getString(content.typeLabelRes()),
                    DateUtils.getRelativeTimeSpanString(item.scannedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
                )
                // Re-open the result sheet without re-scanning (and without bumping history).
                row.root.setOnClickListener { showScanResultSheet(item.content, item.format) }
                binding.recentList.addView(row.root)
            }
        }
    }

    // Raw Wi-Fi / vCard / vEvent payloads are unreadable on one line, so show
    // the field a person would recognise.
    private fun recentTitle(content: ScanContent): String = when (content) {
        is ScanContent.Wifi -> content.ssid
        is ScanContent.VCard -> content.name ?: content.raw
        is ScanContent.CalendarEvent -> content.title ?: content.raw
        is ScanContent.Phone -> content.number
        is ScanContent.Email -> content.to
        else -> content.raw
    }

    // Dipanggil ConsentManager setelah consent selesai. Guard isEmpty mencegah
    // banner dobel kalau callback jalan dua kali (cache + refresh).
    private fun showBanner() {
        val container = binding.adViewHomeContainer
        if (container.childCount > 0) return
        val adView = AdView(this).apply {
            adUnitId = AppConfig.bannerAdId()
            setAdSize(AdSize.BANNER)
        }
        container.addView(adView)
        adView.loadAd(AdRequest.Builder().build())
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_home, menu)
        menu.findItem(R.id.open_camera_on_launch).isChecked = AppPrefs.openCameraOnLaunch(this)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.open_camera_on_launch -> {
                val enabled = !item.isChecked
                AppPrefs.setOpenCameraOnLaunch(this, enabled)
                item.isChecked = enabled
                true
            }
            R.id.about -> {
                startActivity(Intent(this, AboutActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // --- In-app update ---

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
            binding.root,
            "An update has just been downloaded.",
            Snackbar.LENGTH_INDEFINITE
        ).apply {
            setAction("RESTART") { appUpdateManager.completeUpdate() }
            setActionTextColor(ContextCompat.getColor(this@HomeActivity, R.color.red))
            show()
        }
    }

    private fun removeInstallStateUpdateListener() {
        appUpdateManager.unregisterListener(installStateUpdatedListener)
    }

    @Deprecated("Deprecated in Java")
    @Suppress("MissingSuperCall")
    override fun onBackPressed() {
        if (pressedTime + 2000 > System.currentTimeMillis()) {
            finishAndRemoveTask()
        } else {
            Toast.makeText(this, "Press once again to exit", Toast.LENGTH_SHORT).show()
        }
        pressedTime = System.currentTimeMillis()
    }
}
