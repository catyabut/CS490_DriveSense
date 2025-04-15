package com.example.cs490_drivesense;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

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
        ConstraintLayout rootLayout = findViewById(R.id.main);
        if (rootLayout != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return insets;
            });
        } else {
            Log.e("WarningActivity", "Root layout 'main' was null!");
        }

        warningTextView = findViewById(R.id.warningsTextView);
        exportButton = findViewById(R.id.exportButton);
        doneButton = findViewById(R.id.doneButton);
        warningList = getIntent().getStringArrayListExtra("warnings");

        // Make sure there are warnings to display
        if (warningTextView != null) {
            if (warningList != null && !warningList.isEmpty()) {
                StringBuilder builder = new StringBuilder();
                for (String warning : warningList) {
                    builder.append(warning).append("\n");
                }
                warningTextView.setText(builder.toString());
            } else {
                warningTextView.setText("There are no warnings to display.");
            }
        } else {
            Log.e("WarningActivity", "warningTextView is null! Cannot set warning text.");
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
            finish();
        });

    }

    void exportWarningsToFile(ArrayList<String> warninglist)
    {
        // Get File path for WarningLog.txt
        File path = getApplicationContext().getFilesDir();
        String fileName = "WarningLog-";
        SessionTimer sTimer = new SessionTimer();
        ZonedDateTime timeOfWarning = sTimer.getCurrentTime();
        String timeStr = sTimer.getTimeStr(timeOfWarning);
        fileName += timeStr;
        try
        {
            // Creates a .txt file using the warnings
            FileOutputStream writer = new FileOutputStream(new File(path, fileName));
            for (String warning : warningList)
            {
                // Write to the text file
                writer.write(warning.getBytes());
                writer.write("\n".getBytes());
            }
            writer.close();
            Log.d("Export Warnings", "Warnings written to file");
            Toast.makeText(getApplication(), "Wrote to file: " + fileName, Toast.LENGTH_SHORT).show();
        }
        // Handle any errors
        catch (FileNotFoundException e) {
            Log.e("Export Warnings", "File was not found");
            throw new RuntimeException(e);
        } catch (IOException e) {
            Log.e("Export Warnings", "Could not write to file " + fileName);
            throw new RuntimeException(e);
        }
    }

}