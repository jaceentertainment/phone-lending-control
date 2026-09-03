package com.jace.phonelending.host;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;

import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class QrScannerActivity extends ComponentActivity {
    public static final String EXTRA_PAYLOAD = "pairing_payload";
    private static final int CAMERA_REQUEST = 7101;

    private final ExecutorService analysisExecutor = Executors.newSingleThreadExecutor();
    private PreviewView previewView;
    private BarcodeScanner scanner;
    private boolean completed;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build();
        scanner = BarcodeScanning.getClient(options);
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera();
        else requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST);
    }

    private void buildUi() {
        FrameLayout frame = new FrameLayout(this);
        previewView = new PreviewView(this);
        previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        frame.addView(previewView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView hint = new TextView(this);
        hint.setText("Scan the QR code shown on the rental phone");
        hint.setTextSize(18);
        hint.setTextColor(0xFFFFFFFF);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(dp(18), dp(16), dp(18), dp(16));
        hint.setBackgroundColor(0xAA111827);
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        hp.setMargins(dp(16), dp(16), dp(16), dp(28));
        frame.addView(hint, hp);
        setContentView(frame);
    }

    private void startCamera() {
        com.google.common.util.concurrent.ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                analysis.setAnalyzer(analysisExecutor, this::analyze);

                provider.unbindAll();
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis);
            } catch (Exception e) {
                Toast.makeText(this, "Camera unavailable: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }, getMainExecutor());
    }

    private void analyze(ImageProxy proxy) {
        if (completed) { proxy.close(); return; }
        android.media.Image mediaImage = proxy.getImage();
        if (mediaImage == null) { proxy.close(); return; }
        InputImage image = InputImage.fromMediaImage(mediaImage, proxy.getImageInfo().getRotationDegrees());
        scanner.process(image)
                .addOnSuccessListener(this::handleCodes)
                .addOnCompleteListener(task -> proxy.close());
    }

    private void handleCodes(List<Barcode> codes) {
        if (completed) return;
        for (Barcode code : codes) {
            String raw = code.getRawValue();
            if (raw != null && raw.startsWith("phonelending://pair?")) {
                completed = true;
                Intent data = new Intent();
                data.putExtra(EXTRA_PAYLOAD, raw);
                setResult(RESULT_OK, data);
                finish();
                return;
            }
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) startCamera();
            else {
                Toast.makeText(this, "Camera access is required to scan a rental device QR code.", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override protected void onDestroy() {
        completed = true;
        if (scanner != null) scanner.close();
        analysisExecutor.shutdownNow();
        super.onDestroy();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
