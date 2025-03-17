package com.example.cs490_drivesense;

import static com.example.cs490_drivesense.MediaPipeFaceDetectionTFLite.distBetweenPoints;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
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
    private static final int TARGET_FPS = 10;
    private static final long FRAME_INTERVAL_MS = 1000 / TARGET_FPS; //66ms interval for 15fps use
    private long lastProcessedTime = 0; //Timestamp of the last frame processed
    private static final int INPUT_SIZE = 256;
    private static final double MAX_DIST_ALLOWED = 0.20; // Used to make sure the points are close enough for calibration
    private int counter = 0; // Used to ensure the first 5 results are collected before starting calibration

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
        resultsLayout = findViewById(R.id.resultsLayout);

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
                            // Run inference on both models with the rotated, resized Bitmap
                            FacialAttributeData faceAttributeResults = facialAttributeDetector.detectFacialAttributes(bitmapFA);
                            MediaPipeFaceDetectionData faceDetectionResults = faceDetector.detectFace(bitmapMPFD);

                            // Make sure first 5 results are recorded first before calibrating
                            if (this.counter < 5)
                            {
                                counter += 1;
                            }
                            // Calibrate the system to generate the neutral position
                            else
                            {
                                boolean faceDetectedAllResults = this.faceDetected5Times(faceDetector.last5Results); // Check last 5 position for extreme movement
                                // Check if user moved
                                if (faceDetectedAllResults)
                                {
                                    boolean userStill = this.checkIfUserStill(faceDetector.last5Results);
                                    // Calibration successful set the neutral position
                                    if (userStill)
                                    {
                                        faceDetector.setNeutralPosition(faceDetectionResults);
                                    }
                                    // User moved too much do not set the neutral position
                                    else
                                    {
                                        Log.d("Calibration", "ERROR: User needs to stay still.");
                                    }
                                }
                                // Face was not detected enough times to check movement
                                else
                                {
                                    Log.d("Calibration", "ERROR: Face was not detected 5 times, last 5 records needs to have detected a face.");
                                }
                            }

                            // Debug logs to check if MediaPipe is detecting faces
                            Log.d("MediaPipe", "Face Detected: " + faceDetectionResults.faceDetected);
                            Log.d("MediaPipe", "Bounding Box: Width=" + faceDetectionResults.boxWidth + ", Height=" + faceDetectionResults.boxHeight);


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
        if (!faceDetectionResults.faceDetected) {
            Log.w("MediaPipe", "No face detected, skipping UI update.");
            return; // Skip updating UI if no face detected
        }

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

    private Bitmap rotateBitmap(Bitmap bitmap, int rotationDegrees) {
        if (rotationDegrees == 0) return bitmap; // No rotation needed

        Matrix matrix = new Matrix();
        matrix.postRotate(rotationDegrees);
        //checking the applied rotations to image bitmap
        Log.d("BitmapRotation", "Applying rotation: " + rotationDegrees);

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
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

            // Get rotation from ImageProxy metadata
            int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();

            // Rotate the Bitmap before resizing
            Bitmap rotatedBitmap = rotateBitmap(bitmap, rotationDegrees);

            // Resize the Bitmap to match the input size expected by the model (e.g., 128x128)
            Bitmap resizedBitmap = Bitmap.createScaledBitmap(rotatedBitmap, width, height, true);

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

    // Check to make sure users face was detected last 5 times returns boolean
    public boolean faceDetected5Times(MediaPipeFaceDetectionData[] history)
    {
        for (MediaPipeFaceDetectionData record : history)
        {
            if (!record.faceDetected)
            {
                return false;
            }
        }
        return true;
    }
    // Function that checks the last 5 results from mediapipe face detection model to ensure that the user doesn't move too much returns a bool
    public boolean checkIfUserStill(MediaPipeFaceDetectionData[] history)
    {
        // For each record compare the distance between the points of the others records
        for (int i = 0; i < history.length; i++)
        {
            MediaPipeFaceDetectionData currRecord = history[i];
            for (int j = 0; j < history.length; j++)
            {
                // Don't compare a point with itself
                if (j != i)
                {
                    // Check eyes
                    double dist = distBetweenPoints(currRecord.rightEyeX, currRecord.rightEyeY, history[i].rightEyeX, history[i].rightEyeY);
                    if (dist > MAX_DIST_ALLOWED)
                    {
                        return false;
                    }
                    dist = distBetweenPoints(currRecord.leftEyeX, currRecord.leftEyeY, history[i].leftEyeX, history[i].leftEyeY);
                    if (dist > MAX_DIST_ALLOWED)
                    {
                        return false;
                    }
                    // Check nose
                    dist = distBetweenPoints(currRecord.noseTipX, currRecord.noseTipY, history[i].noseTipX, history[i].noseTipY);
                    if (dist > MAX_DIST_ALLOWED)
                    {
                        return false;
                    }
                    // Check mouth
                    dist = distBetweenPoints(currRecord.mouthCenterX, currRecord.mouthCenterY, history[i].mouthCenterX, history[i].mouthCenterY);
                    if (dist > MAX_DIST_ALLOWED)
                    {
                        return false;
                    }
                    // Check ears
                    dist = distBetweenPoints(currRecord.rightEarTragionX, currRecord.rightEarTragionY, history[i].rightEarTragionX, history[i].rightEarTragionY);
                    if (dist > MAX_DIST_ALLOWED)
                    {
                        return false;
                    }
                    dist = distBetweenPoints(currRecord.leftEarTragionX, currRecord.leftEarTragionY, history[i].leftEarTragionX, history[i].leftEarTragionY);
                    if (dist > MAX_DIST_ALLOWED)
                    {
                        return false;
                    }
                }
            }
        }
        // All points close enough to each other, driver has not made any major movements
        return true;
    }



}