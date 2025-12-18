package com.ahmadabuhasan.qrbarcode.utils

import android.os.Build
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            WindowCompat.setDecorFitsSystemWindows(window, false)

            val decorView = window.decorView
            val content = findViewById<View>(android.R.id.content)

            decorView.post {
                ViewCompat.setOnApplyWindowInsetsListener(decorView) { _, insets ->
                    val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                    val ime = insets.getInsets(WindowInsetsCompat.Type.ime())

                    content.setPadding(
                        systemBars.left,
                        systemBars.top,
                        systemBars.right,
                        maxOf(ime.bottom, systemBars.bottom)
                    )

                    insets
                }
            }
        }
    }

}