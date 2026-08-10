package io.wazo.callkeep;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;

/**
 * Invisible trampoline used as the incoming-call notification's full-screen
 * intent: its only job is to WAKE the screen when a call arrives on a locked
 * device, then disappear — leaving the lock screen showing the CallStyle
 * notification with Answer/Decline (the WhatsApp lock-screen ring behavior).
 * Launching the real app is the notification body's tap, not this.
 */
public class IncomingCallWakeActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        // Give the wake flags a beat to take effect, then vanish without a trace.
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            finish();
            overridePendingTransition(0, 0);
        }, 200);
    }
}
