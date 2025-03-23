package com.example.cs490_drivesense;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
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