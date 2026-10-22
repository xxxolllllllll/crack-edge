package com.gzt.crackedge;

import android.content.Context;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Writes uncaught exceptions to a local file, then delegates to the default handler. */
public final class CrashHandler implements Thread.UncaughtExceptionHandler {
    private final Context context;
    private final Thread.UncaughtExceptionHandler previous;

    private CrashHandler(Context context, Thread.UncaughtExceptionHandler previous) {
        this.context = context.getApplicationContext();
        this.previous = previous;
    }

    public static void install(Context context) {
        Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(context, prev));
    }

    @Override
    public void uncaughtException(Thread thread, Throwable error) {
        try {
            File dir = context.getExternalFilesDir("logs");
            if (dir == null) dir = new File(context.getFilesDir(), "logs");
            if (dir.exists() || dir.mkdirs()) {
                String name = "crash_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                        .format(new Date()) + ".txt";
                File f = new File(dir, name);
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                pw.println("time: " + new Date());
                pw.println("thread: " + thread.getName());
                pw.println("device: " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL);
                pw.println("android: " + android.os.Build.VERSION.RELEASE);
                pw.println();
                error.printStackTrace(pw);
                pw.flush();
                try (FileWriter fw = new FileWriter(f, false)) {
                    fw.write(sw.toString());
                }
                AppLogger.e(AppConfig.TAG, "uncaught exception, crash log: " + f.getAbsolutePath(), error);
            }
        } catch (IOException ignored) {
            // never crash inside the crash handler
        }
        if (previous != null) previous.uncaughtException(thread, error);
    }
}
