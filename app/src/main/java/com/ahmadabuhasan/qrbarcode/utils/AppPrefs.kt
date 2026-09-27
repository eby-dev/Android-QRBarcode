package com.ahmadabuhasan.qrbarcode.utils

import android.content.Context

object AppPrefs {

    private const val FILE = "settings"
    private const val KEY_OPEN_CAMERA_ON_LAUNCH = "open_camera_on_launch"

    // Off by default: the app opens on the dashboard. When on, launching the app
    // goes straight to the scanner, with the dashboard one Back press away.
    fun openCameraOnLaunch(context: Context): Boolean =
        prefs(context).getBoolean(KEY_OPEN_CAMERA_ON_LAUNCH, false)

    fun setOpenCameraOnLaunch(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_OPEN_CAMERA_ON_LAUNCH, enabled).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
