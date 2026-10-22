package com.gzt.crackedge;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.Debug;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Append-only CSV metrics with model/device identity. */
public final class MetricsLogger {
    private static final String HEADER =
            "time,device,android,abi,model,model_sha8,format,mode,w,h,tiles,decode_ms,infer_ms,post_ms,total_ms,crack_pct,threshold,java_heap_mb,native_heap_mb,avail_mem_mb\n";

    private final Context context;
    private final ModelInfo model;
    private final File file;
    private final SimpleDateFormat time =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

    public MetricsLogger(Context context, ModelInfo model) {
        this.context = context.getApplicationContext();
        this.model = model;
        File dir = this.context.getExternalFilesDir(null);
        this.file = new File(dir != null ? dir : this.context.getFilesDir(), "metrics.csv");
    }

    public synchronized void log(String mode, int w, int h, int tiles,
                                 long decodeMs, long inferMs, long postMs, long totalMs,
                                 float crackPct, float threshold) {
        try {
            boolean exists = file.exists();
            Runtime rt = Runtime.getRuntime();
            long javaMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
            long nativeMb = Debug.getNativeHeapAllocatedSize() / (1024 * 1024);
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            long availMb = mi.availMem / (1024 * 1024);

            String abi = Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "?";
            String device = Build.MANUFACTURER + " " + Build.MODEL;

            try (FileWriter fw = new FileWriter(file, true)) {
                if (!exists) fw.write(HEADER);
                fw.write(String.format(Locale.US,
                        "%s,\"%s\",%s,%s,%s,%s,%s,%s,%d,%d,%d,%d,%d,%d,%d,%.3f,%.2f,%d,%d,%d%n",
                        time.format(new Date()), device, Build.VERSION.RELEASE, abi,
                        model.asset, model.sha8(), model.format, mode, w, h, tiles,
                        decodeMs, inferMs, postMs, totalMs, crackPct, threshold,
                        javaMb, nativeMb, availMb));
            }
        } catch (Exception e) {
            AppLogger.w(AppConfig.TAG, "metrics write failed: " + e.getMessage());
        }
    }

    public File file() {
        return file;
    }

    public void close() {
        // FileWriter is opened per run; nothing persistent to close.
    }
}
