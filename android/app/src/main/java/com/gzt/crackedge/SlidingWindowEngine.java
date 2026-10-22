package com.gzt.crackedge;

/** Pure sliding-window geometry and probability stitching. */
public final class SlidingWindowEngine {
    private SlidingWindowEngine() { }

    /** Window starts covering [0, length). The last window is shifted to end at length. */
    public static int[] starts(int length, int window, int stride) {
        if (length <= window) return new int[]{0};
        int full = (length - window) / stride + 1;
        int last = length - window;
        boolean extra = (full - 1) * stride != last;
        int[] out = new int[full + (extra ? 1 : 0)];
        for (int i = 0; i < full; i++) out[i] = i * stride;
        if (extra) out[full] = last;
        return out;
    }

    /**
     * Add one tile probability map into the full-image accumulator.
     * Only the valid (non-padded) region [0, validW) x [0, validH) is accumulated.
     */
    public static void accumulate(float[] dst, byte[] count, float[] tile,
                                  int tileW, int tileH, int dstW,
                                  int x, int y, int validW, int validH) {
        for (int yy = 0; yy < validH; yy++) {
            int srcRow = yy * tileW;
            int dstRow = (y + yy) * dstW + x;
            for (int xx = 0; xx < validW; xx++) {
                int si = srcRow + xx;
                int di = dstRow + xx;
                dst[di] += tile[si];
                count[di]++;
            }
        }
    }

    /** Divide accumulated probabilities by overlap counts. */
    public static void average(float[] prob, byte[] count) {
        for (int i = 0; i < prob.length; i++) {
            prob[i] /= Math.max(count[i], 1);
        }
    }
}
