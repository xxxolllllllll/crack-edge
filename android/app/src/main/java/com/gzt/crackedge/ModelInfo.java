package com.gzt.crackedge;

import android.content.Context;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Locale;

/** Metadata for the deployed model: identity, input shape and format. */
public final class ModelInfo {
    public final String asset;
    public final String format;
    public final String source;
    public final int inputSize;
    private volatile String sha256 = "";

    public ModelInfo(String asset, String format, String source, int inputSize) {
        this.asset = asset;
        this.format = format;
        this.source = source;
        this.inputSize = inputSize;
    }

    public void setSha256(String value) {
        this.sha256 = value == null ? "" : value;
    }

    public String sha8() {
        String h = sha256;
        return h.length() >= 8 ? h.substring(0, 8) : h;
    }

    public String summary() {
        String h = sha8();
        return String.format(Locale.US,
                "模型 %s | 输入 %d×%d | %s%s",
                asset, inputSize, inputSize, format,
                h.isEmpty() ? "" : " | sha " + h);
    }

    public static String sha256(Context context, String asset) throws IOException {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw new IOException("SHA-256 unavailable", e);
        }
        byte[] buf = new byte[64 * 1024];
        try (InputStream is = context.getAssets().open(asset)) {
            int n;
            while ((n = is.read(buf)) > 0) md.update(buf, 0, n);
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format(Locale.US, "%02x", b));
        return sb.toString();
    }
}
