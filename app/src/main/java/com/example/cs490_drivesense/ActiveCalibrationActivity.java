package com.example.cs490_drivesense;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.RectF;
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

    private static final int INPUT_SIZE = 256;

    //Camera private variables
    private LinearLayout resultsLayout; //Declare resultsLayout
    private PreviewView previewView;
    private ExecutorService cameraExecutor;
    private static final int CAMERA_PERMISSION_CODE = 100;

    //FADM private variable
    private FacialAttributeDetectorTFLite facialAttributeDetector; // TFLite Model

    //MPFD private variable
    private MediaPipeFaceDetectionTFLite faceDetector; // TFLite Model

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_active_calibration);

        previewView = findViewById(R.id.previewView);
        // Results layout not found
        //resultsLayout = findViewById(R.id.resultsLayout);

        //Retrieve preloaded model from the Application class
        facialAttributeDetector = ((DriveSenseApplication) getApplication()).getFacialAttributeModel();
        faceDetector = ((DriveSenseApplication) getApplication()).getMediaPipeFaceDetectionModel();

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
                        .setTargetResolution(new android.util.Size(INPUT_SIZE, INPUT_SIZE)) // Match model input size 128 X 128
                        .build();

                // Set the analyzer for real-time frame processing
                imageAnalysis.setAnalyzer(cameraExecutor, image -> {
                    long currentTime = System.currentTimeMillis();
                    // Only process frames if 66ms (1/15 fps) have passed since the last frame
                    if (currentTime - lastProcessedTime >= FRAME_INTERVAL_MS) {
                        // Convert the camera frame to resized Bitmaps for each model
                        Bitmap bitmapFA = imageToResizedBitmap(image, 128, 128);
                        Bitmap bitmapMPFD = resizeAndPadMaintainAspectRatio(bitmapFA, 256, 256, 0);

                        if (bitmapFA != null && bitmapMPFD != null) {
                            // Run inference on both models with given bitmap, return results
                            FacialAttributeData faceAttributeResults = facialAttributeDetector.detectFacialAttributes(bitmapFA);
                            MediaPipeFaceDetectionData faceDetectionResults = faceDetector.detectFace(bitmapMPFD);
                            // Update the last processed time
                            lastProcessedTime = currentTime;

                            // Update the UI with the results
                            runOnUiThread(() -> updateAttributesUI(faceAttributeResults, faceDetectionResults));
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
    private void updateAttributesUI(FacialAttributeData attributeResults, MediaPipeFaceDetectionData faceDetectionResults) {
        // Assuming results contains the attributes booleans: Eye Openness, Liveness, Glasses, Mask, etc.

        // Check if the results are being passed correctly
        Log.d("FacialAttributes", "Eye Openness Left: " + !attributeResults.eyeClosenessL);
        Log.d("FacialAttributes", "Eye Openness Right: " + !attributeResults.eyeClosenessR);
        Log.d("FacialAttributes", "Liveness: " + attributeResults.liveness);
        Log.d("FacialAttributes", "Glasses: " + attributeResults.glasses);
        Log.d("FacialAttributes", "Sunglasses: " + attributeResults.sunglasses);
        Log.d("FacialAttributes", "Mask: " + attributeResults.mask);
        // Check if results are displaying properly
        Log.d("FaceDetectionResults", "Box Center (X, Y): " + " (" + faceDetectionResults.boxCenterX + ", " + faceDetectionResults.boxCenterY);
        Log.d("FaceDetectionResults", "Box Width: " + faceDetectionResults.boxWidth);
        Log.d("FaceDetectionResults", "Box Height: " + faceDetectionResults.boxHeight);
        Log.d("FaceDetectionResults", "Right Eye (X, Y): " + " (" + faceDetectionResults.rightEyeX + ", " + faceDetectionResults.rightEyeY);
        Log.d("FaceDetectionResults", "Left Eye (X, Y): " + " (" + faceDetectionResults.leftEyeX + ", " + faceDetectionResults.leftEyeY);
        Log.d("FaceDetectionResults", "Nose Tip (X, Y): " + " (" + faceDetectionResults.noseTipX + ", " + faceDetectionResults.noseTipY);
        Log.d("FaceDetectionResults", "Mouth Center (X, Y): " + " (" + faceDetectionResults.mouthCenterX + ", " + faceDetectionResults.mouthCenterY);
        Log.d("FaceDetectionResults", "Right Ear (X, Y): " + " (" + faceDetectionResults.rightEarTragionX + ", " + faceDetectionResults.rightEarTragionY);
        Log.d("FaceDetectionResults", "Left Ear (X, Y): " + " (" + faceDetectionResults.leftEarTragionX + ", " + faceDetectionResults.leftEarTragionY);
        Log.d("FaceDetectionResults", "Face Detected: " + faceDetectionResults.faceDetected);

        // Display results for attributes
        TextView eyeOpennessText = findViewById(R.id.eyeOpennessText);
        TextView livenessText = findViewById(R.id.livenessText);
        TextView glassesText = findViewById(R.id.glassesText);
        TextView maskText = findViewById(R.id.maskText);
        TextView sunglassesText = findViewById(R.id.sunglassesText);

        eyeOpennessText.setText("Eye Openness: Left: " + (!attributeResults.eyeClosenessL ? "True" : "False") + ", Right: " + (!attributeResults.eyeClosenessR ? "True" : "False"));
        livenessText.setText("Liveness: " + (attributeResults.liveness ? "True" : "False"));
        glassesText.setText("Glasses: " + (attributeResults.glasses ? "True" : "False"));
        maskText.setText("Mask: " + (attributeResults.mask ? "True" : "False"));
        sunglassesText.setText("Sunglasses: " + (attributeResults.sunglasses ? "True" : "False"));

        // Display face detection results and key points
        TextView faceDetectedText = findViewById(R.id.faceDetectedText);
        TextView boxCenterText = findViewById(R.id.boxCenterText);
        TextView boxWHText = findViewById(R.id.boxWHText);
        TextView rightEyeText = findViewById(R.id.rightEyeText);
        TextView leftEyeText = findViewById(R.id.leftEyeText);
        TextView noseTipText = findViewById(R.id.noseTipText);
        TextView mouthCenterText = findViewById(R.id.mouthCenterText);
        TextView rightEarText = findViewById(R.id.rightEarText);
        TextView leftEarText = findViewById(R.id.leftEarText);

        faceDetectedText.setText("Face Detected: " + (faceDetectionResults.faceDetected ? "True" : "False"));
        boxCenterText.setText("Box Center (X, Y): " + " (" + faceDetectionResults.boxCenterX + ", " + faceDetectionResults.boxCenterY);
        boxWHText.setText("Box Width X Height: " + " (" + faceDetectionResults.boxWidth + ", " + faceDetectionResults.boxHeight);
        rightEyeText.setText("Right Eye (X, Y): " + " (" + faceDetectionResults.rightEyeX + ", " + faceDetectionResults.rightEyeY);
        leftEyeText.setText("Left Eye (X, Y): " + " (" + faceDetectionResults.leftEyeX + ", " + faceDetectionResults.leftEyeY);
        noseTipText.setText("Nose Tip (X, Y): " + " (" + faceDetectionResults.noseTipX + ", " + faceDetectionResults.noseTipY);
        mouthCenterText.setText("Mouth Center (X, Y): " + " (" + faceDetectionResults.mouthCenterX + ", " + faceDetectionResults.mouthCenterY);
        rightEarText.setText("Right Ear (X, Y): " + " (" + faceDetectionResults.rightEarTragionX + ", " + faceDetectionResults.rightEarTragionY);
        leftEarText.setText("Left Ear (X, Y): " + " (" + faceDetectionResults.leftEarTragionX + ", " + faceDetectionResults.leftEarTragionY);
    }

    // Convert CameraX ImageProxy to resized Bitmap of given width and height
    @OptIn(markerClass = ExperimentalGetImage.class)
    private Bitmap imageToResizedBitmap(ImageProxy imageProxy, int width, int height) {
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

            // Resize the Bitmap to match the input size expected by the model (e.g., 128x128)
            Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true);

            return resizedBitmap;

        } catch (Exception e) {
            Log.e("ActiveCalibration", "Error converting image to bitmap", e);
            return null;
        } finally {
            image.close(); // Ensure image is closed in case of error
        }
    }

    // Resize bitmap from another bitmap
    public static Bitmap resizeAndPadMaintainAspectRatio(Bitmap image, int outputBitmapWidth, int outputBitmapHeight, int paddingValue) {
        int width = image.getWidth();
        int height = image.getHeight();
        float ratioBitmap = (float) width / (float) height;
        float ratioMax = (float) outputBitmapWidth / (float) outputBitmapHeight;

        int finalWidth = outputBitmapWidth;
        int finalHeight = outputBitmapHeight;
        if (ratioMax > ratioBitmap) {
            finalWidth = (int) ((float)outputBitmapHeight * ratioBitmap);
        } else {
            finalHeight = (int) ((float)outputBitmapWidth / ratioBitmap);
        }

        Bitmap outputImage = Bitmap.createBitmap(outputBitmapWidth, outputBitmapHeight, Bitmap.Config.ARGB_8888);
        Canvas can = new Canvas(outputImage);
        can.drawARGB(0xFF, paddingValue, paddingValue, paddingValue);
        int left = (outputBitmapWidth - finalWidth) / 2;
        int top = (outputBitmapHeight - finalHeight) / 2;
        can.drawBitmap(image, null, new RectF(left, top, finalWidth + left, finalHeight + top), null);
        return outputImage;
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