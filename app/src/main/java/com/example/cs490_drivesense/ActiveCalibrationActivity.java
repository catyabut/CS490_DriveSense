package com.example.cs490_drivesense;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.YuvImage;
import android.media.Image;
import android.os.Bundle;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ActiveCalibrationActivity extends AppCompatActivity {
    private static final int TARGET_FPS = 15;
    private static final long FRAME_INTERVAL_MS = 1000 / TARGET_FPS; //66ms interval for 15fps use
    private long lastProcessedTime = 0; //Timestamp of the last frame processed

    private static final int INPUT_SIZE = 128;

    //Camera private variables
    private LinearLayout resultsLayout; //Declare resultsLayout
    private PreviewView previewView;
    private ExecutorService cameraExecutor;
    private static final int CAMERA_PERMISSION_CODE = 100;

    //FADM private variable
    private FacialAttributeDetectorTFLite facialAttributeDetector; // TFLite Model

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_active_calibration);

        previewView = findViewById(R.id.previewView);
        resultsLayout = findViewById(R.id.resultsLayout);

        //Retrieve preloaded model from the Application class
        facialAttributeDetector = ((DriveSenseApplication) getApplication()).getFacialAttributeModel();

        if (facialAttributeDetector == null) {
            Log.e("ActiveCalibration", "TFLite model was NOT preloaded!");
        }

        // Check and request camera permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
        }

        cameraExecutor = Executors.newSingleThreadExecutor();
    }


    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_FRONT) // Use front camera for driver tracking
                        .build();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                // Image analysis for real-time inference
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setTargetResolution(new android.util.Size(INPUT_SIZE, INPUT_SIZE)) // Match model input size
                        .build();

                // Set the analyzer for real-time frame processing
                imageAnalysis.setAnalyzer(cameraExecutor, image -> {
                    long currentTime = System.currentTimeMillis();
                    // Only process frames if 66ms (1/15 fps) have passed since the last frame
                    if (currentTime - lastProcessedTime >= FRAME_INTERVAL_MS) {
                        // Convert the camera frame to Bitmap
                        Bitmap bitmap = imageToBitmap(image);
                        if (bitmap != null) {
                            // Convert the resized Bitmap to float array for the model
                            float[] floatArray = bitmapToFloatArray(bitmap);

                            // Run inference on the float array using the model
                            float[] results = facialAttributeDetector.detectFacialAttributes(floatArray);
                            Log.d("TFLite", "Facial Attributes: " + arrayToString(results));

                            // Update the last processed time
                            lastProcessedTime = currentTime;

                            // Update the UI with the results
                            runOnUiThread(() -> updateAttributesUI(results));
                        }
                    }
                    image.close();
                });

                // Camera binding with logging
                Camera camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
                Log.d("CameraX", "Camera bound successfully");

            } catch (Exception e) {
                Log.e("CameraX", "Use case binding failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    // Update the UI with the detected attributes
    private void updateAttributesUI(float[] results) {
        // Assuming results array contains the attributes: Eye Openness, Liveness, Glasses, Mask, etc.
        boolean eyeOpennessLeft = results[0] > 0.5;  // Example threshold logic
        boolean eyeOpennessRight = results[1] > 0.5;
        boolean liveness = results[2] > 0.5;
        boolean glasses = results[3] > 0.5;
        boolean mask = results[4] > 0.5;

        // Check if the results are being passed correctly
        Log.d("FacialAttributes", "Eye Openness Left: " + eyeOpennessLeft);
        Log.d("FacialAttributes", "Eye Openness Right: " + eyeOpennessRight);
        Log.d("FacialAttributes", "Liveness: " + liveness);
        Log.d("FacialAttributes", "Glasses: " + glasses);
        Log.d("FacialAttributes", "Mask: " + mask);

        TextView eyeOpennessText = findViewById(R.id.eyeOpennessText);
        TextView livenessText = findViewById(R.id.livenessText);
        TextView glassesText = findViewById(R.id.glassesText);
        TextView maskText = findViewById(R.id.maskText);

        eyeOpennessText.setText("Eye Openness: Left: " + (eyeOpennessLeft ? "True" : "False") + ", Right: " + (eyeOpennessRight ? "True" : "False"));
        livenessText.setText("Liveness: " + (liveness ? "True" : "False"));
        glassesText.setText("Glasses: " + (glasses ? "True" : "False"));
        maskText.setText("Mask: " + (mask ? "True" : "False"));
    }

    private float[] bitmapToFloatArray(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] intArray = new int[width * height];
        bitmap.getPixels(intArray, 0, width, 0, 0, width, height);

        float[] floatArray = new float[width * height * 3];  // 3 channels for RGB

        // Normalize each pixel and fill the float array
        for (int i = 0; i < intArray.length; i++) {
            int pixel = intArray[i];
            floatArray[i * 3] = ((pixel >> 16) & 0xFF) / 255.0f;  // Red channel
            floatArray[i * 3 + 1] = ((pixel >> 8) & 0xFF) / 255.0f;   // Green channel
            floatArray[i * 3 + 2] = (pixel & 0xFF) / 255.0f;  // Blue channel
        }

        return floatArray;
    }

    // Convert CameraX ImageProxy to Bitmap
    @OptIn(markerClass = ExperimentalGetImage.class)
    private Bitmap imageToBitmap(ImageProxy imageProxy) {
        Image image = imageProxy.getImage();
        if (image == null) return null;

        try {
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);

            // Convert YUV to RGB Bitmap
            YuvImage yuvImage = new YuvImage(bytes, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(new android.graphics.Rect(0, 0, image.getWidth(), image.getHeight()), 100, outputStream);
            byte[] jpegBytes = outputStream.toByteArray();

            // Decode the byte array into a Bitmap
            Bitmap bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.length);

            // Resize the Bitmap to match the input size expected by the model (e.g., 224x224)
            Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true);

            return resizedBitmap;

        } catch (Exception e) {
            Log.e("ActiveCalibration", "Error converting image to bitmap", e);
            return null;
        } finally {
            image.close(); // Ensure image is closed in case of error
        }
    }

    private String arrayToString(float[] array) {
        StringBuilder sb = new StringBuilder();
        for (float value : array) {
            sb.append(value).append(" ");
        }
        return sb.toString();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }

    // Handle camera permission request
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, "Camera permission is required!", Toast.LENGTH_SHORT).show();
                finish(); // Close the activity if permission is denied
            }
        }
    }
}
