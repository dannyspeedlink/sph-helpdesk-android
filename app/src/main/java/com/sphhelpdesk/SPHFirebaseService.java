package com.sphhelpdesk;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class SPHFirebaseService extends FirebaseMessagingService {

    private static final String CHANNEL_ID   = "sph_helpdesk";
    private static final String CHANNEL_NAME = "SPH HelpDesk Notifications";
    private static final String REGISTER_URL = "https://helpdesk.uphsph.edu.ng/fcm_register.php";

    // ── New FCM token received ────────────────────────────────
    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        // Save token to shared preferences
        getSharedPreferences("sph_prefs", MODE_PRIVATE)
            .edit()
            .putString("fcm_token", token)
            .apply();
        // Register with server
        registerTokenWithServer(token);
    }

    // ── Incoming push notification ────────────────────────────
    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        String title = "SPH HelpDesk";
        String body  = "You have a new notification";
        String url   = "https://helpdesk.uphsph.edu.ng/student/dashboard.php";

        if (remoteMessage.getNotification() != null) {
            title = remoteMessage.getNotification().getTitle() != null
                ? remoteMessage.getNotification().getTitle() : title;
            body  = remoteMessage.getNotification().getBody() != null
                ? remoteMessage.getNotification().getBody() : body;
        }

        if (remoteMessage.getData().containsKey("url")) {
            url = remoteMessage.getData().get("url");
        }
        if (remoteMessage.getData().containsKey("title")) {
            title = remoteMessage.getData().get("title");
        }
        if (remoteMessage.getData().containsKey("body")) {
            body = remoteMessage.getData().get("body");
        }

        showNotification(title, body, url);
    }

    // ── Show notification ─────────────────────────────────────
    private void showNotification(String title, String body, String url) {
        NotificationManager manager = (NotificationManager)
            getSystemService(Context.NOTIFICATION_SERVICE);

        // Create channel for Android 8+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Ticket updates and support notifications");
            channel.enableVibration(true);
            manager.createNotificationChannel(channel);
        }

        // Intent to open app when notification tapped
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("url", url);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setSound(soundUri)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent);

        manager.notify((int) System.currentTimeMillis(), builder.build());
    }

    // ── Register FCM token with helpdesk server ───────────────
    public static void registerTokenWithServer(String token) {
        new Thread(() -> {
            try {
                URL url = new URL(REGISTER_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                String json = "{\"fcm_token\":\"" + token + "\",\"device_info\":\"Android\"}";
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                }

                conn.getResponseCode(); // Execute request
                conn.disconnect();
            } catch (Exception e) {
                // Silent fail — will retry on next app launch
            }
        }).start();
    }
}
