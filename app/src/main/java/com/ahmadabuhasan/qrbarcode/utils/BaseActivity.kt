package com.ahmadabuhasan.qrbarcode.utils

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

abstract class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyEdgeToEdge()
    }

    private fun applyEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        applyStatusBarIconContrast()

        val content = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())

            view.setPadding(
                bars.left,
                bars.top,
                bars.right,
                maxOf(ime.bottom, bars.bottom)
            )

            insets
        }
        ViewCompat.requestApplyInsets(content)
    }

    /**
     * Edge-to-edge leaves the status bar strip showing the window background, which
     * is white under the light theme. The system icons default to white there too,
     * so they disappear — ask for dark icons instead. Night mode already draws a
     * dark background behind light icons, so it is left untouched.
     */
    private fun applyStatusBarIconContrast() {
        val night = resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = !night
    }
}
