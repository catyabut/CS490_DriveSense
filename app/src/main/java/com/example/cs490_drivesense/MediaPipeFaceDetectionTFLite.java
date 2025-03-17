package com.example.cs490_drivesense;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import android.content.res.AssetManager;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.Map;
import java.util.Objects;

public class MediaPipeFaceDetectionTFLite {
    private static final String MODEL_PATH = "mediapipe_face-mediapipefacedetector.tflite"; // Ensure this is in the assets folder
    private static final int INPUT_SIZE = 256; // Adjust to your model input size
    private static final int NUM_CHANNELS = 3; // RGB
    private static final int OUTPUT_SIZE = 2; // Adjust based on model output
    private static final double BOX_SCORE_MIN_THRESHOLD = 0.25; // If sigmoid(boxScore) exceeds this value, face is detected 0.75 default

    private static final int MAX_DETECTION_DATA = 10; //max number of previous mediapipe face detection data
    private MediaPipeFaceDetectionData lastValidResults = new MediaPipeFaceDetectionData(); //
    private Map<Integer, Object> rawOutputs;
    private FloatBuffer[] boxCoords = new FloatBuffer[896]; // 896 outputs
    private FloatBuffer boxScores;
    public boolean isCalibrating = true; // The first time this model is called it will generate the neutral position
    private Interpreter tfliteInterpreter;

    public MediaPipeFaceDetectionData neutralPosition = null; // Box coords and score of neutral position
    public MediaPipeFaceDetectionData[] lastXResults = new MediaPipeFaceDetectionData[MAX_DETECTION_DATA];
    private int currentIndex = 0; // Used to iterate over last5Results

    public MediaPipeFaceDetectionTFLite(AssetManager assetManager) {
        try {
            tfliteInterpreter = new Interpreter(Objects.requireNonNull(loadModelFile(assetManager)));
        } catch (IOException e) {
            Log.e("TFLite", "Error loading TensorFlow Lite model", e);
        }
    }

    public MediaPipeFaceDetectionData detectFace(Bitmap resizedBitmap) {
        if (resizedBitmap == null) return lastValidResults; // Prevent empty results

        // Preprocess image into bytebuffer
        ByteBuffer inputBuffer = bitMapToByteBuffer(resizedBitmap);

        // Set up input parameters for multipleInputsOutputs
        Object[] inputs = setUpInputBuffer(inputBuffer);

        // Set up Model output buffers
        Map<Integer, Object> rawOutputs = setUpOutputBuffers();

        // Run inference
        tfliteInterpreter.runForMultipleInputsOutputs(inputs, rawOutputs);
        this.rawOutputs = rawOutputs; // store outputs
        this.organizeOutputs(rawOutputs); // store box coords so each box is a separate FloatBuffer instead of one list
        this.boxScores = (FloatBuffer) rawOutputs.get(1); // store boxScores

        // Store a list of all results as MediaPipeFaceDetectionData[] to send to detectDriverFromBoxes
        MediaPipeFaceDetectionData[] allBoxes = new MediaPipeFaceDetectionData[896];
        this.boxScores.flip();
        int index = 0;
        for (FloatBuffer boxCoords : this.boxCoords)
        {
            allBoxes[index] = new MediaPipeFaceDetectionData(boxCoords, boxScores.get());
            index++;
        }

        // Send the list to detectDriverFromBoxes returns the data for the driver and their updated detectedStatus
        MediaPipeFaceDetectionData driverData = detectDriverFromBoxes(allBoxes);

        if (lastXResults == null) {
            lastXResults = new MediaPipeFaceDetectionData[5]; // Initialize if null
        }

        // Shift the results (move older detections down)
        for (int i = lastXResults.length - 1; i > 0; i--) {
            lastXResults[i] = lastXResults[i - 1];
        }

        // Store the latest detection
        lastXResults[0] = driverData;

        // Return driver data
        return driverData;
    }

    private ByteBuffer bitMapToByteBuffer(Bitmap bitmap) {
        ByteBuffer inputBuffer = ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * NUM_CHANNELS);
        inputBuffer.order(ByteOrder.nativeOrder());
        // For FLOAT 32 use image processor
        ImageProcessor imageProcessor = new ImageProcessor.Builder().add(new NormalizeOp(0.0f, 255.0f)).build();
        TensorImage tImg = new TensorImage(DataType.FLOAT32);
        tImg.load(bitmap);
        inputBuffer = imageProcessor.process(tImg).getBuffer();
        return inputBuffer;
    }

    private MappedByteBuffer loadModelFile(AssetManager assetManager) throws IOException {
        try {
            AssetFileDescriptor fileDescriptor = assetManager.openFd(MODEL_PATH);
            FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
            FileChannel fileChannel = inputStream.getChannel();
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.getStartOffset(), fileDescriptor.getDeclaredLength());
        } catch (IOException e) {
            Log.e("TFLite", "Model file NOT found in assets!", e);
            return null;
        }
    }

    // Set up input buffers for interpreter should be Object[] containing bytebuffer
    private Object[] setUpInputBuffer(ByteBuffer image)
    {
        Object[] inputs = new Object[1];
        inputs[0] = image;
        return inputs;
    }

    // Set up output buffers
    private Map<Integer, Object> setUpOutputBuffers()
    {
        FloatBuffer boxCoords = FloatBuffer.allocate(896 * 16);
        FloatBuffer boxScores = FloatBuffer.allocate(896);
        Map<Integer, Object> outputs = new HashMap<>();
        outputs.put(0, boxCoords);
        outputs.put(1, boxScores);
        return outputs;
    }

    // Reorganize outputs so each box coord has 16 elements
    private void organizeOutputs(Map<Integer, Object> outputs)
    {
        FloatBuffer rawBoxCoords = (FloatBuffer) outputs.get(0);
        rawBoxCoords.flip();
        int index = 0;
        for (int i = 0; i < 896; i++)
        {
            FloatBuffer b = FloatBuffer.allocate(16);
            for (int j = 0; j < 16; j++)
            {
                b.put(rawBoxCoords.get());
            }
            this.boxCoords[index] = b;
            index++;
        }
    }

    // Returns score in range [0, 1] using formula 1 / (1 + e^-x), used for box score
    public static double sigmoid(double score)
    {
        return 1.0 / (1.0 + Math.exp((score*-1)));
    }

    // Function that uses a threshold and box score to detect all faces
    // The largest face will be considered the driver
    // Returns driver data (bounding box, keypoints, and faceDetect bool)
    MediaPipeFaceDetectionData detectDriverFromBoxes(MediaPipeFaceDetectionData[] allBoxes)
    {
        MediaPipeFaceDetectionData driver = null;     // Return value is the driver
        List<MediaPipeFaceDetectionData> allFaces = new ArrayList<MediaPipeFaceDetectionData>(); // All detected faces including passengers

        for (MediaPipeFaceDetectionData box : allBoxes)
        {
            double newScore = sigmoid(box.boxScore);
            // If a face is detected
            if (sigmoid(box.boxScore) > BOX_SCORE_MIN_THRESHOLD)
            {
                box.faceDetected = true;
                allFaces.add(box);
            }
        }

        double maxArea = 0.0;
        for (MediaPipeFaceDetectionData face : allFaces)
        {
            if ((face.boxWidth * face.boxHeight) > maxArea)
            {
                maxArea = (face.boxWidth * face.boxHeight);
                driver = face;
            }
        }

        // Face was not detected create empty data to avoid null error
        if (driver == null)
        {
            driver = new MediaPipeFaceDetectionData();
        }

        // Store in last 5 results
        this.lastXResults[this.currentIndex] = driver;
        this.currentIndex++;
        if (currentIndex == MAX_DETECTION_DATA)
        {
            currentIndex = 0; // reset index, array is size 5
        }


        return driver;
    }

    // Function to calculate the distance between 2 points
    public static double distBetweenPoints(double x1, double y1, double x2, double y2)
    {
        double xResult = x2 - x1;
        double yResult = y2 - y1;
        xResult = Math.pow(xResult, 2);
        yResult = Math.pow(yResult, 2);
        double sum = xResult + yResult;
        return Math.sqrt(sum);
    }

    public void setNeutralPosition(MediaPipeFaceDetectionData position)
    {
        this.neutralPosition = position;
    }

    public MediaPipeFaceDetectionData getNeutralPosition()
    {
        return this.neutralPosition;
    }

}
