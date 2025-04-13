package com.example.cs490_drivesense;

import android.content.Intent;
import android.os.Bundle;
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
            //finish();
        });

        // Initialize camera executor for background thread
        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }
}
