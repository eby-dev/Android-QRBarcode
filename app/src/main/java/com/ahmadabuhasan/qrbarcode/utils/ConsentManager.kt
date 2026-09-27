package com.ahmadabuhasan.qrbarcode.utils

import android.app.Activity
import android.util.Log
import com.ahmadabuhasan.qrbarcode.BuildConfig
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import java.util.concurrent.atomic.AtomicBoolean

/**
 * GDPR consent via Google's User Messaging Platform.
 *
 * EU/UK users must consent before their data is used for personalised ads;
 * Google passes that obligation to publishers through the EU User Consent
 * Policy. Outside those regions the SDK reports that no form is required and
 * the user sees nothing.
 *
 * Ads are never requested until consent has been resolved. When resolution
 * fails (no network, SDK error) we fall back to whatever consent state is
 * cached rather than leaving the app permanently ad-free — this mirrors
 * Google's own reference flow.
 */
object ConsentManager {

    private val TAG = ConsentManager::class.java.simpleName

    // Guards against a duplicate init across config changes or a second activity.
    private val adsInitialised = AtomicBoolean(false)

    private var consentInformation: ConsentInformation? = null

    /** True once the SDK has enough consent to request ads. */
    val canRequestAds: Boolean
        get() = consentInformation?.canRequestAds() ?: false

    /**
     * Resolves consent, then initialises the Ads SDK.
     *
     * @param onReady invoked on the main thread once ads may be requested.
     *   Callers must leave their ad container empty until it fires. It can fire
     *   more than once — cached consent first, then the background refresh — so
     *   the callback must be idempotent.
     *
     * Note that refusing the form does not suppress this callback. Under the
     * TCF framework "Do not consent" refuses *personalisation*, not ads, so
     * `canRequestAds()` stays true and non-personalised ads still serve.
     */
    fun gatherConsent(activity: Activity, onReady: () -> Unit) {
        val info = UserMessagingPlatform.getConsentInformation(activity)
        consentInformation = info

        // A previous session may already have resolved consent — start ads now
        // while the SDK refreshes its state in the background.
        if (info.canRequestAds()) initialiseAds(activity, onReady)

        info.requestConsentInfoUpdate(
            activity,
            buildRequestParameters(activity),
            {
                // Shows the form only where one is required; elsewhere this
                // invokes the callback immediately.
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) {
                        Log.w(TAG, "Consent form error ${formError.errorCode}: ${formError.message}")
                    }
                    if (info.canRequestAds()) initialiseAds(activity, onReady)
                }
            },
            { requestError ->
                Log.w(TAG, "Consent info update failed ${requestError.errorCode}: ${requestError.message}")
                if (info.canRequestAds()) initialiseAds(activity, onReady)
            }
        )
    }

    private fun initialiseAds(activity: Activity, onReady: () -> Unit) {
        if (adsInitialised.compareAndSet(false, true)) {
            MobileAds.initialize(activity) { Log.i(TAG, "MobileAds initialised") }
        }
        // The caller needs its banner either way — a second activity arriving
        // after init still has an empty container to fill.
        onReady()
    }

    /**
     * Debug builds can force EEA geography to exercise the consent form.
     * Set `admob_test_device_id` in local.properties to the hashed id the UMP
     * SDK prints to logcat on first run; without it this is a no-op.
     */
    private fun buildRequestParameters(activity: Activity): ConsentRequestParameters {
        val builder = ConsentRequestParameters.Builder()
        if (BuildConfig.DEBUG && BuildConfig.CONSENT_TEST_DEVICE_ID.isNotEmpty()) {
            builder.setConsentDebugSettings(
                ConsentDebugSettings.Builder(activity)
                    .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                    .addTestDeviceHashedId(BuildConfig.CONSENT_TEST_DEVICE_ID)
                    .build()
            )
        }
        return builder.build()
    }
}
