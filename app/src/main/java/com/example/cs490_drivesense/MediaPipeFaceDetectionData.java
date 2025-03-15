package com.example.cs490_drivesense;

import java.nio.FloatBuffer;
import java.util.Map;

public class MediaPipeFaceDetectionData {
    public FloatBuffer[] boxCoords; // 896 box coords. Each box coord is an array of 16 float values
    public FloatBuffer boxScores;   // 896 box scores

    MediaPipeFaceDetectionData(FloatBuffer[] boxCoords, FloatBuffer boxScores)
    {
        this.boxCoords = boxCoords;
        this.boxScores = boxScores;
    }
}
