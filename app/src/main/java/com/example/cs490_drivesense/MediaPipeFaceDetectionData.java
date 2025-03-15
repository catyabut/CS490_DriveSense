package com.example.cs490_drivesense;

import java.nio.FloatBuffer;
import java.util.Map;

public class MediaPipeFaceDetectionData {
    // Bounding box information
    public double boxCenterX;
    public double boxCenterY;
    public double boxWidth;
    public double boxHeight;

    // Key points information 6 key points produced from model (both eyes, nose, mouth, both ears)

    // Right eye key point
    public double rightEyeX;
    public double rightEyeY;
    // Left eye key point
    public double leftEyeX;
    public double leftEyeY;
    // Nose tip key point
    public double noseTipX;
    public double noseTipY;
    // Mouth center key point
    public double mouthCenterX;
    public double mouthCenterY;
    // Right ear tragion key point
    public double rightEarTragionX;
    public double rightEarTragionY;
    // Left ear tragion key point
    public double leftEarTragionX;
    public double leftEarTragionY;
    // Raw boxScore
    public double boxScore;
    // Detection status based on sigmoid(boxScore) going over the threshold in detectDriverFromBoxes
    public boolean faceDetected;

    // Constructor for empty data object
    MediaPipeFaceDetectionData()
    {
        this.boxCenterX = 0.0;
        this.boxCenterY = 0.0;
        this.boxWidth = 0.0;
        this.boxHeight = 0.0;
        this.rightEyeX = 0.0;
        this.rightEyeY = 0.0;
        this.leftEyeX = 0.0;
        this.leftEyeY = 0.0;
        this.noseTipX = 0.0;
        this.noseTipY = 0.0;
        this.mouthCenterX = 0.0;
        this.mouthCenterY = 0.0;
        this.rightEarTragionX = 0.0;
        this.rightEarTragionY = 0.0;
        this.leftEarTragionX = 0.0;
        this.leftEarTragionY = 0.0;
        this.boxScore = 0.0;
        this.faceDetected = false;
    }
    // Extract info from boxCoords and boxScores
    MediaPipeFaceDetectionData(FloatBuffer boxCoords, double boxScore)
    {
        boxCoords.flip();
        this.boxCenterX = boxCoords.get();
        this.boxCenterY = boxCoords.get();
        this.boxWidth = boxCoords.get();
        this.boxHeight = boxCoords.get();
        this.rightEyeX = boxCoords.get();
        this.rightEyeY = boxCoords.get();
        this.leftEyeX = boxCoords.get();
        this.leftEyeY = boxCoords.get();
        this.noseTipX = boxCoords.get();
        this.noseTipY = boxCoords.get();
        this.mouthCenterX = boxCoords.get();
        this.mouthCenterY = boxCoords.get();
        this.rightEarTragionX = boxCoords.get();
        this.rightEarTragionY = boxCoords.get();
        this.leftEarTragionX = boxCoords.get();
        this.leftEarTragionY = boxCoords.get();
        this.boxScore = boxScore;
        this.faceDetected = false;
    }
}
