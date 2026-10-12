package com.errorrelooad.wavehome;

import android.app.Notification;
import android.app.PendingIntent;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.content.Intent;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class NotificationAccessService extends NotificationListenerService {
    public static final String ACTION_CHANGED = "com.errorrelooad.wavehome.NOTIFICATIONS_CHANGED";
    private static volatile NotificationAccessService activeService;
    private static final ConcurrentHashMap<String, Notice> notices = new ConcurrentHashMap<>();

    public static final class Notice {
        public final String key;
        public final String packageName;
        public final String title;
        public final String text;
        public final long postTime;
        private final PendingIntent contentIntent;

        Notice(String key, String packageName, String title, String text, long postTime,
               PendingIntent contentIntent) {
            this.key = key;
            this.packageName = packageName;
            this.title = title;
            this.text = text;
            this.postTime = postTime;
            this.contentIntent = contentIntent;
        }

        public void open() {
            try {
                if (contentIntent != null) contentIntent.send();
            } catch (PendingIntent.CanceledException ignored) {
            }
        }
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        activeService = this;
        notices.clear();
        StatusBarNotification[] active = getActiveNotifications();
        if (active != null) {
            for (StatusBarNotification item : active) add(item);
        }
        notifyUi();
    }

    @Override
    public void onListenerDisconnected() {
        activeService = null;
        super.onListenerDisconnected();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        add(sbn);
        notifyUi();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        notices.remove(sbn.getKey());
        notifyUi();
    }

    private void add(StatusBarNotification sbn) {
        Notification notification = sbn.getNotification();
        Bundle extras = notification.extras;
        CharSequence rawTitle = extras == null ? null : extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence rawText = extras == null ? null : extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        if (rawText == null && extras != null) rawText = extras.getCharSequence(Notification.EXTRA_TEXT);
        String title = rawTitle == null ? "" : rawTitle.toString();
        String text = rawText == null ? "" : rawText.toString();
        notices.put(sbn.getKey(), new Notice(sbn.getKey(), sbn.getPackageName(),
                title, text, sbn.getPostTime(), notification.contentIntent));
    }

    private void notifyUi() {
        Intent intent = new Intent(ACTION_CHANGED);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

    public static List<Notice> snapshot() {
        ArrayList<Notice> result = new ArrayList<>(notices.values());
        Collections.sort(result, new Comparator<Notice>() {
            @Override public int compare(Notice a, Notice b) {
                return Long.compare(b.postTime, a.postTime);
            }
        });
        return result;
    }

    public static void dismiss(String key) {
        NotificationAccessService service = activeService;
        if (service != null) service.cancelNotification(key);
    }

    public static void clearAll() {
        NotificationAccessService service = activeService;
        if (service != null) {
            service.cancelAllNotifications();
            notices.clear();
            service.notifyUi();
        }
    }
}
