package com.example.cs490_drivesense;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CalibrationActivity extends AppCompatActivity {
    private FacialAttributeDetectorTFLite facialAttributeDetector; // Preload model
    private PreviewView previewView;
    private ExecutorService cameraExecutor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calibration);

        Button startButton = findViewById(R.id.button);

//        //Preload Facial Attribute Detection Model in a background thread
//        new Thread(() -> {
//            facialAttributeDetector = new FacialAttributeDetectorTFLite(getAssets());
//            runOnUiThread(() -> {
//                Toast.makeText(CalibrationActivity.this, "TFLite model preloaded successfully.", Toast.LENGTH_SHORT).show();
//            });
//        }).start();

        startButton.setOnClickListener(view -> {
            // Move to another activity for camera activation
            Intent intent = new Intent(CalibrationActivity.this, ActiveCalibrationActivity.class);
            startActivity(intent);
            finish();
        });

        // Initialize camera executor for background thread
        cameraExecutor = Executors.newSingleThreadExecutor();
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }
}
