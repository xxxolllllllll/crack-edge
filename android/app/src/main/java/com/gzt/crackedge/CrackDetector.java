package com.gzt.crackedge;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.channels.FileChannel;

/** TFLite owner: one 1024x1024 tile -> crack probability map. */
public final class CrackDetector implements AutoCloseable {
    private final Interpreter interpreter;
    private final int size;
    private final ByteBuffer input;
    private final ByteBuffer output;
    private final int[] pixels;

    public CrackDetector(Context context, String asset, int size, int threads) throws IOException {
        this.size = size;
        this.interpreter = new Interpreter(loadModel(context, asset),
                new Interpreter.Options().setNumThreads(threads));
        this.input = ByteBuffer.allocateDirect(size * size * 3 * 4)
                .order(ByteOrder.nativeOrder());
        this.output = ByteBuffer.allocateDirect(size * size * 2 * 4)
                .order(ByteOrder.nativeOrder());
        this.pixels = new int[size * size];
    }

    private static ByteBuffer loadModel(Context context, String asset) throws IOException {
        AssetFileDescriptor fd = context.getAssets().openFd(asset);
        try (FileInputStream is = fd.createInputStream()) {
            FileChannel ch = is.getChannel();
            return ch.map(FileChannel.MapMode.READ_ONLY,
                    fd.getStartOffset(), fd.getDeclaredLength());
        } finally {
            fd.close();
        }
    }

    /**
     * Run one tile. {@code probOut} must have length {@code size*size}.
     * The method is synchronized because TFLite Interpreter is not thread-safe.
     */
    public synchronized void predict(Bitmap tile, float[] probOut) {
        if (tile.getWidth() != size || tile.getHeight() != size) {
            throw new IllegalArgumentException("tile must be " + size + "x" + size);
        }
        if (probOut.length != size * size) {
            throw new IllegalArgumentException("probOut must be " + (size * size));
        }
        tile.getPixels(pixels, 0, size, 0, 0, size, size);

        FloatBuffer fb = input.asFloatBuffer();
        fb.rewind();
        for (int p : pixels) {
            fb.put(((p >> 16) & 0xFF) / 255f);
            fb.put(((p >> 8) & 0xFF) / 255f);
            fb.put((p & 0xFF) / 255f);
        }
        input.rewind();
        output.rewind();
        interpreter.run(input, output);

        for (int i = 0; i < probOut.length; i++) {
            int base = i * 2 * 4;
            float z0 = output.getFloat(base);
            float z1 = output.getFloat(base + 4);
            probOut[i] = 1f / (1f + (float) Math.exp(z0 - z1));
        }
    }

    public int size() {
        return size;
    }

    @Override
    public void close() {
        interpreter.close();
    }
}
