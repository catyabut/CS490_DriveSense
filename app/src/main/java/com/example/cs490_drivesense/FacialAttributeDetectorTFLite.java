package com.example.cs490_drivesense;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Color;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

public class FacialAttributeDetectorTFLite {
    private static final String MODEL_PATH = "face_attrib_net.tflite"; // Ensure this is in the assets folder
    private static final int INPUT_SIZE = 224; // Adjust to your model input size
    private static final int NUM_CHANNELS = 3; // RGB
    private static final int OUTPUT_SIZE = 6; // Adjust based on model output

    private Interpreter tfliteInterpreter;

    public FacialAttributeDetectorTFLite(AssetManager assetManager) {
        try {
            tfliteInterpreter = new Interpreter(Objects.requireNonNull(loadModelFile(assetManager)));
        } catch (IOException e) {
            Log.e("TFLite", "Error loading TensorFlow Lite model", e);
        }
    }

    public float[] detectFacialAttributes(float[] inputImage) {
        // Prepare ByteBuffer for model input
        ByteBuffer inputBuffer = preprocessImage(inputImage);

        // Model output
        float[][] outputData = new float[1][OUTPUT_SIZE];

        // Run inference
        tfliteInterpreter.run(inputBuffer, outputData);

        return outputData[0];
    }

    private ByteBuffer preprocessImage(float[] inputImage) {
        ByteBuffer inputBuffer = ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * NUM_CHANNELS * 4);
        inputBuffer.order(ByteOrder.nativeOrder());

        // Fill the input buffer with the float array
        for (int i = 0; i < inputImage.length; i++) {
            inputBuffer.putFloat(inputImage[i]); // Input image is already normalized
        }

        return inputBuffer;
    }

    private MappedByteBuffer loadModelFile(AssetManager assetManager) throws IOException {
        try {
            AssetFileDescriptor fileDescriptor = assetManager.openFd("face_attrib_net.tflite");
            FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
            FileChannel fileChannel = inputStream.getChannel();
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.getStartOffset(), fileDescriptor.getDeclaredLength());
        } catch (IOException e) {
            Log.e("TFLite", "Model file NOT found in assets!", e);
            return null;
        }
    }


    public void close() {
        tfliteInterpreter.close();
    }
}
