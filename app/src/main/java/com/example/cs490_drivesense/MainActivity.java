package com.example.cs490_drivesense;


import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    private Button button;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // Ensure the button exists in the layout
        button = findViewById(R.id.button);
        if (button == null) {
            Log.e("MainActivity", "Button is null! Check activity_main.xml");
            return;  // Prevent crash
        }

        FaceAttributeModel model = new FaceAttributeModel(this);
        button.setOnClickListener(view -> {
            Log.d("BUTTONS", "User tapped the button");
            try {
                if (model != null) {
                    model.setUpInterpreter(model.loadModelFile());
                } else {
                    Log.e("MainActivity", "Model is null!");
                }
            } catch (IOException e) {
                Log.e("FaceAttributeModel", "Failed to load model", e);
                Toast.makeText(MainActivity.this, "Model failed to load!", Toast.LENGTH_SHORT).show();
            }
        });
    }

}