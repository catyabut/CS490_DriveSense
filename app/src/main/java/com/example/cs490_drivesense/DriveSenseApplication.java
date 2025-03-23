package com.example.cs490_drivesense;

import android.app.Application;
import android.content.res.AssetManager;
import android.widget.Toast;

public class DriveSenseApplication extends Application {
    private FacialAttributeDetectorTFLite facialAttributeDetector;
    private MediaPipeFaceDetectionTFLite mediaPipeFaceDetector;

    @Override
    public void onCreate() {
        super.onCreate();

        //Preload the TFLite model when the app starts
        new Thread(() -> {
            try {
                AssetManager assetManager = getAssets();
                facialAttributeDetector = new FacialAttributeDetectorTFLite(assetManager);
                mediaPipeFaceDetector = new MediaPipeFaceDetectionTFLite(assetManager);
                //runOnUiThread(() -> Toast.makeText(this, "AI Models Loaded!", Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Error loading AI model!", Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    public FacialAttributeDetectorTFLite getFacialAttributeModel() {
        return facialAttributeDetector;
    }

    public MediaPipeFaceDetectionTFLite getMediaPipeFaceDetectionModel() {
        return mediaPipeFaceDetector;
    }

    private void runOnUiThread(Runnable action) {
        new android.os.Handler(getMainLooper()).post(action);
    }
}
