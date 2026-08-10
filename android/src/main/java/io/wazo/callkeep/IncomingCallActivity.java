package io.wazo.callkeep;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.telecom.Connection;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Full-screen incoming-call UI for the locked device — the WhatsApp model:
 * on the keyguard the ring is a dedicated call screen, not a notification.
 * Launched by the ring notification's full-screen intent; Answer/Decline
 * drive the Telecom connection exactly like the notification actions.
 * Dismissed automatically when the call stops ringing (cancel/timeout/
 * answered elsewhere).
 */
public class IncomingCallActivity extends Activity {

    public static final String ACTION_CLOSE = "io.wazo.callkeep.INCOMING_UI_CLOSE";
    public static final String EXTRA_NAME = "displayName";

    private String uuid;
    private final BroadcastReceiver closeReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            String target = i.getStringExtra(Constants.EXTRA_CALL_UUID);
            if (target == null || target.equals(uuid)) finishNoAnim();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        uuid = getIntent().getStringExtra(Constants.EXTRA_CALL_UUID);
        String name = getIntent().getStringExtra(EXTRA_NAME);
        if (name == null || name.isEmpty()) name = "Incoming call";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // ---- programmatic layout: dark call screen, name, two round buttons
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0B1F3A"));
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText(name);
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = dp(120);
        root.addView(title, tlp);

        TextView subtitle = new TextView(this);
        subtitle.setText("Incoming video call");
        subtitle.setTextColor(Color.parseColor("#9FC1FF"));
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        slp.topMargin = dp(8);
        root.addView(subtitle, slp);

        View spacer = new View(this);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, 0, 1f);
        sp.width = LinearLayout.LayoutParams.MATCH_PARENT;
        root.addView(spacer, sp);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.bottomMargin = dp(96);
        root.addView(buttons, blp);

        buttons.addView(makeButton("Decline", "#EF4444", v -> act(false)), pill());
        View gap = new View(this);
        buttons.addView(gap, new LinearLayout.LayoutParams(dp(40), 1));
        buttons.addView(makeButton("Answer", "#22C55E", v -> act(true)), pill());

        setContentView(root);

        IntentFilter f = new IntentFilter(ACTION_CLOSE);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(closeReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(closeReceiver, f);
    }

    private LinearLayout.LayoutParams pill() {
        return new LinearLayout.LayoutParams(dp(130), dp(56));
    }

    private Button makeButton(String label, String color, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        b.setAllCaps(false);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor(color));
        bg.setCornerRadius(dp(28));
        b.setBackground(bg);
        b.setOnClickListener(l);
        return b;
    }

    private void act(boolean answer) {
        try {
            IncomingCallNotification.cancel(this, uuid);
            Connection conn = uuid == null ? null : VoiceConnectionService.getConnection(uuid);
            if (conn != null) {
                if (answer) {
                    conn.onAnswer();
                    Intent launch = getPackageManager().getLaunchIntentForPackage(getPackageName());
                    if (launch != null) {
                        launch.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        startActivity(launch);
                    }
                } else {
                    conn.onReject();
                }
            }
        } catch (Throwable ignored) { }
        finishNoAnim();
    }

    private void finishNoAnim() {
        finish();
        overridePendingTransition(0, 0);
    }

    @Override
    protected void onDestroy() {
        try { unregisterReceiver(closeReceiver); } catch (Throwable ignored) { }
        super.onDestroy();
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }
}
