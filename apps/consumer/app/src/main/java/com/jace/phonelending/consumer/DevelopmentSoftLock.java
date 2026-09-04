package com.jace.phonelending.consumer;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Early-development enforcement only. It is deliberately reversible and is NOT a production security boundary.
 * A lock lease lasts at most five minutes. Seven taps on the title releases it immediately.
 */
final class DevelopmentSoftLock {
    static final long MAX_LOCK_MS = 5L * 60L * 1000L;

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final WindowManager wm;
    private View overlay;
    private long releaseAtElapsed;
    private int tapCount;
    private long lastTapElapsed;

    DevelopmentSoftLock(Context context) {
        this.context = context.getApplicationContext();
        this.wm = (WindowManager) this.context.getSystemService(Context.WINDOW_SERVICE);
    }

    boolean isVisible() { return overlay != null; }

    void arm(String state, String deviceId) {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(context)) return;
        release();
        releaseAtElapsed = SystemClock.elapsedRealtime() + MAX_LOCK_MS;
        tapCount = 0;
        lastTapElapsed = 0L;

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(28), dp(36), dp(28), dp(36));
        root.setBackgroundColor(0xFFFFFFFF);

        TextView title = text("PhoneLending Rental", 26, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(34));
        title.setOnClickListener(v -> handleRecoveryTap());
        root.addView(title, matchWrap());

        TextView headline;
        TextView description;
        if (SessionStore.AVAILABLE_LOCKED.equals(state)) {
            headline = text("READY", 30, Typeface.BOLD);
            description = text("This phone is waiting for the operator to start a rental.", 17, Typeface.NORMAL);
        } else if (SessionStore.EXPIRED_LOCKED.equals(state)) {
            headline = text("RENTAL TIME EXPIRED", 27, Typeface.BOLD);
            description = text("Please return this phone to the rental operator.", 17, Typeface.NORMAL);
        } else {
            headline = text("DEVICE NEEDS OWNER ATTENTION", 24, Typeface.BOLD);
            description = text("Rental access is temporarily unavailable.", 17, Typeface.NORMAL);
        }
        headline.setGravity(Gravity.CENTER);
        root.addView(headline, matchWrap());

        TextView zero = text("00:00:00", 48, Typeface.BOLD);
        zero.setGravity(Gravity.CENTER);
        zero.setPadding(0, dp(18), 0, dp(18));
        root.addView(zero, matchWrap());

        description.setGravity(Gravity.CENTER);
        description.setPadding(0, 0, 0, dp(36));
        root.addView(description, matchWrap());

        TextView device = text("Device: " + deviceId, 14, Typeface.NORMAL);
        device.setGravity(Gravity.CENTER);
        root.addView(device, matchWrap());

        TextView footer = text("DEVELOPMENT BUILD", 13, Typeface.BOLD);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(26), 0, 0);
        root.addView(footer, matchWrap());

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.OPAQUE);
        lp.gravity = Gravity.TOP | Gravity.START;
        try {
            wm.addView(root, lp);
            overlay = root;
            handler.postDelayed(autoRelease, MAX_LOCK_MS);
        } catch (Throwable ignored) {
            overlay = null;
        }
    }

    void release() {
        handler.removeCallbacks(autoRelease);
        if (overlay != null) {
            try { wm.removeViewImmediate(overlay); } catch (Throwable ignored) {}
            overlay = null;
        }
        tapCount = 0;
        lastTapElapsed = 0L;
    }

    private final Runnable autoRelease = () -> {
        if (releaseAtElapsed > 0L && SystemClock.elapsedRealtime() >= releaseAtElapsed) release();
    };

    private void handleRecoveryTap() {
        long now = SystemClock.elapsedRealtime();
        if (lastTapElapsed == 0L || now - lastTapElapsed <= 1200L) tapCount++;
        else tapCount = 1;
        lastTapElapsed = now;
        if (tapCount >= 7) release();
    }

    private TextView text(String value, int sizeSp, int style) {
        TextView t = new TextView(context);
        t.setText(value);
        t.setTextSize(sizeSp);
        t.setTextColor(0xFF111111);
        t.setTypeface(Typeface.DEFAULT, style);
        return t;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
