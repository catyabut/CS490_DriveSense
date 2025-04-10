package com.example.cs490_drivesense;

import static android.graphics.ColorSpace.Named.SRGB;
import static com.example.cs490_drivesense.MediaPipeFaceDetectionTFLite.distBetweenPoints;

import static org.opencv.core.CvType.CV_8UC1;
import static org.opencv.imgproc.Imgproc.cvtColor;
import android.content.Intent;
import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorSpace;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.YuvImage;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.camera2.interop.Camera2Interop.Extender;
import androidx.camera.camera2.interop.ExperimentalCamera2Interop;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.CvException;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ActiveCalibrationActivity extends AppCompatActivity {
    //*****************Main Detection Layout**********************
    private LinearLayout messageLayout;
    private ImageButton cameraToggleButton;
    private ImageButton exportButton;
    private TextView deviationWarningText;
    private boolean isCameraOn = false;
    private boolean isCalibrationComplete = false;
    private boolean isPostCalibLayoutRdy = false;
    private Preview preview; //camera work for after calibration
    //************************************************************
    private static final int TARGET_FPS = 15;
    private static final long FRAME_INTERVAL_MS = 1000 / TARGET_FPS; //66ms interval for 15fps use
    private long lastProcessedTime = 0; //Timestamp of the last frame processed
    private static final int INPUT_SIZE = 256;
    // Used to make sure the points are close enough for calibration

    //***************Calibration Private Variables***************
    private int counter = 0; // Used to ensure the first 5 results are collected before starting calibration
    private static final int CALIBRATION_FRAME_COUNT = 20; //To change the max frames needed for checking neutral position
    private static final double MAX_DEVIATION_THRESHOLD = 8.0; // Used to tell if driver deviates from neutral
    private long deviationStartTime = 0;
    private long eyeClosenessStartTime = 0;
    private long livenessStartTime = 0;
    private boolean isCurrentlyDeviating = false;   // deviation from neutral used in active session
    private boolean isCurrentlyClosingEyes = false; // eyecloseness check used in active session
    private boolean isCurrentlyNotLive = false;     // liveness check used in active session
    private static final long DEVIATION_THRESHOLD_MS = 5000; //5 seconds
    private static final long EYECLOSENESS_THRESHOLD_MS = 2000; // 2 seconds
    private static final long LIVENESS_THRESHOLD_MS = 2000; // 2 seconds
    private static boolean wearingMask = false; // Set during calibration, for use in active session to check the mask
    private static boolean wearingSunglasses = false; // Set during calibration, if true user should be told to take sunglasses off
    private static final double MIN_ATTRIBUTE_THRESHOLD = 0.8; // 80% of attribute detections should be true when checking last X results

    private MediaPlayer mediaPlayer;
    private LinearLayout resultsLayout; //Declare resultsLayout
    //***************Camera private variables********************
    private PreviewView previewView;
    private ExecutorService cameraExecutor;
    private static final int CAMERA_PERMISSION_CODE = 100;
    //***********************************************************
    //Private Variables for the Models: Facial Attribute Detection Model & Mediapipe Face Detection Model
    //FADM private variable
    private FacialAttributeDetectorTFLite facialAttributeDetector; // TFLite Model

    //MPFD private variable
    private MediaPipeFaceDetectionTFLite faceDetector; // TFLite Model

    // Warning log array
    private ArrayList<String> warningList = new ArrayList<>(); // Stores each warning as a string
    private boolean isNewSession = true; // Used to reset the warning list the second time calibration is run
    //***********************************************************

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        OpenCVLoader.initLocal(); // load opencv
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB); // set colorspace as SRGB

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_active_calibration);

        //*************initializing UI elements*******************
        //This is for AFTER CALIBRATION IS COMPLETED
        //Camera View
        previewView = findViewById(R.id.previewView);
        //Results layout
        resultsLayout = findViewById(R.id.resultsLayout);
        //*********************************************************

        //Retrieve preloaded model from the DriveSenseApplication class
        facialAttributeDetector = ((DriveSenseApplication) getApplication()).getFacialAttributeModel();
        faceDetector = ((DriveSenseApplication) getApplication()).getMediaPipeFaceDetectionModel();

        //Check if the FADM is NULL & MPFD is NULL
        if (facialAttributeDetector == null || faceDetector == null) {
            Log.e("ActiveCalibration", "TFLite models was NOT preloaded!");
        }

        // Check and request camera permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
        }

        cameraExecutor = Executors.newSingleThreadExecutor();

    }

    /*
    Function is for showing the layout after the calibration is done
     */
    private void showPostCalibrationLayout(){
        setContentView(R.layout.activity_main_detection);
        isPostCalibLayoutRdy = true;

        previewView = findViewById(R.id.previewViewAC); //from new layout
        messageLayout = findViewById(R.id.messageLayout);
        cameraToggleButton = findViewById(R.id.cameraToggleButton);
        deviationWarningText = findViewById(R.id.deviationWarningText);

        //Recalibrate Button Functionality
        ImageButton recalibrateButton = findViewById(R.id.recalibrateButton);
        recalibrateButton.setOnClickListener(view -> {
            Intent intent = getIntent();
            isCalibrationComplete = false;
            isPostCalibLayoutRdy = false;
            finish(); // close current instance
            startActivity(intent); // start it fresh
                });

        previewView.setVisibility(View.VISIBLE);
        messageLayout.setVisibility(View.GONE);

        // Re-bind the camera surface provider
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        cameraToggleButton.setOnClickListener(view -> {
            if(!isCalibrationComplete) return; //Prevent toggling before calibration
            isCameraOn = !isCameraOn; //Toggle state

            previewView.setVisibility(isCameraOn ? View.VISIBLE : View.GONE);
            messageLayout.setVisibility(isCameraOn ? View.GONE : View.VISIBLE);
        });

        //Export button function here
    }


    @OptIn(markerClass = ExperimentalCamera2Interop.class)
    private void startCamera() {
        // If warning list is not empty and this is a new session clear it otherwise keep the warnings
        if (isNewSession && !warningList.isEmpty())
        {
            warningList.clear();
            isNewSession = false;
        }

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_FRONT) // Use the front camera
                        .build();

                preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                //Create ImageCapture.Builder for Camera2Interop
                ImageCapture.Builder imageCaptureBuilder = new ImageCapture.Builder();
                Extender<ImageCapture> extender = new Extender<>(imageCaptureBuilder);
                extender.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT); // Fix green tint
                ImageCapture imageCapture = imageCaptureBuilder.build(); // Build ImageCapture

                //Image Analysis for real-time processing
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setTargetResolution(new android.util.Size(INPUT_SIZE, INPUT_SIZE))
                        .build();



                // Set the analyzer for real-time frame processing
                imageAnalysis.setAnalyzer(cameraExecutor, image -> {
                    long currentTime = System.currentTimeMillis();
                    // Only process frames if 66ms (1/15 fps) have passed since the last frame
                    if (currentTime - lastProcessedTime >= FRAME_INTERVAL_MS) {

                        // Convert the camera frame to resized Bitmaps for each model
                        Bitmap bitmapMPFD = imageToResizedBitmap(image, 256, 256);
                        Bitmap bitmapFA = resizeAndPadMaintainAspectRatio(bitmapMPFD, 128, 128, 0);

                        if (bitmapFA != null && bitmapMPFD != null) {
                            // Run inference on both models with the rotated, resized Bitmap
                            Log.d("Running models", "Getting model results");
                            FacialAttributeData faceAttributeResults = facialAttributeDetector.detectFacialAttributes(bitmapFA);
                            MediaPipeFaceDetectionData faceDetectionResults = faceDetector.detectFace(bitmapMPFD);

//                            //draw the facebox
//                            if (!isCalibrationComplete) {
//                                drawFaceBox(faceDetectionResults, bitmapMPFD);
//                            }

                            // Make sure first 10 results are recorded first before calibrating
                            if (this.counter < CALIBRATION_FRAME_COUNT)
                            {
                                counter += 1;
                            }
                            // Calibrate the system to generate the neutral position
                            else
                            {
                                boolean faceDetectedAllResults = this.faceDetectedXTimes(faceDetector.lastXResults); // Check last X positions for extreme movement
                                //wearingSunglasses = this.sunglassesDetectedXTimes(facialAttributeDetector.lastXResults); // Check last X results for sunglasses
                                wearingSunglasses = false; // Force sunglasses false for now detection not working correctly
                                wearingMask = this.maskDetectedXTimes(facialAttributeDetector.lastXResults); // Check last X results for mask
                                // Check if user moved
                                if (faceDetectedAllResults)
                                {
                                    boolean userStill = this.checkIfUserStill(faceDetector.lastXResults);
                                    // Calibration successful set the neutral position
                                    if (userStill && !isCalibrationComplete && !wearingSunglasses)
                                    {
                                        faceDetector.setNeutralPosition(faceDetectionResults);
                                        isCalibrationComplete = true;

                                        runOnUiThread(this::showPostCalibrationLayout);
                                    }
                                    // User moved too much do not set the neutral position
                                    else
                                    {
                                        Log.d("Calibration", "ERROR: User needs to stay still.");
                                        if (wearingSunglasses)
                                        {
                                            // Tell user to take sunglasses off
                                            Log.d("Calibration", "ERROR: User needs to take off sunglasses.");
                                        }
                                    }
                                }
                                // Face was not detected enough times to check movement
                                else
                                {
                                    Log.d("Calibration", "ERROR: Face was not detected 5 times, last 5 records needs to have detected a face.");
                                }
                            }

                            if (isCalibrationComplete && isPostCalibLayoutRdy) {
                                Log.d("DetectionLoop", "Calibration is complete and Layout is ready.");
                                //drawFaceBox(faceDetectionResults, bitmapMPFD);
                                // Deviation check logic
                                MediaPipeFaceDetectionData neutral = faceDetector.getNeutralPosition();
                                if (neutral == null) {
                                    Log.e("DeviationCheck", "Neutral position is null! Skipping deviation check.");
                                    return;
                                }

                                boolean deviating = isDeviatingFromNeutral(faceDetectionResults, neutral); // check if driver deviates from neutral
                                boolean eyeClosenessLastXResults = eyeClosenessDetectedXTimes(facialAttributeDetector.lastXResults); // check if eyeCloseness is above the threshold for last X results (default is 80%)
                                boolean livenessLastXResults = livenessDetectedXTimes(facialAttributeDetector.lastXResults); // check if liveness is above the threshold for last X results

                                // First check is deviation from neutral
                                if (deviating) {
                                    Log.d("DetectionLoop", "In deviating if statement.");
                                    if (!isCurrentlyDeviating) {
                                        deviationStartTime = System.currentTimeMillis();
                                        isCurrentlyDeviating = true;
                                    } else {
                                        long elapsed = System.currentTimeMillis() - deviationStartTime;
                                        if (elapsed >= DEVIATION_THRESHOLD_MS) {
                                            runOnUiThread(() -> {
                                                if (deviationWarningText != null) {
                                                    deviationWarningText.setText("⚠️ Please return to neutral position!");
                                                    deviationWarningText.setTextColor(Color.rgb(255, 191, 0));
                                                    deviationWarningText.setVisibility(View.VISIBLE);
                                                }
                                                if (mediaPlayer == null) {
                                                    mediaPlayer = MediaPlayer.create(this, R.raw.warning_sound);
                                                    mediaPlayer.setLooping(true);
                                                    mediaPlayer.start();
                                                }
                                            });
                                            Log.w("WARNING", "Driver has been looking away for more than 5 seconds!");
                                        }
                                    }
                                }
//                                // Then check eyeCloseness
                                else if (eyeClosenessLastXResults)
                                {
                                    Log.d("DetectionLoop", "In eyecloseness if statement.");
                                    if (!isCurrentlyClosingEyes) {
                                        eyeClosenessStartTime = System.currentTimeMillis();
                                        isCurrentlyClosingEyes = true;
                                    } else {
                                        long elapsed = System.currentTimeMillis() - eyeClosenessStartTime;
                                        if (elapsed >= EYECLOSENESS_THRESHOLD_MS) {
                                            runOnUiThread(() -> {
                                                if (deviationWarningText != null) {
                                                    deviationWarningText.setText("⚠️ Please open your eyes!");
                                                    deviationWarningText.setTextColor(Color.rgb(255, 191, 0));
                                                    deviationWarningText.setVisibility(View.VISIBLE);
                                                }
                                                if (mediaPlayer == null) {
                                                    mediaPlayer = MediaPlayer.create(this, R.raw.warning_sound);
                                                    mediaPlayer.setLooping(true);
                                                    mediaPlayer.start();
                                                }
                                            });
                                            Log.w("WARNING", "Driver has been closing eyes more than 2 seconds!");
                                        }
                                    }
                                }
                                // Lastly check liveness
                                else if (livenessLastXResults)
                                {
                                    Log.d("DetectionLoop", "In liveness if statement.");
                                    if (!isCurrentlyNotLive) {
                                        livenessStartTime = System.currentTimeMillis();
                                        isCurrentlyNotLive = true;
                                    } else {
                                        long elapsed = System.currentTimeMillis() - livenessStartTime;
                                        if (elapsed >= LIVENESS_THRESHOLD_MS) {
                                            runOnUiThread(() -> {
                                                if (deviationWarningText != null) {
                                                    deviationWarningText.setText("⚠️ Liveness not detected!");
                                                    deviationWarningText.setTextColor(Color.rgb(255, 191, 0));
                                                    deviationWarningText.setVisibility(View.VISIBLE);
                                                }
                                                if (mediaPlayer == null) {
                                                    mediaPlayer = MediaPlayer.create(this, R.raw.warning_sound);
                                                    mediaPlayer.setLooping(true);
                                                    mediaPlayer.start();
                                                }
                                            });
                                            Log.w("WARNING", "Driver does not have liveness for more than 2 seconds!");
                                        }
                                    }
                                }
                                // If driver passes every check reset the chime and hide warning text
                                else {
                                    runOnUiThread(() -> {
                                        isCurrentlyDeviating = false;
                                        isCurrentlyClosingEyes = false;
                                        isCurrentlyNotLive = false;
                                        deviationStartTime = 0;

                                        if (deviationWarningText != null) {
                                            deviationWarningText.setVisibility(View.GONE);
                                        }

                                        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                                            mediaPlayer.stop();
                                            mediaPlayer.release();
                                            mediaPlayer = null;
                                        }
                                    });
                                }
                            }

                            // Debug logs to check if MediaPipe is detecting faces
                            Log.d("MediaPipe", "Face Detected: " + faceDetectionResults.faceDetected);
                            Log.d("MediaPipe", "Bounding Box: Width=" + faceDetectionResults.boxWidth + ", Height=" + faceDetectionResults.boxHeight);


                            // Update the last processed time
                            lastProcessedTime = currentTime;

                            // Update the UI with the results
                            if (!isCalibrationComplete) {
                                runOnUiThread(() -> updateAttributesUI(faceAttributeResults, faceDetectionResults));
                            }

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
//        if (!faceDetectionResults.faceDetected) {
//            Log.w("MediaPipe", "No face detected, skipping UI update.");
//            return; // Skip updating UI if no face detected
//        }

//        // Check if the results are being passed correctly
//        Log.d("FacialAttributes", "Eye Openness Left: " + !attributeResults.eyeClosenessL);
//        Log.d("FacialAttributes", "Eye Openness Right: " + !attributeResults.eyeClosenessR);
//        Log.d("FacialAttributes", "Liveness: " + attributeResults.liveness);
//        Log.d("FacialAttributes", "Glasses: " + attributeResults.glasses);
//        Log.d("FacialAttributes", "Sunglasses: " + attributeResults.sunglasses);
//        Log.d("FacialAttributes", "Mask: " + attributeResults.mask);
//        // Check if results are displaying properly
//        Log.d("FaceDetectionResults", "Box Center (X, Y): " + " (" + faceDetectionResults.boxCenterX + ", " + faceDetectionResults.boxCenterY);
//        Log.d("FaceDetectionResults", "Box Width: " + faceDetectionResults.boxWidth);
//        Log.d("FaceDetectionResults", "Box Height: " + faceDetectionResults.boxHeight);
//        Log.d("FaceDetectionResults", "Right Eye (X, Y): " + " (" + faceDetectionResults.rightEyeX + ", " + faceDetectionResults.rightEyeY);
//        Log.d("FaceDetectionResults", "Left Eye (X, Y): " + " (" + faceDetectionResults.leftEyeX + ", " + faceDetectionResults.leftEyeY);
//        Log.d("FaceDetectionResults", "Nose Tip (X, Y): " + " (" + faceDetectionResults.noseTipX + ", " + faceDetectionResults.noseTipY);
//        Log.d("FaceDetectionResults", "Mouth Center (X, Y): " + " (" + faceDetectionResults.mouthCenterX + ", " + faceDetectionResults.mouthCenterY);
//        Log.d("FaceDetectionResults", "Right Ear (X, Y): " + " (" + faceDetectionResults.rightEarTragionX + ", " + faceDetectionResults.rightEarTragionY);
//        Log.d("FaceDetectionResults", "Left Ear (X, Y): " + " (" + faceDetectionResults.leftEarTragionX + ", " + faceDetectionResults.leftEarTragionY);
//        Log.d("FaceDetectionResults", "Face Detected: " + faceDetectionResults.faceDetected);
////
////        // Display results for attributes
////        TextView eyeOpennessText = findViewById(R.id.eyeOpennessText);
////        TextView livenessText = findViewById(R.id.livenessText);
////        TextView glassesText = findViewById(R.id.glassesText);
////        TextView maskText = findViewById(R.id.maskText);
////        TextView sunglassesText = findViewById(R.id.sunglassesText);
//
//        eyeOpennessText.setText("Eye Openness: Left: " + (!attributeResults.eyeClosenessL ? "True" : "False") + ", Right: " + (!attributeResults.eyeClosenessR ? "True" : "False"));
//        livenessText.setText("Liveness: " + (attributeResults.liveness ? "True" : "False"));
//        glassesText.setText("Glasses: " + (attributeResults.glasses ? "True" : "False"));
//        maskText.setText("Mask: " + (attributeResults.mask ? "True" : "False"));
//        sunglassesText.setText("Sunglasses: " + (attributeResults.sunglasses ? "True" : "False"));
//
////        // Display face detection results and key points
////        TextView faceDetectedText = findViewById(R.id.faceDetectedText);
////        TextView boxCenterText = findViewById(R.id.boxCenterText);
////        TextView boxWHText = findViewById(R.id.boxWHText);
////        TextView rightEyeText = findViewById(R.id.rightEyeText);
////        TextView leftEyeText = findViewById(R.id.leftEyeText);
////        TextView noseTipText = findViewById(R.id.noseTipText);
////        TextView mouthCenterText = findViewById(R.id.mouthCenterText);
////        TextView rightEarText = findViewById(R.id.rightEarText);
////        TextView leftEarText = findViewById(R.id.leftEarText);
//
//        faceDetectedText.setText("Face Detected: " + (faceDetectionResults.faceDetected ? "True" : "False"));
//        boxCenterText.setText("Box Center (X, Y): " + " (" + faceDetectionResults.boxCenterX + ", " + faceDetectionResults.boxCenterY);
//        boxWHText.setText("Box Width X Height: " + " (" + faceDetectionResults.boxWidth + ", " + faceDetectionResults.boxHeight);
//        rightEyeText.setText("Right Eye (X, Y): " + " (" + faceDetectionResults.rightEyeX + ", " + faceDetectionResults.rightEyeY);
//        leftEyeText.setText("Left Eye (X, Y): " + " (" + faceDetectionResults.leftEyeX + ", " + faceDetectionResults.leftEyeY);
//        noseTipText.setText("Nose Tip (X, Y): " + " (" + faceDetectionResults.noseTipX + ", " + faceDetectionResults.noseTipY);
//        mouthCenterText.setText("Mouth Center (X, Y): " + " (" + faceDetectionResults.mouthCenterX + ", " + faceDetectionResults.mouthCenterY);
//        rightEarText.setText("Right Ear (X, Y): " + " (" + faceDetectionResults.rightEarTragionX + ", " + faceDetectionResults.rightEarTragionY);
//        leftEarText.setText("Left Ear (X, Y): " + " (" + faceDetectionResults.leftEarTragionX + ", " + faceDetectionResults.leftEarTragionY);

        TextView calibrationStatusText = findViewById(R.id.calibrationStatusText);

        if (counter < CALIBRATION_FRAME_COUNT) {
            calibrationStatusText.setText("Calibrating... Hold still.");
        } else if (!faceDetectedXTimes(faceDetector.lastXResults)) {
            calibrationStatusText.setText("Error: Face not detected consistently.");
            calibrationStatusText.setTextColor(Color.RED);
        } else if (!checkIfUserStill(faceDetector.lastXResults)) {
            calibrationStatusText.setText("Error: Please stay still.");
            calibrationStatusText.setTextColor(Color.RED);
        }
        // Sunglasses detection not working correctly
//        else if (sunglassesDetectedXTimes(facialAttributeDetector.lastXResults)) {
//            calibrationStatusText.setText("Error: Please remove your sunglasses.");
//            calibrationStatusText.setTextColor(Color.RED);
//        }
        else {
            calibrationStatusText.setText("Calibration Successful!");
            calibrationStatusText.setTextColor(Color.GREEN);
        }

    }

    /*
    **********************************************************************
    Image conversion functions for rotating the bitmap, resizing,
    yuv to RGB.
     */
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
            byte[] nv21;
            ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
            ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
            ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();

            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();

            nv21 = new byte[ySize + uSize + vSize];

            //U and V are swapped
            yBuffer.get(nv21, 0, ySize);
            vBuffer.get(nv21, ySize, vSize);
            uBuffer.get(nv21, ySize + vSize, uSize);

            Mat mRGB = getYUV2Mat(nv21, image);

            Bitmap bitmap = convertMatToBitMap(mRGB);

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

    // Create Mat from YUV
    public Mat getYUV2Mat(byte[] data, Image image) {
        Mat mYuv = new Mat(image.getHeight() + image.getHeight() / 2, image.getWidth(), CV_8UC1);
        mYuv.put(0, 0, data);
        Mat mRGB = new Mat();
        cvtColor(mYuv, mRGB, Imgproc.COLOR_YUV2RGB_NV21, 3);
        return mRGB;
    }

    // Turn Mat into bitmap
    private static Bitmap convertMatToBitMap(Mat input){
        Bitmap bmp = null;
        Mat rgb = new Mat();
        Imgproc.cvtColor(input, rgb, Imgproc.COLOR_RGB2RGBA);

        try {
            bmp = Bitmap.createBitmap(rgb.cols(), rgb.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(rgb, bmp);
        }
        catch (CvException e){
            Log.d("Exception",e.getMessage());
        }
        return bmp;
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
    //*********************************************************************
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
    public boolean faceDetectedXTimes(MediaPipeFaceDetectionData[] history)
    {
        if (history == null) {
            Log.e("Calibration", "faceDetectedXTimes: history array is null!");
            return false;
        }

        for (int i = 0; i < history.length; i++) {
            if (history[i] == null) {
                Log.w("Calibration", "faceDetectedXTimes: history[" + i + "] is null!");
                return false; // Avoid NullPointerException
            }

            if (!history[i].faceDetected) {
                return false;
            }
        }
        return true;
    }

    // Function to check if sunglasses were detected the last X results returns boolean for use in active session
    public boolean sunglassesDetectedXTimes(FacialAttributeData[] history)
    {
        int positives = 0; // Used to get ratio of positive detections
        int total = history.length;

        // History should not be empty
        if (history == null) {
            Log.e("Calibration", "sunglassesDetectedXTimes: history array is null!");
            return false;
        }

        // For each recorded result
        for (int i = 0; i < history.length; i++) {
            // Result is true count it
            if (history[i].sunglasses)
            {
                positives += 1;
            }
        }

        double ratio = positives / total; // ratio of true detections over lastXResults

        // If true detections exceed the threshold
        if (ratio > MIN_ATTRIBUTE_THRESHOLD)
        {
            return true; // Sunglasses detected
        }
        else
        {
            return false; // Sunglasses not detected
        }
    }

    // Function to check if mask was detected the last X results returns boolean for use in active session
    public boolean maskDetectedXTimes(FacialAttributeData[] history)
    {
        int positives = 0; // Used to get ratio of positive detections
        int total = history.length;

        // History should not be empty
        if (history == null) {
            Log.e("Calibration", "maskDetectedXTimes: history array is null!");
            return false;
        }

        // For each recorded result
        for (int i = 0; i < history.length; i++) {
            // Result is true count it
            if (history[i].mask)
            {
                positives += 1;
            }
        }

        double ratio = positives / total; // ratio of true detections over lastXResults

        // If true detections exceed the threshold
        if (ratio > MIN_ATTRIBUTE_THRESHOLD)
        {
            return true; // Mask detected
        }
        else
        {
            return false; // Mask not detected
        }
    }

    // Function to check if eye closeness was detected the last X results returns boolean for use in active session
    public boolean eyeClosenessDetectedXTimes(FacialAttributeData[] history)
    {
        Log.e("Eyecloseness detection", "In function eyeClosenessDetectedXTimes");
        int positives = 0; // Used to get ratio of positive detections
        int total = history.length;

        // History should not be empty
        if (history == null) {
            Log.e("Eyecloseness detection", "eyeClosenessDetectedXTimes: history array is null!");
            return false;
        }

        // For each recorded result
        for (int i = 0; i < history.length; i++) {
            // Either Result is true count it
            Log.e("Eyecloseness detection", "eyeOpenness = " + Boolean.toString(history[i].eyeOpenness));
            if (!history[i].eyeOpenness) // if the eyes are closed
            {
                Log.e("Eyecloseness detection", "Counted as positive");
                positives += 1;
            }
        }

        double ratio = positives / total; // ratio of true detections over lastXResults

        // If true detections exceed the threshold
        if (ratio > MIN_ATTRIBUTE_THRESHOLD)
        {
            // Generate a warning message
            SessionTimer sTimer = new SessionTimer();
            Log.e("Eyecloseness detection", "Return true ratio greater than .80");
            String warningMsg = "WARNING! Time: ";
            ZonedDateTime timeOfWarning = sTimer.getCurrentTime();
            String timeStr = sTimer.getTimeStr(timeOfWarning);
            warningMsg += timeStr;
            warningMsg += " Cause: Eyes are closed!";
            warningList.add(warningMsg);
            return true; // Eyecloseness detected
        }
        else
        {
            Log.e("Eyecloseness detection", "Return false ratio less than .80");
            return false; // Eyecloseness not detected
        }
    }

    // Function to check if liveness was detected the last X results returns boolean for use in active session
    public boolean livenessDetectedXTimes(FacialAttributeData[] history)
    {
        int positives = 0; // Used to get ratio of positive detections
        int total = history.length;

        // History should not be empty
        if (history == null) {
            Log.e("Calibration", "livenesssDetectedXTimes: history array is null!");
            return false;
        }

        // For each recorded result
        for (int i = 0; i < history.length; i++) {
            // Result is false count it
            if (!history[i].liveness)
            {
                positives += 1;
            }
        }

        double ratio = positives / total; // ratio of true detections over lastXResults

        // If true detections exceed the threshold
        if (ratio > MIN_ATTRIBUTE_THRESHOLD)
        {
            return true; // Liveness detected
        }
        else
        {
            // Generate a warning message
            SessionTimer sTimer = new SessionTimer();
            Log.e("Liveness detection", "Return true ratio greater than .80");
            String warningMsg = "WARNING! Time: ";
            ZonedDateTime timeOfWarning = sTimer.getCurrentTime();
            String timeStr = sTimer.getTimeStr(timeOfWarning);
            warningMsg += timeStr;
            warningMsg += " Cause: Liveness false!";
            warningList.add(warningMsg);
            return false; // Liveness not detected
        }
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
                    //Calculate a dynamic threshold based on the face size
                    double dynamicThreshold = currRecord.boxWidth * 0.10; //10% of face width
                    //Log.d("Calibration", "Dynamic Threshold: " + dynamicThreshold);

                    // Check eyes
                    double dist = distBetweenPoints(currRecord.rightEyeX, currRecord.rightEyeY, history[i].rightEyeX, history[i].rightEyeY);
                    if (dist > dynamicThreshold)
                    {
                        return false;
                    }
                    dist = distBetweenPoints(currRecord.leftEyeX, currRecord.leftEyeY, history[i].leftEyeX, history[i].leftEyeY);
                    if (dist > dynamicThreshold)
                    {
                        return false;
                    }
                    // Check nose
                    dist = distBetweenPoints(currRecord.noseTipX, currRecord.noseTipY, history[i].noseTipX, history[i].noseTipY);
                    if (dist > dynamicThreshold)
                    {
                        return false;
                    }
                    // Check mouth
                    dist = distBetweenPoints(currRecord.mouthCenterX, currRecord.mouthCenterY, history[i].mouthCenterX, history[i].mouthCenterY);
                    if (dist > dynamicThreshold)
                    {
                        return false;
                    }
                    // Check ears
                    dist = distBetweenPoints(currRecord.rightEarTragionX, currRecord.rightEarTragionY, history[i].rightEarTragionX, history[i].rightEarTragionY);
                    if (dist > dynamicThreshold)
                    {
                        return false;
                    }
                    dist = distBetweenPoints(currRecord.leftEarTragionX, currRecord.leftEarTragionY, history[i].leftEarTragionX, history[i].leftEarTragionY);
                    if (dist > dynamicThreshold)
                    {
                        return false;
                    }
                }
            }
        }
        // All points close enough to each other, driver has not made any major movements
        return true;
    }

    public boolean isDeviatingFromNeutral(MediaPipeFaceDetectionData results, MediaPipeFaceDetectionData neutral)
    {
        if (results == null || neutral == null) {
            Log.e("isDeviatingFromNeutral", "results or neutral is null");
            return false;
        }

        SessionTimer sTimer = new SessionTimer();
        boolean deviating = false;
        double neutralDistBetLeftEyeLeftEar = Math.abs(distBetweenPoints(neutral.leftEyeX, neutral.leftEyeY, neutral.leftEarTragionX, neutral.leftEarTragionY));
        double neutralDistBetRightEyeRightEar = Math.abs(distBetweenPoints(neutral.rightEyeX, neutral.rightEyeY, neutral.rightEarTragionX, neutral.rightEarTragionY));
        double neutralDistBetNoseMouth = Math.abs(distBetweenPoints(neutral.noseTipX, neutral.noseTipY, neutral.mouthCenterX, neutral.mouthCenterY));
        double distBetLeftEyeLeftEar = Math.abs(distBetweenPoints(results.leftEyeX, results.leftEyeY, results.leftEarTragionX, results.leftEarTragionY));
        double distBetRightEyeRightEar = Math.abs(distBetweenPoints(results.rightEyeX, results.rightEyeY, results.rightEarTragionX, results.rightEarTragionY));
        double distBetNoseMouth = Math.abs(distBetweenPoints(results.noseTipX, results.noseTipY, results.mouthCenterX, results.mouthCenterY));

        // Compare results against neutral position
        if (Math.abs(neutralDistBetLeftEyeLeftEar - distBetLeftEyeLeftEar) > MAX_DEVIATION_THRESHOLD)
        {
            deviating = true;
        }
        else if (Math.abs(neutralDistBetRightEyeRightEar - distBetRightEyeRightEar) > MAX_DEVIATION_THRESHOLD)
        {
            deviating = true;
        }
        else if (Math.abs(neutralDistBetNoseMouth - distBetNoseMouth) > MAX_DEVIATION_THRESHOLD)
        {
            deviating = true;
        }

        // Check which direction the driver is deviating
        if (deviating)
        {
            // Create a warning message if driver deviates with time and cause
            String warningMsg = "WARNING! Time: ";
            ZonedDateTime timeOfWarning = sTimer.getCurrentTime();
            String timeStr = sTimer.getTimeStr(timeOfWarning);
            warningMsg += timeStr;
            warningMsg += " Cause: ";
            // Turning Left
            if (results.noseTipX > neutral.noseTipX)
            {
                warningMsg += "Driver looking Left!";
                Log.e("WARNING!", timeStr + ": Driver looking Left!");
            }
            // Turning Right
            if (results.noseTipX < neutral.noseTipX)
            {
                warningMsg += "Driver looking Right!";
                Log.e("WARNING!", timeStr + ": Driver looking Right!");
            }
            // Looking Up
            if (results.noseTipY < neutral.noseTipY)
            {
                warningMsg += "Driver looking Up!";
                Log.e("WARNING!", timeStr + ": Driver looking Up!");
            }
            // Looking Down
            if (results.noseTipY > neutral.noseTipY)
            {
                warningMsg += "Driver looking Down!";
                Log.e("WARNING!", timeStr + ": Driver looking Down!");
            }
            warningList.add(warningMsg); // Append the warning to the list
        }
        return deviating;
    }

//    private void drawFaceBox(MediaPipeFaceDetectionData faceData, Bitmap inputBitmap) {
//        runOnUiThread(() -> {
//            FaceOverlayView faceOverlay = findViewById(R.id.faceOverlay);
//            if (faceOverlay != null && faceData.faceDetected && isCameraOn) {
//                float scaleX = previewView.getWidth() / (float) inputBitmap.getWidth();
//                float scaleY = previewView.getHeight() / (float) inputBitmap.getHeight();
//
//                float left = (float) (faceData.boxCenterX - faceData.boxWidth / 2.0);
//                float top = (float) (faceData.boxCenterY - faceData.boxHeight / 2.0);
//                float right = (float) (left + faceData.boxWidth);
//                float bottom = (float) (top + faceData.boxHeight);
//
//                RectF box = new RectF(
//                        left * scaleX,
//                        top * scaleY,
//                        right * scaleX,
//                        bottom * scaleY
//                );
//
//                faceOverlay.updateBox(box);
//                faceOverlay.setVisibility(View.VISIBLE);
//            } else if (faceOverlay != null) {
//                faceOverlay.setVisibility(View.GONE);
//            }
//        });
//    }

}