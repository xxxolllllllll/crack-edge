package com.gzt.crackedge;

/** Central configuration for the app. */
public final class AppConfig {
    private AppConfig() { }

    public static final String TAG = "CrackEdge";

    public static final int SIZE = 1024;
    public static final int STRIDE = 512;
    public static final int DISPLAY_MAX = 1600;

    public static final int MAX_PENDING = 3;
    public static final int MAX_HISTORY = 10;

    public static final String MODEL_ASSET = "yolo11n_fold0_1024_fp16.tflite";
    public static final String MODEL_FORMAT = "FP16";
    public static final String MODEL_SOURCE = "YOLOv11n-U-Net fold0 (distilled, 5.18M)";
    public static final int MODEL_INPUT_SIZE = 1024;

    public static final float DEFAULT_THRESHOLD = 0.50f;
    public static final int DEFAULT_THRESHOLD_PROGRESS = 45; // 0.05 + 45 * 0.01
}
