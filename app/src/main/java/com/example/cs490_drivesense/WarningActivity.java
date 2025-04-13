package com.example.cs490_drivesense;

import android.content.Intent;
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

        warningTextView = findViewById(R.id.warningsTextView);
        exportButton = findViewById(R.id.exportButton);
        doneButton = findViewById(R.id.doneButton);
        warningList = getIntent().getStringArrayListExtra("warnings");

        // Make sure there are warnings to display
        if (warningList != null && !warningList.isEmpty())
        {
            StringBuilder builder = new StringBuilder();
            // Concatenate all warnings so they are one long string for use in TextView
            for (String warning : warningList)
            {
                builder.append(warning).append("\n");
            }
            warningTextView.setText(builder.toString());
        }
        else
        {
            warningTextView.setText("There are no warnings to display.");
        }

        // Export button should store warnings in a text file
        exportButton.setOnClickListener(view -> {
            exportWarningsToFile(warningList); // Needs to be implemented
        });
        // Done button should go back to calibration screen
        doneButton.setOnClickListener(view -> {
            // Move back to calibration screen
            Intent intent = new Intent(WarningActivity.this, ActiveCalibrationActivity.class);
            startActivity(intent);
        });

    }

    void exportWarningsToFile(ArrayList<String> warninglist)
    {
        // Creates a .txt file using the warnings

    }
}