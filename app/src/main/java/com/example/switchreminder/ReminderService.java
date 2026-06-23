package com.example.switchreminder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.widget.Toast;
import androidx.core.app.NotificationCompat;

public class ReminderService extends Service {

    private static final String CHANNEL_ID = "precise_power_alerts";
    private static final int NOTIFICATION_ID = 501;

    // CONFIGURATION AREA
    private static final String TARGET_BSSID = "44:95:3b:ce:1d:70" +
            "";

    private Handler proximityHandler;
    private Runnable proximityCheckRunnable;
    private boolean isInsideTargetRadius = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Proximity Monitor Active")
                .setContentText("Scanning for charging station proximity...")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setOngoing(true)
                .build();
        startForeground(NOTIFICATION_ID, notification);

        startProximityTracking();
    }

    private void startProximityTracking() {
        proximityHandler = new Handler(Looper.getMainLooper());
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        proximityCheckRunnable = new Runnable() {
            @Override
            public void run() {
                if (connectivityManager != null) {
                    android.net.Network activeNetwork = connectivityManager.getActiveNetwork();
                    NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(activeNetwork);

                    if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                        if (wifiManager != null) {
                            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                            String currentBSSID = wifiInfo.getBSSID();
                            int currentRssi = wifiInfo.getRssi();

                            // Verify connection to target or generic masked framework BSSID
                            if (currentBSSID != null && (currentBSSID.equalsIgnoreCase(TARGET_BSSID) || currentBSSID.equals("02:00:00:00:00:00"))) {

                                // DYNAMIC RETRIEVAL: Pull parameters directly saved by the user interface settings
                                SharedPreferences sharedPref = getSharedPreferences("SwitchReminderPrefs", Context.MODE_PRIVATE);
                                int dynamicMax = sharedPref.getInt("MAX_DBM", -25);
                                int dynamicMin = sharedPref.getInt("MIN_DBM", -36);

                                // Apply the user-configured custom evaluation box mapping
                                if (currentRssi <= dynamicMax && currentRssi >= dynamicMin) {

                                    if (!isInsideTargetRadius) {
                                        isInsideTargetRadius = true;
                                        Toast.makeText(getApplicationContext(), "📍 Custom Desk Pocket Locked! Checking power in 1 min...", Toast.LENGTH_LONG).show();
                                        new Handler(Looper.getMainLooper()).postDelayed(() -> verifyChargingStatus(), 60000);
                                    }

                                } else {
                                    if (isInsideTargetRadius) {
                                        isInsideTargetRadius = false;
                                        Toast.makeText(getApplicationContext(), "🚶 Left the custom desk pocket radius.", Toast.LENGTH_SHORT).show();
                                    }
                                }
                            }
                        }
                    }
                }
                // Check the pocket window configuration every 3 seconds
                proximityHandler.postDelayed(this, 3000);
            }
        };

        proximityHandler.post(proximityCheckRunnable);
    }

    private void verifyChargingStatus() {
        // If the user walked away during that 1-minute buffer window, cancel the check silently
        if (!isInsideTargetRadius) return;

        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            batteryStatus = registerReceiver(null, ifilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            batteryStatus = registerReceiver(null, ifilter);
        }

        if (batteryStatus != null) {
            int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            boolean isCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL);

            if (!isCharging) {
                sendAlert();
            }
        }
    }

    private void sendAlert() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
                .setContentTitle("⚠️ Switch is Turned OFF!")
                .setContentText("Phone is at the switchboard, but not eating charge. Turn on the switch!")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(502, builder.build());
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Proximity Charger Alert", NotificationManager.IMPORTANCE_HIGH);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (proximityHandler != null) {
            proximityHandler.removeCallbacks(proximityCheckRunnable);
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}