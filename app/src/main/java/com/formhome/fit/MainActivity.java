package com.formhome.fit;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends Activity {
    private static final String CHANNEL = "workout_reminders";
    private WebView webView;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xFF10120F);
        getWindow().setNavigationBarColor(0xFF10120F);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 41);
        }
        createChannel();
        webView = new WebView(this);
        webView.setBackgroundColor(0xFF10120F);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new AppBridge(), "HomeFitNative");
        setContentView(webView);
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "홈트 알림", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("타이머 완료 및 운동 리마인더");
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private class AppBridge {
        @JavascriptInterface public void notify(String title, String message) {
            runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
                NotificationCompat.Builder b = new NotificationCompat.Builder(MainActivity.this, CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    .setContentTitle(title).setContentText(message)
                    .setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true);
                getSystemService(NotificationManager.class).notify((int)System.currentTimeMillis(), b.build());
            });
        }
        @JavascriptInterface public void setDailyReminder(boolean enabled, int hour, int minute) {
            getSharedPreferences(ReminderReceiver.PREFS, MODE_PRIVATE).edit().putBoolean("enabled", enabled).putInt("hour", hour).putInt("minute", minute).apply();
            if (enabled) ReminderReceiver.schedule(MainActivity.this, hour, minute); else ReminderReceiver.cancel(MainActivity.this);
        }
        @JavascriptInterface public void vibrate() {
            Vibrator v = (Vibrator)getSystemService(VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(180, VibrationEffect.DEFAULT_AMPLITUDE));
                else v.vibrate(180);
            }
        }
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }
}
