package com.bugpostcard.android;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ColorSpace;
import android.net.Uri;
import android.os.Build;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

final class Screenshot {
    private static final int MAX_INPUT_BYTES = 16 * 1024 * 1024;
    private static final int MAX_SOURCE_DIMENSION = 16_384;
    private static final long MAX_SOURCE_PIXELS = 48_000_000;
    private static final int MAX_OUTPUT_DIMENSION = 1600;
    private static final String PREFIX = "data:image/png;base64,";
    private static final int MAX_OUTPUT_BYTES = (4_000_000 - PREFIX.length()) / 4 * 3;

    final Bitmap preview;
    private final byte[] png;

    private Screenshot(Bitmap preview, byte[] png) {
        this.preview = preview;
        this.png = png;
    }

    String dataUrl() {
        return PREFIX + Base64.encodeToString(png, Base64.NO_WRAP);
    }

    static Screenshot read(ContentResolver resolver, Uri uri) throws IOException {
        byte[] source;
        try (InputStream stream = resolver.openInputStream(uri)) {
            if (stream == null) throw new IOException("Image unavailable");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = stream.read(buffer)) != -1) {
                if (bytes.size() + count > MAX_INPUT_BYTES) throw new IOException("Image too large");
                bytes.write(buffer, 0, count);
            }
            source = bytes.toByteArray();
        }

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(source, 0, source.length, bounds);
        int longest = Math.max(bounds.outWidth, bounds.outHeight);
        if (!"image/png".equals(bounds.outMimeType) || bounds.outWidth <= 0 || bounds.outHeight <= 0
                || longest > MAX_SOURCE_DIMENSION
                || (long) bounds.outWidth * bounds.outHeight > MAX_SOURCE_PIXELS) {
            throw new IOException("Unsupported PNG dimensions or format");
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        while (longest / options.inSampleSize > MAX_OUTPUT_DIMENSION) options.inSampleSize *= 2;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        if (Build.VERSION.SDK_INT >= 26) {
            options.inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB);
        }
        Bitmap bitmap = BitmapFactory.decodeByteArray(source, 0, source.length, options);
        if (bitmap == null) throw new IOException("Image could not be decoded");
        try {
            Bitmap scaled = resize(bitmap, MAX_OUTPUT_DIMENSION);
            if (scaled != bitmap) {
                bitmap.recycle();
                bitmap = scaled;
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes)
                    || bytes.size() > MAX_OUTPUT_BYTES) {
                throw new IOException("PNG exceeds the export size limit");
            }
            Bitmap preview = resize(bitmap, 640);
            if (preview == bitmap) bitmap = null;
            return new Screenshot(preview, bytes.toByteArray());
        } finally {
            if (bitmap != null) bitmap.recycle();
        }
    }

    private static Bitmap resize(Bitmap bitmap, int maxDimension) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int longest = Math.max(width, height);
        if (longest <= maxDimension) return bitmap;
        return Bitmap.createScaledBitmap(bitmap,
            Math.max(1, width * maxDimension / longest),
            Math.max(1, height * maxDimension / longest), true);
    }
}
