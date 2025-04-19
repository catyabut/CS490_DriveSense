package com.example.cs490_drivesense;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class MyForegroundService extends Service {

    private static final String CHANNEL_ID = "DriveSenseForegroundChannel";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("DriveSense Monitoring")
                .setContentText("DriveSense is actively monitoring attentiveness.")
                .setSmallIcon(R.mipmap.ic_launcher) // Replace with your app's icon
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setSilent(true) //no sound
                .setOngoing(true) //cannot swipe away accidentally
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android 14 = API 34+
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA);
        } else {
            startForeground(1, notification);
        }

        return START_STICKY; // restart if service is killed
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Optional: Cleanup if needed
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // We are not binding
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "DriveSense Foreground Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }
}
