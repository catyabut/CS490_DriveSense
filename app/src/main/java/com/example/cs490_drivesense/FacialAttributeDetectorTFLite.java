package com.example.cs490_drivesense;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
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

    public float[] detectFacialAttributes(Bitmap bitmap) {
        // Resize bitmap to match model input size
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true);

        // Preprocess image
        ByteBuffer inputBuffer = preprocessImage(resizedBitmap);

        // Model output
        float[][] outputData = new float[1][OUTPUT_SIZE];

        // Run inference
        tfliteInterpreter.run(inputBuffer, outputData);

        return outputData[0];
    }

    private ByteBuffer preprocessImage(Bitmap bitmap) {
        ByteBuffer inputBuffer = ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * NUM_CHANNELS * 4);
        inputBuffer.order(ByteOrder.nativeOrder());

        for (int y = 0; y < INPUT_SIZE; y++) {
            for (int x = 0; x < INPUT_SIZE; x++) {
                int pixel = bitmap.getPixel(x, y);
                float r = Color.red(pixel) / 255.0f;
                float g = Color.green(pixel) / 255.0f;
                float b = Color.blue(pixel) / 255.0f;

                inputBuffer.putFloat(r);
                inputBuffer.putFloat(g);
                inputBuffer.putFloat(b);
            }
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
