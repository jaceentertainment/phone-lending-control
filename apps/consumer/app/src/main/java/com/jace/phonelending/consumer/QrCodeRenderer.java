package com.jace.phonelending.consumer;

import android.graphics.Bitmap;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;

import java.util.EnumMap;
import java.util.Map;

public final class QrCodeRenderer {
    private QrCodeRenderer() {}

    public static Bitmap render(String value, int sizePx) throws Exception {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.MARGIN, 1);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        BitMatrix matrix = new MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE, sizePx, sizePx, hints);
        int[] pixels = new int[sizePx * sizePx];
        for (int y = 0; y < sizePx; y++) {
            for (int x = 0; x < sizePx; x++) {
                pixels[y * sizePx + x] = matrix.get(x, y) ? 0xFF111111 : 0xFFFFFFFF;
            }
        }
        Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx);
        return bitmap;
    }
}
