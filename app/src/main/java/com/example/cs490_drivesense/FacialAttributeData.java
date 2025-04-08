package com.example.cs490_drivesense;

import java.nio.FloatBuffer;

public class FacialAttributeData {
    public FacialAttributeData(boolean eyeOpenness, boolean sunglasses, boolean liveness, boolean glasses, boolean mask)
    {
        this.eyeOpenness = eyeOpenness;
        this.sunglasses = sunglasses;
        this.liveness = liveness;
        this.glasses = glasses;
        this.mask = mask;
    }
    public boolean eyeOpenness;
    public boolean sunglasses;
    public boolean liveness;
    public boolean glasses;
    public boolean mask;
}
