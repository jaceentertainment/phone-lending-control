package com.jace.phonelending.consumer;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SessionStore sessions;
    private PairingManager pairing;
    private LinearLayout root;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sessions = new SessionStore(this);
        pairing = new PairingManager(this);
        ConsumerService.start(this);
        requestNotificationPermissionIfNeeded();
        render();
        handler.post(uiTick);
    }

    @Override protected void onResume() { super.onResume(); render(); }
    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy(); }

    private final Runnable uiTick = new Runnable() {
        @Override public void run() {
            sessions.reconcileMaintenance();
            sessions.expireIfNeeded();
            render();
            handler.postDelayed(this, 1000L);
        }
    };

    void render() {
        if (!SoftLockOverlay.hasPermission(this)) {
            renderSetupRequired();
            return;
        }

        sessions.initializeDevelopment();
        sessions.expireIfNeeded();

        if (!pairing.isPaired()) {
            renderPairing();
            return;
        }

        String state = sessions.getState();
        if (SessionStore.ACTIVE.equals(state)) renderActive();
        else if (SessionStore.AVAILABLE_LOCKED.equals(state)) renderReady();
        else if (SessionStore.EXPIRED_LOCKED.equals(state)) renderExpired();
        else renderNeedsAttention();
    }

    private void renderSetupRequired() {
        page();
        title("PhoneLending Rental");
        warning("SETUP REQUIRED");
        body("Allow PhoneLending to display the development rental lock over other apps.");
        button("ALLOW LOCK SCREEN", v -> {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
        });
        labelValue("Device", pairing.getDeviceId());
        developmentFooter();
    }

    private void renderPairing() {
        page();
        title("PhoneLending Rental");
        warning("PAIR WITH HOST");

        int port = ConsumerService.advertisedPort();
        String service = ConsumerService.advertisedServiceName();
        if (port <= 0 || service.isEmpty()) {
            section("Preparing pairing...");
            body("Keep both phones on the same local network. The QR code will appear automatically.");
        } else {
            List<String> hints = NetworkUtils.localEndpointHints(port);
            String payload = pairing.buildPairingUri(service, hints);
            try {
                ImageView qr = new ImageView(this);
                int size = dp(280);
                qr.setImageBitmap(QrCodeRenderer.render(payload, size));
                qr.setAdjustViewBounds(true);
                root.addView(qr, new LinearLayout.LayoutParams(size, size));
            } catch (Exception e) {
                warning("QR generation failed");
            }
            body("Scan this QR using PhoneLending Host.");
            labelValue("QR expires in", formatDuration(pairing.pairingSessionRemainingSeconds()));
        }
        labelValue("Device", pairing.getDeviceId());
        body("Waiting for Host...");
        developmentFooter();
    }

    private void renderReady() {
        page();
        title("PhoneLending Rental");
        success("READY");
        bigTime("00:00:00");
        body("This phone is ready for the operator to start a rental.");
        labelValue("Device", pairing.getDeviceId());
        labelValue("Host", "CONNECTED");
        developmentFooter();
    }

    private void renderActive() {
        page();
        title("PhoneLending Rental");
        success("RENTAL ACTIVE");
        bigTime(formatDuration(sessions.remainingSeconds()));
        body("remaining");
        button("RETURN TO PHONE", v -> finish());
        labelValue("Device", pairing.getDeviceId());
        developmentFooter();
    }

    private void renderExpired() {
        page();
        title("PhoneLending Rental");
        warning("RENTAL TIME EXPIRED");
        bigTime("00:00:00");
        body("Please return this phone to the rental operator.");
        labelValue("Device", pairing.getDeviceId());
        developmentFooter();
    }

    private void renderNeedsAttention() {
        page();
        title("PhoneLending Rental");
        warning("NEEDS OWNER ATTENTION");
        body("Rental access is unavailable. Please return this phone to the operator.");
        labelValue("Device", pairing.getDeviceId());
        developmentFooter();
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7001);
    }

    private void developmentFooter() {
        TextView footer = text("DEVELOPMENT BUILD  ·  Build " + versionName(), 13, Typeface.BOLD);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(28), 0, dp(10));
        root.addView(footer, matchWrap());
    }

    private void page() {
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(28), dp(22), dp(28));
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        scroll.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
    }

    private void title(String value) { TextView t=text(value,24,Typeface.BOLD); t.setGravity(Gravity.CENTER); t.setPadding(0,0,0,dp(14)); root.addView(t); }
    private void section(String value) { TextView t=text(value,16,Typeface.BOLD); t.setGravity(Gravity.CENTER); t.setPadding(0,dp(8),0,dp(8)); root.addView(t); }
    private void warning(String value) { TextView t=text(value,18,Typeface.BOLD); t.setGravity(Gravity.CENTER); t.setPadding(dp(12),dp(12),dp(12),dp(12)); root.addView(t,matchWrap()); }
    private void success(String value) { TextView t=text(value,22,Typeface.BOLD); t.setGravity(Gravity.CENTER); t.setPadding(0,dp(12),0,dp(12)); root.addView(t); }
    private void bigTime(String value) { TextView t=text(value,46,Typeface.BOLD); t.setGravity(Gravity.CENTER); t.setPadding(0,dp(6),0,dp(14)); root.addView(t); }
    private void body(String value) { TextView t=text(value,16,Typeface.NORMAL); t.setGravity(Gravity.CENTER); t.setPadding(0,dp(8),0,dp(12)); root.addView(t); }
    private void labelValue(String label,String value) { TextView t=text(label+":  "+value,15,Typeface.NORMAL); t.setPadding(0,dp(5),0,dp(5)); root.addView(t,matchWrap()); }
    private Button button(String label,android.view.View.OnClickListener listener) { Button b=new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(15); b.setOnClickListener(listener); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(52)); lp.setMargins(0,dp(7),0,dp(7)); root.addView(b,lp); return b; }
    private TextView text(String value,int sizeSp,int style) { TextView t=new TextView(this); t.setText(value); t.setTextSize(sizeSp); t.setTextColor(0xFF111111); t.setTypeface(Typeface.DEFAULT,style); return t; }
    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT); }
    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
    private static String formatDuration(long totalSeconds) { long h=totalSeconds/3600,m=(totalSeconds%3600)/60,s=totalSeconds%60; return String.format(Locale.US,"%02d:%02d:%02d",h,m,s); }
    private String versionName() { try { return getPackageManager().getPackageInfo(getPackageName(),0).versionName; } catch(Exception e) { return "unknown"; } }
}
