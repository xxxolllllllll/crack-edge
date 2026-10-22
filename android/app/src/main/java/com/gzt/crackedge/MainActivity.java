package com.gzt.crackedge;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.ImageProxy;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Accuracy-first, capture-and-analyze crack detector.
 *
 * CameraController owns CameraX; CrackDetector owns TFLite; SlidingWindowEngine
 * is pure geometry/stitching; this class only orchestrates UI and background work.
 */
public class MainActivity extends AppCompatActivity {
    private static final String TAG = AppConfig.TAG;
    private static final int REQ_CAMERA = 1001;
    private static final int SIZE = AppConfig.SIZE;
    private static final int STRIDE = AppConfig.STRIDE;
    private static final int DISPLAY_MAX = AppConfig.DISPLAY_MAX;
    private static final int MAX_PENDING = AppConfig.MAX_PENDING;
    private static final int MAX_HISTORY = AppConfig.MAX_HISTORY;
    private static final String MODEL_ASSET = AppConfig.MODEL_ASSET;

    private PreviewView previewView;
    private ImageView resultView;
    private TextView statsView;
    private TextView thrLabel;
    private TextView modelInfoView;
    private RadioButton modeRoi;
    private RadioButton modeFull;
    private SeekBar thrBar;
    private ProgressBar progressBar;
    private Button captureBtn;
    private Button resultsBtn;
    private Button saveBtn;
    private Button retakeBtn;
    private Button prevBtn;
    private Button nextBtn;

    private CameraController cameraController;
    private CrackDetector detector;
    private Bitmap tileBitmap;
    private Canvas tileCanvas;

    private ExecutorService captureExecutor;
    private ExecutorService inferExecutor;
    private final AtomicInteger pending = new AtomicInteger(0);
    private final AtomicBoolean captureInFlight = new AtomicBoolean(false);
    private final List<Result> results = Collections.synchronizedList(new ArrayList<>());
    private volatile int resultIndex = -1;

    private ModelInfo modelInfo;
    private MetricsLogger metricsLogger;
    private volatile float threshold = AppConfig.DEFAULT_THRESHOLD;

    private static final class Result {
        final Bitmap image;
        final String summary;
        volatile boolean saved = false;
        volatile boolean saving = false;
        Result(Bitmap image, String summary) {
            this.image = image;
            this.summary = summary;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppLogger.init(this);
        CrashHandler.install(this);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        previewView = findViewById(R.id.preview);
        resultView = findViewById(R.id.resultView);
        statsView = findViewById(R.id.stats);
        thrLabel = findViewById(R.id.thrLabel);
        modelInfoView = findViewById(R.id.modelInfo);
        modeRoi = findViewById(R.id.modeRoi);
        modeFull = findViewById(R.id.modeFull);
        thrBar = findViewById(R.id.thr);
        progressBar = findViewById(R.id.progress);
        captureBtn = findViewById(R.id.captureBtn);
        resultsBtn = findViewById(R.id.resultsBtn);
        saveBtn = findViewById(R.id.saveBtn);
        retakeBtn = findViewById(R.id.retakeBtn);
        prevBtn = findViewById(R.id.prevBtn);
        nextBtn = findViewById(R.id.nextBtn);

        thrBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                threshold = 0.05f + progress * 0.01f;
                thrLabel.setText(String.format(Locale.US, "threshold: %.2f", threshold));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });

        captureExecutor = Executors.newSingleThreadExecutor();
        inferExecutor = Executors.newSingleThreadExecutor();
        tileBitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        tileCanvas = new Canvas(tileBitmap);

        modelInfo = new ModelInfo(AppConfig.MODEL_ASSET, AppConfig.MODEL_FORMAT,
                AppConfig.MODEL_SOURCE, AppConfig.MODEL_INPUT_SIZE);
        metricsLogger = new MetricsLogger(this, modelInfo);
        try {
            detector = new CrackDetector(this, MODEL_ASSET, SIZE, 4);
            AppLogger.i(TAG, "model loaded: " + MODEL_ASSET);
            modelInfoView.setText(modelInfo.summary());
            captureExecutor.execute(() -> {
                try {
                    String hash = ModelInfo.sha256(this, AppConfig.MODEL_ASSET);
                    modelInfo.setSha256(hash);
                    AppLogger.i(TAG, "model sha256: " + hash);
                    runOnUiThread(() -> modelInfoView.setText(modelInfo.summary()));
                } catch (Exception ex) {
                    AppLogger.w(TAG, "model hash failed: " + ex.getMessage());
                }
            });
        } catch (Exception e) {
            AppLogger.e(TAG, "model load failed", e);
            modelInfoView.setText("模型加载失败");
            Toast.makeText(this, "模型加载失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        captureBtn.setOnClickListener(v -> capture());
        resultsBtn.setOnClickListener(v -> showLatestResult());
        saveBtn.setOnClickListener(v -> saveResult());
        retakeBtn.setOnClickListener(v -> retake());
        prevBtn.setOnClickListener(v -> showResultAt(resultIndex - 1));
        nextBtn.setOnClickListener(v -> showResultAt(resultIndex + 1));

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
        } else {
            startCamera();
        }
    }

    private void startCamera() {
        cameraController = new CameraController(this, previewView);
        cameraController.start(e -> runOnUiThread(() ->
                Toast.makeText(this, "相机启动失败: " + e.getMessage(), Toast.LENGTH_LONG).show()));
    }

    /**
     * Fire-and-forget capture. The user can take the next photo as soon as
     * this JPEG has been decoded and queued; inference runs in the background.
     */
    private void capture() {
        if (detector == null) {
            Toast.makeText(this, "模型未加载", Toast.LENGTH_SHORT).show();
            return;
        }
        if (cameraController == null || !cameraController.isReady()) {
            Toast.makeText(this, "相机未就绪", Toast.LENGTH_SHORT).show();
            return;
        }
        if (pending.get() >= MAX_PENDING) {
            Toast.makeText(this, "后台还有检测未完成，请稍候", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!captureInFlight.compareAndSet(false, true)) return;

        pending.incrementAndGet();
        captureBtn.setEnabled(false);
        statsView.setText("正在拍摄...（待处理 " + pending.get() + "）");

        cameraController.takePicture(captureExecutor, new CameraController.CaptureCallback() {
            @Override
            public void onCaptured(@NonNull ImageProxy image) {
                Bitmap rotated;
                long decodeMs;
                long tDecode = SystemClock.elapsedRealtime();
                try {
                    int rotation = image.getImageInfo().getRotationDegrees();
                    ByteBuffer buf = image.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[buf.remaining()];
                    buf.get(bytes);

                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
                    opts.inMutable = true;
                    opts.inSampleSize = 1;
                    Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
                    if (bmp == null) throw new IOException("JPEG decode failed");

                    rotated = rotate(bmp, rotation);
                    if (rotated != bmp) bmp.recycle();
                    decodeMs = SystemClock.elapsedRealtime() - tDecode;
                } catch (Exception e) {
                    AppLogger.e(TAG, "capture decode failed", e);
                    pending.decrementAndGet();
                    captureInFlight.set(false);
                    finishWithError("拍摄失败: " + e.getMessage());
                    return;
                } finally {
                    image.close();
                }

                captureInFlight.set(false);
                final long decodeFinal = decodeMs;
                try {
                    inferExecutor.execute(() -> detect(rotated, decodeFinal));
                } catch (Exception e) {
                    pending.decrementAndGet();
                    finishWithError("检测排队失败: " + e.getMessage());
                    return;
                }
                runOnUiThread(() -> {
                    captureBtn.setEnabled(true);
                    statsView.setText("已加入检测队列（待处理 " + pending.get() + "）");
                });
            }

            @Override
            public void onError(@NonNull Exception e) {
                AppLogger.e(TAG, "capture error", e);
                pending.decrementAndGet();
                captureInFlight.set(false);
                finishWithError("拍摄失败: " + e.getMessage());
            }
        });
    }

    private Bitmap rotate(Bitmap src, int degrees) {
        if (degrees == 0) return src;
        Matrix m = new Matrix();
        m.postRotate(degrees);
        return Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, true);
    }

    private void detect(Bitmap image, long decodeMs) {
        long t0 = SystemClock.elapsedRealtime();
        Result r;
        try {
            r = modeRoi.isChecked()
                    ? detectRoi(image, t0, decodeMs)
                    : detectFull(image, t0, decodeMs);
        } catch (Throwable e) {
            AppLogger.e(TAG, "detect failed", e);
            pending.decrementAndGet();
            finishWithError("检测失败: " + e.getMessage());
            return;
        }
        pending.decrementAndGet();
        try {
            addResult(r);
        } catch (Throwable e) {
            AppLogger.e(TAG, "add result failed", e);
        }
    }

    private Result detectRoi(Bitmap image, long t0, long decodeMs) {
        int w = image.getWidth(), h = image.getHeight();
        int side = Math.min(SIZE, Math.min(w, h));
        int left = (w - side) / 2;
        int top = (h - side) / 2;
        Bitmap crop = Bitmap.createBitmap(image, left, top, side, side);
        if (side != SIZE) {
            Bitmap scaled = Bitmap.createScaledBitmap(crop, SIZE, SIZE, true);
            if (scaled != crop) crop.recycle();
            crop = scaled;
        }

        tileCanvas.drawColor(Color.BLACK);
        tileCanvas.drawBitmap(crop, 0, 0, null);

        long ti = SystemClock.elapsedRealtime();
        float[] prob = new float[SIZE * SIZE];
        detector.predict(tileBitmap, prob);
        long infer = SystemClock.elapsedRealtime() - ti;

        int crack = 0;
        for (int i = 0; i < prob.length; i++) {
            if (prob[i] >= threshold) crack++;
        }
        float ratio = 100f * crack / prob.length;

        long tPost = SystemClock.elapsedRealtime();
        Bitmap composite = composite(crop, prob, SIZE, SIZE);
        long post = SystemClock.elapsedRealtime() - tPost;
        long total = decodeMs + (SystemClock.elapsedRealtime() - t0);

        metricsLogger.log("ROI", w, h, 1, decodeMs, infer, post, total, ratio, threshold);
        String summary = String.format(Locale.US,
                "单 ROI 1024 | 推理 %.2f s | 总计 %.2f s%n裂缝像素 %.2f%% | 阈值 %.2f",
                infer / 1000f, total / 1000f, ratio, threshold);
        return new Result(composite, summary);
    }

    private Result detectFull(Bitmap image, long t0, long decodeMs) {
        int w = image.getWidth(), h = image.getHeight();
        float[] prob = new float[w * h];
        byte[] count = new byte[w * h];
        int[] xs = SlidingWindowEngine.starts(w, SIZE, STRIDE);
        int[] ys = SlidingWindowEngine.starts(h, SIZE, STRIDE);
        int totalTiles = xs.length * ys.length;
        float[] tileProb = new float[SIZE * SIZE];
        long inferTotal = 0;

        runOnUiThread(() -> {
            progressBar.setVisibility(ProgressBar.VISIBLE);
            progressBar.setMax(totalTiles);
            progressBar.setProgress(0);
            statsView.setText("整图滑窗: 0/" + totalTiles);
        });

        int done = 0;
        for (int y : ys) {
            for (int x : xs) {
                tileCanvas.drawColor(Color.BLACK);
                tileCanvas.drawBitmap(image, -x, -y, null);

                long ti = SystemClock.elapsedRealtime();
                detector.predict(tileBitmap, tileProb);
                inferTotal += SystemClock.elapsedRealtime() - ti;

                int cw = Math.min(SIZE, w - x);
                int ch = Math.min(SIZE, h - y);
                SlidingWindowEngine.accumulate(prob, count, tileProb,
                        SIZE, SIZE, w, x, y, cw, ch);

                done++;
                final int d = done;
                final long inferSoFar = inferTotal;
                runOnUiThread(() -> {
                    progressBar.setProgress(d);
                    statsView.setText(String.format(Locale.US,
                            "整图滑窗: %d/%d | 推理 %.1f s", d, totalTiles, inferSoFar / 1000f));
                });
            }
        }

        long tPost = SystemClock.elapsedRealtime();
        SlidingWindowEngine.average(prob, count);
        int crack = 0;
        for (int i = 0; i < prob.length; i++) {
            if (prob[i] >= threshold) crack++;
        }
        float ratio = 100f * crack / prob.length;
        Bitmap composite = composite(image, prob, w, h);
        long post = SystemClock.elapsedRealtime() - tPost;
        long total = decodeMs + (SystemClock.elapsedRealtime() - t0);

        metricsLogger.log("FULL", w, h, totalTiles, decodeMs, inferTotal, post, total, ratio, threshold);
        String summary = String.format(Locale.US,
                "整图滑窗 1024/%d | %d tiles | 推理 %.1f s | 总计 %.1f s%n裂缝像素 %.2f%% | 阈值 %.2f",
                STRIDE, totalTiles, inferTotal / 1000f, total / 1000f, ratio, threshold);
        runOnUiThread(() -> progressBar.setVisibility(ProgressBar.GONE));
        return new Result(composite, summary);
    }

    private Bitmap composite(Bitmap src, float[] prob, int probW, int probH) {
        int bw = src.getWidth(), bh = src.getHeight();
        if (Math.max(bw, bh) > DISPLAY_MAX) {
            float s = DISPLAY_MAX / (float) Math.max(bw, bh);
            bw = Math.max(1, Math.round(src.getWidth() * s));
            bh = Math.max(1, Math.round(src.getHeight() * s));
        }
        Bitmap base = Bitmap.createScaledBitmap(src, bw, bh, true);
        Bitmap overlay = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        int[] px = new int[bw * bh];
        int red = Color.argb(150, 255, 40, 40);
        for (int y = 0; y < bh; y++) {
            int sy = (int) ((long) y * probH / bh);
            int srow = sy * probW;
            int drow = y * bw;
            for (int x = 0; x < bw; x++) {
                int sx = (int) ((long) x * probW / bw);
                if (prob[srow + sx] >= threshold) px[drow + x] = red;
            }
        }
        overlay.setPixels(px, 0, bw, 0, 0, bw, bh);
        Bitmap out = base.copy(Bitmap.Config.ARGB_8888, true);
        new Canvas(out).drawBitmap(overlay, 0, 0, null);
        if (base != src) base.recycle();
        overlay.recycle();
        return out;
    }

    private void addResult(Result r) {
        final int count;
        final int removed;
        synchronized (results) {
            results.add(r);
            int drop = Math.max(0, results.size() - MAX_HISTORY);
            for (int i = 0; i < drop; i++) results.remove(0);
            removed = drop;
            count = results.size();
        }
        runOnUiThread(() -> {
            if (removed > 0 && resultIndex >= 0) {
                resultIndex = Math.max(0, resultIndex - removed);
            }
            resultsBtn.setText("本次历史 (" + count + ")");
            resultsBtn.setEnabled(true);
            if (resultView.getVisibility() != ImageView.VISIBLE) {
                statsView.setText("检测完成（待处理 " + pending.get() + "），点击“查看结果”");
                Toast.makeText(this, "检测完成", Toast.LENGTH_SHORT).show();
            } else {
                showResultAt(resultIndex);
            }
        });
    }

    private void showLatestResult() {
        int n;
        synchronized (results) {
            n = results.size();
        }
        if (n == 0) {
            Toast.makeText(this, "还没有检测结果", Toast.LENGTH_SHORT).show();
            return;
        }
        showResultAt(n - 1);
    }

    private void showResultAt(int idx) {
        synchronized (results) {
            if (results.isEmpty()) return;
            if (idx < 0) idx = 0;
            if (idx >= results.size()) idx = results.size() - 1;
            resultIndex = idx;
            Result r = results.get(idx);
            resultView.setImageBitmap(r.image);
            resultView.setVisibility(ImageView.VISIBLE);
            previewView.setVisibility(ImageView.INVISIBLE);
            statsView.setText(r.summary);
            saveBtn.setText(r.saved ? "已保存" : (r.saving ? "保存中..." : "保存结果"));
            saveBtn.setEnabled(!r.saved && !r.saving);
            retakeBtn.setEnabled(true);
            captureBtn.setEnabled(false);
            prevBtn.setEnabled(idx > 0);
            nextBtn.setEnabled(idx < results.size() - 1);
            resultsBtn.setText("本次历史 (" + results.size() + ")");
        }
    }

    private void finishWithError(String message) {
        runOnUiThread(() -> {
            progressBar.setVisibility(ProgressBar.GONE);
            statsView.setText(message);
            captureBtn.setEnabled(true);
        });
    }

    private void saveResult() {
        Result r;
        synchronized (results) {
            if (resultIndex < 0 || resultIndex >= results.size()) return;
            r = results.get(resultIndex);
        }
        synchronized (r) {
            if (r.saved) {
                Toast.makeText(this, "这张结果已经保存过", Toast.LENGTH_SHORT).show();
                return;
            }
            if (r.saving) {
                Toast.makeText(this, "正在保存，请稍候", Toast.LENGTH_SHORT).show();
                return;
            }
            r.saving = true;
        }
        saveBtn.setText("保存中...");
        saveBtn.setEnabled(false);

        captureExecutor.execute(() -> {
            boolean ok = false;
            String path = "";
            try {
                File dir = new File(getExternalFilesDir(null), "results");
                if (!dir.exists() && !dir.mkdirs()) throw new IOException("mkdir failed");
                String name = "crack_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                        .format(new Date()) + ".png";
                File f = new File(dir, name);
                try (FileOutputStream fos = new FileOutputStream(f)) {
                    r.image.compress(Bitmap.CompressFormat.PNG, 100, fos);
                }
                r.saved = true;
                ok = true;
                path = f.getAbsolutePath();
            } catch (Exception e) {
                AppLogger.w(TAG, "save failed", e);
            } finally {
                r.saving = false;
            }
            final boolean savedOk = ok;
            final String savedPath = path;
            runOnUiThread(() -> {
                if (isShowing(r)) {
                    saveBtn.setText(r.saved ? "已保存" : "保存结果");
                    saveBtn.setEnabled(!r.saved && !r.saving);
                }
                Toast.makeText(this,
                        savedOk ? "已保存: " + savedPath : "保存失败",
                        Toast.LENGTH_LONG).show();
            });
        });
    }

    private boolean isShowing(Result r) {
        synchronized (results) {
            return resultIndex >= 0 && resultIndex < results.size()
                    && results.get(resultIndex) == r;
        }
    }

    private void retake() {
        resultView.setVisibility(ImageView.GONE);
        previewView.setVisibility(ImageView.VISIBLE);
        saveBtn.setText("保存结果");
        saveBtn.setEnabled(false);
        retakeBtn.setEnabled(false);
        prevBtn.setEnabled(false);
        nextBtn.setEnabled(false);
        captureBtn.setEnabled(true);
        statsView.setText("对准裂缝后点击“拍照检测”");
        resultIndex = -1;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAMERA && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            Toast.makeText(this, "需要相机权限", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        AppLogger.i(TAG, "onDestroy");
        if (cameraController != null) cameraController.shutdown();
        if (captureExecutor != null) captureExecutor.shutdown();
        if (inferExecutor != null) inferExecutor.shutdown();
        if (metricsLogger != null) metricsLogger.close();
        if (detector != null) detector.close();
    }
}
