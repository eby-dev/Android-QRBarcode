package com.ahmadabuhasan.qrbarcode;

import static com.ahmadabuhasan.qrbarcode.utils.Utils.interstitialAd;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;

import com.ahmadabuhasan.qrbarcode.databinding.ActivityAboutBinding;
import com.ahmadabuhasan.qrbarcode.utils.BaseActivity;
import com.ahmadabuhasan.qrbarcode.utils.Utils;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.MobileAds;

import java.util.Objects;

public class AboutActivity extends BaseActivity {

    private ActivityAboutBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAboutBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        Objects.requireNonNull(getSupportActionBar()).setHomeButtonEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(getString(R.string.about));

        binding.version.setText(String.format("%s%s", getResources().getString(R.string.version), BuildConfig.VERSION_NAME));

        MobileAds.initialize(this, initializationStatus -> {

        });
        new Utils().loadAd(this);
        showInterstitial();
        AdRequest adRequest = new AdRequest.Builder().build();
        binding.adViewAbout.loadAd(adRequest);
    }

    private void showInterstitial() {
        // Show the ad if it's ready. Otherwise toast and restart the game.
        if (interstitialAd != null) {
            interstitialAd.show(this);
        } else {
            startGame();
        }
    }

    private void startGame() {
        // Request a new ad if one isn't already loaded, hide the button, and kick off the timer.
        if (interstitialAd == null) {
            new Utils().loadAd(this);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            finish();
        }
        return super.onOptionsItemSelected(item);
    }
}