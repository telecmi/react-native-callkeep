package io.wazo.callkeep;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.ViewGroup;

import com.facebook.react.ReactApplication;
import com.facebook.react.ReactHost;
import com.facebook.react.ReactNativeHost;
import com.facebook.react.ReactRootView;
import com.facebook.react.interfaces.fabric.ReactSurface;

/**
 * Hosts the SDK's React-rendered in-call screen ('ConnleInCallShell') inside
 * a plain Activity shown over the keyguard. The app's own activity cannot
 * appear above the lock screen, but the React runtime is process-wide — a
 * second surface attached to an over-keyguard activity renders full video +
 * call controls there.
 *
 * Kept in its own class so IncomingCallActivity never triggers loading
 * bridgeless classes (ReactHost exists only on RN >= 0.73); every entry point
 * is called under catch(Throwable), so consumers on older RN fall back to the
 * native timer shell.
 */
final class InCallReactSurface {

    private static final String TAG = "RNCallKeep";
    private static final String APP_KEY = "ConnleInCallShell";

    private ReactSurface surface;   // bridgeless
    private ReactHost host;
    private ReactRootView rootView; // legacy bridge
    private final Handler claimHandler = new Handler(Looper.getMainLooper());

    private InCallReactSurface() { }

    /** Claim the React host's lifecycle for this activity — DELAYED, never
     *  immediate: a ReactActivity that is mid-transition behind us must get
     *  its own onHostPause in first (its pause asserts against the CURRENT
     *  activity; claiming before it pauses crashes the process with
     *  "Pausing an activity that is not the current activity"). After the
     *  delay the claim resumes the host so the surface's JS timers run even
     *  with no ReactActivity in the foreground. */
    private void scheduleClaim(Activity activity) {
        claimHandler.removeCallbacksAndMessages(null);
        claimHandler.postDelayed(() -> {
            try {
                if (host != null) host.onHostResume(activity);
            } catch (Throwable ignored) { }
        }, 800);
    }

    /** Attach the React in-call screen to the activity. Returns null when the
     *  React runtime isn't reachable (fallback: native timer shell). */
    static InCallReactSurface attach(Activity activity, String uuid, String name) {
        try {
            Object app = activity.getApplication();
            if (!(app instanceof ReactApplication)) return null;

            Bundle props = new Bundle();
            props.putString("uuid", uuid);
            props.putString("name", name);

            // Bridgeless (RN 0.76+ default): a ReactSurface on our activity.
            ReactHost reactHost = ((ReactApplication) app).getReactHost();
            if (reactHost != null) {
                ReactSurface s = reactHost.createSurface(activity, APP_KEY, props);
                if (s != null) {
                    ViewGroup view = s.getView();
                    if (view != null) {
                        s.start();
                        activity.setContentView(view);
                        InCallReactSurface r = new InCallReactSurface();
                        r.surface = s;
                        r.host = reactHost;
                        // Delayed lifecycle claim — see scheduleClaim.
                        r.scheduleClaim(activity);
                        Log.d(TAG, "[InCallReactSurface] bridgeless surface attached");
                        return r;
                    }
                }
            }

            // Legacy bridge fallback.
            ReactNativeHost rnHost = ((ReactApplication) app).getReactNativeHost();
            if (rnHost != null) {
                ReactRootView root = new ReactRootView(activity);
                root.startReactApplication(rnHost.getReactInstanceManager(), APP_KEY, props);
                activity.setContentView(root);
                rnHost.getReactInstanceManager().onHostResume(activity);
                InCallReactSurface r = new InCallReactSurface();
                r.rootView = root;
                Log.d(TAG, "[InCallReactSurface] legacy root view attached");
                return r;
            }
        } catch (Throwable t) {
            Log.w(TAG, "[InCallReactSurface] attach failed — native shell fallback", t);
        }
        return null;
    }

    /** Re-assert host resume (e.g. from Activity.onResume) — delayed, so a
     *  ReactActivity pausing behind us settles first. */
    void onResume(Activity activity) {
        if (host != null) scheduleClaim(activity);
    }

    void detach() {
        try { claimHandler.removeCallbacksAndMessages(null); } catch (Throwable ignored) { }
        try {
            if (surface != null) {
                surface.stop();
                surface.detach();
                surface = null;
            }
        } catch (Throwable ignored) { }
        try {
            if (rootView != null) {
                rootView.unmountReactApplication();
                rootView = null;
            }
        } catch (Throwable ignored) { }
        host = null;
    }
}
