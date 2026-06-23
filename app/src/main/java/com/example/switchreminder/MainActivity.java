package com.example.switchreminder;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

public class MainActivity extends AppCompatActivity {

    private EditText inputMaxDbm, inputMinDbm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        inputMaxDbm = findViewById(R.id.inputMaxDbm);
        inputMinDbm = findViewById(R.id.inputMinDbm);
        Button btnSaveConfig = findViewById(R.id.btnSaveConfig);

        // Load previously saved configurations to pre-fill the input fields
        SharedPreferences sharedPref = getSharedPreferences("SwitchReminderPrefs", Context.MODE_PRIVATE);
        inputMaxDbm.setText(String.valueOf(sharedPref.getInt("MAX_DBM", -25)));
        inputMinDbm.setText(String.valueOf(sharedPref.getInt("MIN_DBM", -36)));

        // Request runtime layout notification permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            } else {
                startReminderService();
            }
        } else {
            startReminderService();
        }

        btnSaveConfig.setOnClickListener(v -> {
            try {
                int maxVal = Integer.parseInt(inputMaxDbm.getText().toString());
                int minVal = Integer.parseInt(inputMinDbm.getText().toString());

                // Guard rails check to make sure the user inputs realistic numbers
                if (maxVal < minVal) {
                    Toast.makeText(this, "Max value must be mathematically greater than Min value!", Toast.LENGTH_LONG).show();
                    return;
                }

                // Save parameters locally
                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putInt("MAX_DBM", maxVal);
                editor.putInt("MIN_DBM", minVal);
                editor.apply();

                Toast.makeText(this, "Pocket limits updated! Restarting monitor...", Toast.LENGTH_SHORT).show();

                // Restart service to reload the updated config values
                startReminderService();
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Please enter valid integers", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void startReminderService() {
        Intent serviceIntent = new Intent(this, ReminderService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }
}