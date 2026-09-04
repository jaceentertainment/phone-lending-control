package com.jace.phonelending.consumer;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class SoftLockOverlay {
    private final Context context;
    private final SessionStore sessions;
    private final WindowManager windowManager;

    private View overlayView;
    private String shownSignature = "";
    private int tapCount = 0;
    private long lastTapElapsed = 0L;

    public SoftLockOverlay(Context context, SessionStore sessions) {
        this.context = context.getApplicationContext();
        this.sessions = sessions;
        this.windowManager = (WindowManager) this.context.getSystemService(Context.WINDOW_SERVICE);
    }

    public static boolean hasPermission(Context context) {
        return Settings.canDrawOverlays(context);
    }

    public synchronized boolean isShowing() {
        return overlayView != null;
    }

    public synchronized void show(String state, String deviceId) {
        if (!hasPermission(context) || windowManager == null || !sessions.softLockLeaseActive()) {
            hide();
            return;
        }

        String signature = state + "|" + deviceId;
        if (overlayView != null && signature.equals(shownSignature)) return;
        hide();

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);
        root.setPadding(dp(28), dp(36), dp(28), dp(36));
        root.setBackgroundColor(0xFFF7F7F7);

        TextView title = text("PhoneLending Rental", 28, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(dp(12), dp(18), dp(12), dp(18));
        title.setOnClickListener(v -> hiddenTap());
        root.addView(title, matchWrap());

        TextView status;
        TextView message;
        if (SessionStore.AVAILABLE_LOCKED.equals(state)) {
            status = text("READY", 24, Typeface.BOLD);
            message = text("This phone is waiting for the operator to start a rental.", 17, Typeface.NORMAL);
        } else if (SessionStore.EXPIRED_LOCKED.equals(state)) {
            status = text("RENTAL TIME EXPIRED", 24, Typeface.BOLD);
            message = text("Please return this phone to the rental operator.", 17, Typeface.NORMAL);
        } else {
            status = text("NEEDS OWNER ATTENTION", 22, Typeface.BOLD);
            message = text("Rental access is temporarily unavailable. Please return this phone to the operator.", 17, Typeface.NORMAL);
        }
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(8), dp(28), dp(8), dp(16));
        root.addView(status, matchWrap());

        TextView zero = text("00:00:00", 46, Typeface.BOLD);
        zero.setGravity(Gravity.CENTER);
        zero.setPadding(dp(8), dp(4), dp(8), dp(18));
        root.addView(zero, matchWrap());

        message.setGravity(Gravity.CENTER);
        message.setPadding(dp(8), dp(8), dp(8), dp(24));
        root.addView(message, matchWrap());

        TextView device = text("Device: " + deviceId, 14, Typeface.NORMAL);
        device.setGravity(Gravity.CENTER);
        device.setPadding(dp(8), dp(18), dp(8), dp(8));
        root.addView(device, matchWrap());

        TextView footer = text("DEVELOPMENT BUILD", 13, Typeface.BOLD);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(dp(8), dp(20), dp(8), dp(8));
        root.addView(footer, matchWrap());

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.OPAQUE);
        params.gravity = Gravity.TOP | Gravity.START;

        try {
            windowManager.addView(root, params);
            overlayView = root;
            shownSignature = signature;
        } catch (Throwable ignored) {
            overlayView = null;
            shownSignature = "";
        }
    }

    public synchronized void hide() {
        if (overlayView != null && windowManager != null) {
            try { windowManager.removeViewImmediate(overlayView); } catch (Throwable ignored) {}
        }
        overlayView = null;
        shownSignature = "";
        tapCount = 0;
        lastTapElapsed = 0L;
    }

    private synchronized void hiddenTap() {
        long now = SystemClock.elapsedRealtime();
        if (lastTapElapsed == 0L || now - lastTapElapsed > 3000L) tapCount = 0;
        lastTapElapsed = now;
        tapCount++;
        if (tapCount >= 7) {
            sessions.releaseSoftLockLease();
            hide();
        }
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
