package com.example.cs490_drivesense;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class LoadingActivity extends AppCompatActivity {

    private ProgressBar progressBar;
    private TextView tvProgress;
    private int progressStatus = 0;
    private Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_loading);

        // Initialize Views
        progressBar = findViewById(R.id.progressBar2);
        tvProgress = findViewById(R.id.tvProgress);

        // Start progress update
        simulateProgress();
    }

    /*
    To hide the notifications and bottom bar
     */
    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
    }

    private void hideSystemUI() {
        // For Android 11+ (API 30+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            getWindow().getInsetsController().hide(
                    android.view.WindowInsets.Type.statusBars() |
                            android.view.WindowInsets.Type.navigationBars()
            );
            getWindow().getInsetsController().setSystemBarsBehavior(
                    android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            );
        } else {
            // For Android 10 and lower
            View decorView = getWindow().getDecorView();
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            );
        }
    }

    private void simulateProgress() {
        new Thread(() -> {
            while (progressStatus < 100) {
                progressStatus += 5; // Increment progress
                handler.post(() -> {
                    progressBar.setProgress(progressStatus);
                    tvProgress.setText(progressStatus + "%");
                });
                try {
                    Thread.sleep(150); // Smoothly update progress
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            // After loading is complete, switch activity
            runOnUiThread(() -> {
                Intent intent = new Intent(LoadingActivity.this, CalibrationActivity.class);
                startActivity(intent);
                finish();
            });

        }).start();
    }
}