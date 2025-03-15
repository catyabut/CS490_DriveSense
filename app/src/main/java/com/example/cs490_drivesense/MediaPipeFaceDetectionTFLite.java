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
import java.util.HashMap;
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
    private Map<Integer, Object> rawOutputs;
    private FloatBuffer[] boxCoords = new FloatBuffer[896];
    private FloatBuffer boxScores;
    public boolean isCalibrating = true;
    private Interpreter tfliteInterpreter;

    public MediaPipeFaceDetectionTFLite(AssetManager assetManager) {
        try {
            tfliteInterpreter = new Interpreter(Objects.requireNonNull(loadModelFile(assetManager)));
        } catch (IOException e) {
            Log.e("TFLite", "Error loading TensorFlow Lite model", e);
        }
    }

    public MediaPipeFaceDetectionData detectFace(Bitmap resizedBitmap) {


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

        // Postprocess raw outputs HAVE TO FIGURE THIS OUT

        // Anything that has to be done during calibration only
        if (isCalibrating)
        {

            this.isCalibrating = false;
        }

        // Return raw box coords and scores no post processing yet
        MediaPipeFaceDetectionData processedData = new MediaPipeFaceDetectionData(this.boxCoords, this.boxScores);
        return processedData;
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
        for (int i = 0; i < 56; i++)
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

}
