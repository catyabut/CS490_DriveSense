package com.example.cs490_drivesense;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;

public class WarningActivity extends AppCompatActivity {

    private ArrayList<String> warningList = new ArrayList<>(); // Warnings that will be received from ActiveCalibrationActivity.java
    private TextView warningTextView;
    private Button exportButton;
    private Button doneButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_warning);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        warningList = getIntent().getStringArrayListExtra("warnings");
        StringBuilder builder = new StringBuilder();
        // Concatenate all warnings so they are one long string for use in TextView
        for (String warning : warningList)
        {

        }

    }
}