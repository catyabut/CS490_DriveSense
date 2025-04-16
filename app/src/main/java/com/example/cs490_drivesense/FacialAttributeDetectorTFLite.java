package com.example.cs490_drivesense;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

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

public class FacialAttributeDetectorTFLite {
    private static final String MODEL_PATH = "face_attrib_net.tflite"; // Ensure this is in the assets folder
    private static final int INPUT_SIZE = 128; // Adjust to your model input size
    private static final int NUM_CHANNELS = 3; // RGB
    private static final int OUTPUT_SIZE = 6; // Adjust based on model output
    private static final double EYE_OPENNESS_MIN_THRESHOLD = 0.25;
    private static final double GLASSES_MIN_THRESHOLD = 0.80;
    private static final double SUNGLASSES_MIN_THRESHOLD = 0.80;
    private static final double MASK_MIN_THRESHOLD = 0.80;
    private static final double LIVENESS_MAX_THRESHOLD = 10;
    private static final int MAX_DETECTION_DATA = 10; //max number of previous facial attribute data


    private Map<Integer, Object> rawOutputs;
    public FacialAttributeData[] lastXResults = new FacialAttributeData[MAX_DETECTION_DATA];
    private FloatBuffer originalLivenessEmbedding;
    private FloatBuffer currentLivenessEmbedding;
    public double probEyeOpenness;
    public double probSunglasses;
    public double probGlasses;
    public double probMask;
    public boolean eyeOpenness;
    public boolean sunglasses;
    public double livenessLoss;
    public boolean liveness;
    public boolean glasses;
    public boolean mask;
    public boolean setEmbedding = true;

    private Interpreter tfliteInterpreter;

    public FacialAttributeDetectorTFLite(AssetManager assetManager) {
        try {
            tfliteInterpreter = new Interpreter(Objects.requireNonNull(loadModelFile(assetManager)));
        } catch (IOException e) {
            Log.e("TFLite", "Error loading TensorFlow Lite model", e);
        }
    }

    public FacialAttributeData detectFacialAttributes(Bitmap resizedBitmap) {


        // Preprocess image into bytebuffer
        ByteBuffer inputBuffer = bitMapToByteBuffer(resizedBitmap);

        // Set up input parameters for multipleInputsOutputs
        Object[] inputs = setUpInputBuffer(inputBuffer);

        // Set up Model output buffers
        Map<Integer, Object> rawOutputs = setUpOutputBuffers();

        // Run inference
        tfliteInterpreter.runForMultipleInputsOutputs(inputs, rawOutputs);
        this.rawOutputs = rawOutputs; // store outputs

        // Postprocess raw outputs

        if (setEmbedding)
        {
            setOriginalEmbedding(rawOutputs); // Original embedding created upon calibration
            setEmbedding = false;
        }
        setCurrentEmbedding(rawOutputs); // Set current embedding

        // Compute probabilities and booleans using raw outputs
        computeLiveness();
        computeEyeOpenness();
        computeGlasses();
        computeMask();
        computeSunglasses();

        // Return outputs as booleans
        FacialAttributeData processedData = new FacialAttributeData(this.eyeOpenness, this.sunglasses, this.liveness, this.glasses, this.mask);

        // Shift the results (move older detections down)
        for (int i = lastXResults.length - 1; i > 0; i--) {
            lastXResults[i] = lastXResults[i - 1];
        }

        // Store the latest detection
        lastXResults[0] = processedData;

        return processedData;
    }

    private ByteBuffer bitMapToByteBuffer(Bitmap bitmap) {
        ByteBuffer inputBuffer = ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * NUM_CHANNELS * 4);
        inputBuffer.order(ByteOrder.nativeOrder());
        // For FLOAT 32 use image processor
        ImageProcessor imageProcessor = new ImageProcessor.Builder().add(new NormalizeOp(0.0f, 255.0f)).build();
        TensorImage tImg = TensorImage.fromBitmap(bitmap);
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
        Map<Integer, Object> outputs = new HashMap<>();
        FloatBuffer fbuffer = FloatBuffer.allocate(512);
        outputs.put(0, fbuffer);  // id_feature
        fbuffer = FloatBuffer.allocate(32);
        outputs.put(1, fbuffer);  // liveness
        fbuffer = FloatBuffer.allocate(2);
        outputs.put(2, fbuffer);  // eye_closeness
        fbuffer = FloatBuffer.allocate(2);
        outputs.put(3, fbuffer);  // glasses
        fbuffer = FloatBuffer.allocate(2);
        outputs.put(4, fbuffer);  // mask
        fbuffer = FloatBuffer.allocate(2);
        outputs.put(5, fbuffer);  // sunglasses
        return outputs;
    }

    // Postprocessing

    // Set original embedding for liveness
    private void setOriginalEmbedding(Map<Integer, Object> rawOutputs)
    {
        Object livenessResults = rawOutputs.get(1);
        FloatBuffer buffer = (FloatBuffer) livenessResults;
        this.originalLivenessEmbedding = buffer;
    }

    // Set current embedding for liveness
    private void setCurrentEmbedding(Map<Integer, Object> rawOutputs)
    {
        Object livenessResults = rawOutputs.get(1);
        FloatBuffer buffer = (FloatBuffer) livenessResults;
        this.currentLivenessEmbedding = buffer;
    }

    // Calculate softmax for a given element in a FloatBuffer
    // input = element in FloatBuffer
    // buffer = Entire FloatBuffer
    public static double softmax(double input, FloatBuffer buffer)
    {
        // Calculates e^element / sum(e^i element) for all i in buffer where i = 0 to sizeOfBuffer
        double result = 0.0; // Return value
        double total = 0.0; // denominator = sum of e^i element of buffer for all elements

        buffer.flip(); // For reading the buffer set pos to 0
        while(buffer.hasRemaining())
        {
            double element = buffer.get();
            total += Math.exp(element); // e^(element)
        }

        result = Math.exp(input) / total; // softmax formula for single element
        return result;
    }

    // L2 loss function for liveness
    // L2 = sum((y_true - y_pred)^2) where y = element from embedding
    // Loss closer to 0 means the liveness check passes
    private double L2LossFunc()
    {
        double loss = 0.0;

        FloatBuffer original = this.originalLivenessEmbedding;
        FloatBuffer current = this.currentLivenessEmbedding;

        original.flip(); // Set pos to 0
        //current.flip();  // Set pos to 0

        // For each element of both embeddings
        while (original.hasRemaining())
        {
            // Find their difference squared
            double y_true = original.get();
            double y_pred = current.get();
            double diff = y_true - y_pred;
            double square = Math.pow(diff, 2);
            // add to final result
            loss += square;
        }

        return loss;
    }

    // Compute liveness using L2 loss function
    public void computeLiveness()
    {
        this.computeLivenessLoss();
        this.computeLivenessBoolean();
    }

    // Compute eyeOpennes using softmax
    public void computeEyeOpenness()
    {
        this.computeEyeOpennessProb();
        this.computeEyeOpennessBoolean();
    }

    // Compute glasses using softmax
    public void computeGlasses()
    {
        this.computeGlassesProb();
        this.computeGlassesBoolean();
    }

    // Compute mask using softmax
    public void computeMask()
    {
        this.computeMaskProb();
        this.computeMaskBoolean();
    }

    // Compute sunglasses probability part of raw outputs
    public void computeSunglasses()
    {
        this.computeSunglassesProb();
        this.computeSunglassesBoolean();
    }

    // Compute eyeOpenness probability and boolean for left and right eyes
    private void computeEyeOpennessProb()
    {
        Object eyeClosenessResults = this.rawOutputs.get(2);
        //Class<?> classtype = eyeClosenessResults.getClass();
        FloatBuffer buffer = (FloatBuffer) eyeClosenessResults;
        double eyeOpennesProbability = softmax(buffer.get(0), buffer);
        double eyeClosenessProbability = softmax(buffer.get(1), buffer);
        this.probEyeOpenness = eyeOpennesProbability;
    }

    // Compute glasses probabilities
    private void computeGlassesProb()
    {
        Object glassesResults = this.rawOutputs.get(3);
        FloatBuffer buffer = (FloatBuffer) glassesResults;
        double glassesProbability = softmax(buffer.get(0), buffer);
        this.probGlasses = glassesProbability;
    }

    // Compute mask probabilities
    private void computeMaskProb()
    {
        Object maskResults = this.rawOutputs.get(4);
        FloatBuffer buffer = (FloatBuffer) maskResults;
        double maskProbability = softmax(buffer.get(0), buffer);
        this.probMask = maskProbability;
    }

    // Compute sunglasses probability
    private void computeSunglassesProb()
    {
        Object sunglassesResults = this.rawOutputs.get(5);
        FloatBuffer buffer = (FloatBuffer) sunglassesResults;
        this.probSunglasses = buffer.get(0); // Probability sunglasses true
    }

    // Compute liveness loss
    private void computeLivenessLoss()
    {
        double loss = this.L2LossFunc();
        this.livenessLoss = loss;
    }

    // Compute eyeopenness boolean
    private void computeEyeOpennessBoolean()
    {
        Log.d("ComputeEyeOpenness", "Prob = " + Double.toString(this.probEyeOpenness));
        if (this.probEyeOpenness > EYE_OPENNESS_MIN_THRESHOLD)
        {
            this.eyeOpenness = true;
        }
        else
        {
            this.eyeOpenness = false;
        }
    }

    // Compute glasses boolean
    private void computeGlassesBoolean()
    {
        if (this.probGlasses > GLASSES_MIN_THRESHOLD)
        {
            this.glasses = true;
        }
        else
        {
            this.glasses = false;
        }
    }
    // Compute mask boolean
    private void computeMaskBoolean()
    {
        if (this.probMask > MASK_MIN_THRESHOLD)
        {
            this.mask = true;
        }
        else
        {
            this.mask = false;
        }
    }

    // Compute sunglasses booleans
    private void computeSunglassesBoolean()
    {
        if (this.probSunglasses > SUNGLASSES_MIN_THRESHOLD)
        {
            this.sunglasses = true;
        }
        else
        {
            this.sunglasses = false;
        }
    }

    // Compute liveness boolean
    private void computeLivenessBoolean()
    {
        // If loss is close to 0 meaning liveness embeddings are similar
        if (this.livenessLoss < LIVENESS_MAX_THRESHOLD)
        {
            this.liveness = true;
        }
        else
        {
            this.liveness = false;
        }
    }


    public void close() {
        tfliteInterpreter.close();
    }
}
