package com.ahmadabuhasan.qrbarcode

import android.app.Application

// MobileAds is deliberately NOT initialised here. Under GDPR the Ads SDK must
// not start until consent has been resolved, so ConsentManager owns that call
// and runs it from the first activity (an Activity context is required to show
// the consent form).
class MyApp : Application()
