package com.example.cs490_drivesense;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.content.Intent;
import android.os.Handler;

public class LoadingActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_loading);

        //Delay for 3 seconds before switching to CalibrationActivity
        new Handler().postDelayed(() -> {
            Intent intent = new Intent(LoadingActivity.this, CalibrationActivity.class);
            startActivity(intent);
            finish(); //Close this activity so the user can't return to it
        }, 3000);
    }
}