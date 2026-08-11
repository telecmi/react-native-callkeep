package io.wazo.callkeep;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Person;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;

/**
 * The app's own incoming-call notification, used in SELF-MANAGED mode where
 * Android shows no system call UI at all (the WhatsApp model): a full-screen,
 * ringing, high-priority notification with Answer/Decline actions that drive
 * the Telecom connection exactly like the system screen would have.
 *
 * On Android 12+ this renders as the platform's CallStyle notification —
 * visually identical to how WhatsApp/Telegram ring.
 */
public class IncomingCallNotification {

    private static final String TAG = "RNCallKeep";
    private static final String CHANNEL_ID = "callkeep_incoming_v2";

    public static final String ACTION_NOTIFICATION_ANSWER = "io.wazo.callkeep.NOTIFICATION_ANSWER";
    public static final String ACTION_NOTIFICATION_DECLINE = "io.wazo.callkeep.NOTIFICATION_DECLINE";

    private static final String NOTIF_TAG = "connle_incoming_call";

    private static int notificationId(String uuid) {
        return uuid == null ? 0 : uuid.hashCode();
    }

    /** Remove EVERY incoming-call notification this library ever posted —
     *  they are ongoing (unswipeable), and a process death mid-ring orphans
     *  them until someone cleans up. Called before showing a new ring and at
     *  app start. */
    public static void cancelAll(Context context) {
        if (context == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        try {
            // Close ANY showing IncomingCallActivity too (no uuid = global):
            // when a new ring replaces an old one, the old full-screen UI must
            // not stay on top or its Answer button answers a dead call.
            context.sendBroadcast(new Intent(IncomingCallActivity.ACTION_CLOSE)
                    .setPackage(context.getPackageName()));
        } catch (Throwable ignored) { }
        try {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            for (android.service.notification.StatusBarNotification sbn : nm.getActiveNotifications()) {
                if (NOTIF_TAG.equals(sbn.getTag())) {
                    nm.cancel(NOTIF_TAG, sbn.getId());
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "[IncomingCallNotification] cancelAll failed", t);
        }
    }

    private static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return;

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Incoming calls", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Incoming call alerts");
        Uri ringtone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        channel.setSound(ringtone, attrs);
        channel.enableVibration(true);
        channel.setVibrationPattern(new long[]{0, 1000, 800, 1000, 800});
        // The ring must be fully visible ON the lock screen.
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(channel);
    }

    /** Wake the screen directly — no full-screen-intent permission involved.
     *  FSI is deny-by-default on several OEMs (and silently reset on uid
     *  changes); a call ring cannot depend on it. The deprecated flags remain
     *  fully functional on every OEM. */
    @SuppressWarnings("deprecation")
    private static void wakeScreen(Context context) {
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            PowerManager.WakeLock wl = pm.newWakeLock(
                    PowerManager.FULL_WAKE_LOCK
                            | PowerManager.ACQUIRE_CAUSES_WAKEUP
                            | PowerManager.ON_AFTER_RELEASE,
                    "callkeep:incoming_ring_wake");
            wl.acquire(8000);
        } catch (Throwable t) {
            Log.w(TAG, "[IncomingCallNotification] wakeScreen failed", t);
        }
    }

    public static void show(Context context, String uuid, String callerName, String number) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || uuid == null) return;
        try {
            wakeScreen(context); // screen ON regardless of FSI permission
            cancelAll(context); // one incoming ring at a time — no stacking
            ensureChannel(context);

            String display = (callerName != null && !callerName.isEmpty()) ? callerName
                    : (number != null ? number : "Incoming call");

            int piFlags = PendingIntent.FLAG_UPDATE_CURRENT
                    | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);

            // Full-screen intent: the library's own call screen (caller name,
            // Answer/Decline) — what a locked device shows, the WhatsApp
            // model. Unlocked devices get this notification as heads-up.
            Intent fsi = new Intent(context, IncomingCallActivity.class);
            fsi.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            fsi.putExtra(Constants.EXTRA_CALL_UUID, uuid);
            fsi.putExtra(IncomingCallActivity.EXTRA_NAME, display);
            PendingIntent fullScreen = PendingIntent.getActivity(
                    context, notificationId(uuid), fsi, piFlags);

            Intent launch = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
            PendingIntent openApp = null;
            if (launch != null) {
                launch.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                openApp = PendingIntent.getActivity(context, notificationId(uuid) + 3, launch, piFlags);
            }

            Intent answer = new Intent(ACTION_NOTIFICATION_ANSWER)
                    .setPackage(context.getPackageName())
                    .putExtra(Constants.EXTRA_CALL_UUID, uuid);
            PendingIntent answerPi = PendingIntent.getBroadcast(
                    context, notificationId(uuid) + 1, answer, piFlags);

            Intent decline = new Intent(ACTION_NOTIFICATION_DECLINE)
                    .setPackage(context.getPackageName())
                    .putExtra(Constants.EXTRA_CALL_UUID, uuid);
            PendingIntent declinePi = PendingIntent.getBroadcast(
                    context, notificationId(uuid) + 2, decline, piFlags);

            Notification.Builder builder = new Notification.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.sym_call_incoming)
                    .setContentTitle(display)
                    .setContentText("Incoming call")
                    .setCategory(Notification.CATEGORY_CALL)
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setOngoing(true)
                    .setAutoCancel(false);

            builder.setFullScreenIntent(fullScreen, true);
            if (openApp != null) {
                builder.setContentIntent(openApp);
            }

            if (Build.VERSION.SDK_INT >= 31) {
                Person caller = new Person.Builder().setName(display).setImportant(true).build();
                builder.setStyle(Notification.CallStyle.forIncomingCall(caller, declinePi, answerPi));
            } else {
                builder.addAction(new Notification.Action.Builder(null, "Decline", declinePi).build());
                builder.addAction(new Notification.Action.Builder(null, "Answer", answerPi).build());
            }

            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.notify(NOTIF_TAG, notificationId(uuid), builder.build());
            Log.d(TAG, "[IncomingCallNotification] shown for " + uuid);
        } catch (Throwable t) {
            Log.e(TAG, "[IncomingCallNotification] show failed", t);
        }
    }

    public static void cancel(Context context, String uuid) {
        if (uuid == null || context == null) return;
        try {
            Intent close = new Intent(IncomingCallActivity.ACTION_CLOSE)
                    .setPackage(context.getPackageName())
                    .putExtra(Constants.EXTRA_CALL_UUID, uuid);
            context.sendBroadcast(close);
        } catch (Throwable ignored) { }
        try {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.cancel(NOTIF_TAG, notificationId(uuid));
        } catch (Throwable t) {
            Log.e(TAG, "[IncomingCallNotification] cancel failed", t);
        }
    }
}
