package com.gzt.crackedge;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Logcat + rotating file logger. Never logs image content or user data. */
public final class AppLogger {
    private static final Object LOCK = new Object();
    private static final long MAX_BYTES = 1_000_000L;
    private static final int MAX_FILES = 3;

    private static File logFile;
    private static final SimpleDateFormat TIME =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);

    private AppLogger() { }

    public static void init(Context context) {
        synchronized (LOCK) {
            File dir = context.getExternalFilesDir("logs");
            if (dir == null) dir = new File(context.getFilesDir(), "logs");
            if (!dir.exists() && !dir.mkdirs()) {
                Log.w(AppConfig.TAG, "cannot create log dir: " + dir);
                return;
            }
            logFile = new File(dir, "app.log");
            i(AppConfig.TAG, "logger initialized: " + logFile.getAbsolutePath());
        }
    }

    public static void i(String tag, String msg) {
        Log.i(tag, msg);
        write("I", tag, msg);
    }

    public static void w(String tag, String msg) {
        Log.w(tag, msg);
        write("W", tag, msg);
    }

    public static void e(String tag, String msg) {
        Log.e(tag, msg);
        write("E", tag, msg);
    }

    public static void w(String tag, String msg, Throwable t) {
        Log.w(tag, msg, t);
        write("W", tag, msg + "\n" + stack(t));
    }

    public static void e(String tag, String msg, Throwable t) {
        Log.e(tag, msg, t);
        write("E", tag, msg + "\n" + stack(t));
    }

    private static String stack(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    private static void write(String level, String tag, String msg) {
        synchronized (LOCK) {
            if (logFile == null) return;
            try {
                rotateIfNeeded();
                try (FileWriter fw = new FileWriter(logFile, true)) {
                    fw.write(TIME.format(new Date()));
                    fw.write(" ");
                    fw.write(level);
                    fw.write("/");
                    fw.write(tag);
                    fw.write(": ");
                    fw.write(msg);
                    fw.write("\n");
                }
            } catch (IOException e) {
                Log.w(AppConfig.TAG, "file log failed", e);
            }
        }
    }

    private static void rotateIfNeeded() {
        if (logFile == null || logFile.length() < MAX_BYTES) return;
        File dir = logFile.getParentFile();
        if (dir == null) return;
        for (int i = MAX_FILES - 1; i >= 1; i--) {
            File src = new File(dir, "app.log." + i);
            File dst = new File(dir, "app.log." + (i + 1));
            if (src.isFile()) {
                if (dst.isFile()) dst.delete();
                src.renameTo(dst);
            }
        }
        File first = new File(dir, "app.log.1");
        if (first.isFile()) first.delete();
        logFile.renameTo(first);
    }
}
