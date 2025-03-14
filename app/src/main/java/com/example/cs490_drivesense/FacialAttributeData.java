package com.example.cs490_drivesense;

import java.nio.FloatBuffer;

public class FacialAttributeData {
    public FacialAttributeData(boolean eyeClosenessL, boolean eyeClosenessR, boolean sunglasses, boolean liveness, boolean glasses, boolean mask)
    {
        this.eyeClosenessL = eyeClosenessL;
        this.eyeClosenessR = eyeClosenessR;
        this.sunglasses = sunglasses;
        this.liveness = liveness;
        this.glasses = glasses;
        this.mask = mask;
    }
    public boolean eyeClosenessL;
    public boolean eyeClosenessR;
    public boolean sunglasses;
    public boolean liveness;
    public boolean glasses;
    public boolean mask;
}
