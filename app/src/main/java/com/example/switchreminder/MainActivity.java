package com.example.switchreminder;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

public class MainActivity extends AppCompatActivity {

    private EditText inputWifiName, inputMaxDbm, inputMinDbm;
    private TextView txtLiveDbmMonitor;

    private Handler uiMonitorHandler;
    private Runnable uiMonitorRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        inputWifiName = findViewById(R.id.inputWifiName);
        inputMaxDbm = findViewById(R.id.inputMaxDbm);
        inputMinDbm = findViewById(R.id.inputMinDbm);
        txtLiveDbmMonitor = findViewById(R.id.txtLiveDbmMonitor);
        Button btnSaveConfig = findViewById(R.id.btnSaveConfig);

        // Load previously saved properties
        SharedPreferences sharedPref = getSharedPreferences("SwitchReminderPrefs", Context.MODE_PRIVATE);
        inputWifiName.setText(sharedPref.getString("TARGET_SSID", "RH-2.4G-CE1D70"));
        inputMaxDbm.setText(String.valueOf(sharedPref.getInt("MAX_DBM", -25)));
        inputMinDbm.setText(String.valueOf(sharedPref.getInt("MIN_DBM", -36)));

        // Prompt notification permission and start service background loops
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
                String targetSSID = inputWifiName.getText().toString().trim();
                int maxVal = Integer.parseInt(inputMaxDbm.getText().toString());
                int minVal = Integer.parseInt(inputMinDbm.getText().toString());

                if (targetSSID.isEmpty()) {
                    Toast.makeText(this, "Please enter a valid Wi-Fi name!", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (maxVal < minVal) {
                    Toast.makeText(this, "Max value must be greater than Min value!", Toast.LENGTH_LONG).show();
                    return;
                }

                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putString("TARGET_SSID", targetSSID);
                editor.putInt("MAX_DBM", maxVal);
                editor.putInt("MIN_DBM", minVal);
                editor.apply();

                Toast.makeText(this, "Configuration Updated Successfully!", Toast.LENGTH_SHORT).show();
                startReminderService();
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Please check your input values", Toast.LENGTH_SHORT).show();
            }
        });

        // Initialize the live screen calibrator loop
        startLiveUiCalibrationMonitor();
    }

    private void startLiveUiCalibrationMonitor() {
        uiMonitorHandler = new Handler(Looper.getMainLooper());
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        uiMonitorRunnable = new Runnable() {
            @Override
            public void run() {
                if (wifiManager != null) {
                    WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                    int currentRssi = wifiInfo.getRssi();

                    // Update the text box on screen in real time
                    txtLiveDbmMonitor.setText(currentRssi + " dBm");
                }
                // Refresh the screen indicator text every 1 second
                uiMonitorHandler.postDelayed(this, 1000);
            }
        };
        uiMonitorHandler.post(uiMonitorRunnable);
    }

    private void startReminderService() {
        Intent serviceIntent = new Intent(this, ReminderService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Prevent memory leaks when user closes the application UI layout screen
        if (uiMonitorHandler != null && uiMonitorRunnable != null) {
            uiMonitorHandler.removeCallbacks(uiMonitorRunnable);
        }
    }
}