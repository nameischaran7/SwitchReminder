package com.example.switchreminder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import androidx.core.app.NotificationCompat;

public class ReminderService extends Service {

    private static final String PERSISTENT_CHANNEL_ID = "reminder_service_channel";
    private static final String ALERT_CHANNEL_ID = "power_alert_channel";
    private static final int PERSISTENT_NOTIFICATION_ID = 1;
    private static final int ALERT_NOTIFICATION_ID = 2;

    private BroadcastReceiver cableReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannels();

        // Start as Foreground Service with a persistent notification
        Notification persistentNotification = new NotificationCompat.Builder(this, PERSISTENT_CHANNEL_ID)
                .setContentTitle("Switch Reminder Active")
                .setContentText("Monitoring charging port connections...")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();

        startForeground(PERSISTENT_NOTIFICATION_ID, persistentNotification);

        // Set up the receiver to listen for physical cable plug-ins

                cableReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_POWER_CONNECTED.equals(intent.getAction())) {
                    // DEBUG TOAST: Tells us if the hardware trigger fired
                    android.widget.Toast.makeText(context, "🔌 Hardware detected cable plug-in!", android.widget.Toast.LENGTH_SHORT).show();

                    // Wait 5 seconds for hardware state to settle
                    new Handler(Looper.getMainLooper()).postDelayed(() -> checkPowerStatus(context), 5000);
                }
            }
        };

        IntentFilter filter = new IntentFilter(Intent.ACTION_POWER_CONNECTED);
        registerReceiver(cableReceiver, filter);
    }

    private void checkPowerStatus(Context context) {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, ifilter);

        if (batteryStatus != null) {
            int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            boolean isCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL);

            // DEBUG TOAST: Tells us what the phone thinks the charging state is
            android.widget.Toast.makeText(context, "Is Charging? " + isCharging, android.widget.Toast.LENGTH_SHORT).show();

            if (!isCharging) {
                sendAlertNotification(context);
            }
        } else {
            android.widget.Toast.makeText(context, "Battery status intent was NULL", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private void sendAlertNotification(Context context) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
                .setContentTitle("⚠️ Switch is OFF!")
                .setContentText("Cable detected, but no power is flowing. Turn on the switch!")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(ALERT_NOTIFICATION_ID, builder.build());
        }
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                // Channel for the persistent service notification
                NotificationChannel serviceChannel = new NotificationChannel(
                        PERSISTENT_CHANNEL_ID, "Service Status", NotificationManager.IMPORTANCE_LOW);
                manager.createNotificationChannel(serviceChannel);

                // Channel for the actual high-priority alert
                NotificationChannel alertChannel = new NotificationChannel(
                        ALERT_CHANNEL_ID, "Power Alerts", NotificationManager.IMPORTANCE_HIGH);
                manager.createNotificationChannel(alertChannel);
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // START_STICKY ensures the OS attempts to recreate the service if it gets killed due to low memory
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (cableReceiver != null) {
            unregisterReceiver(cableReceiver);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}