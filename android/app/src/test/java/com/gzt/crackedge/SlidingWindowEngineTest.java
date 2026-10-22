package com.gzt.crackedge;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class SlidingWindowEngineTest {
    @Test
    public void singleWindowWhenImageSmallerThanWindow() {
        assertArrayEquals(new int[]{0}, SlidingWindowEngine.starts(800, 1024, 512));
        assertArrayEquals(new int[]{0}, SlidingWindowEngine.starts(1024, 1024, 512));
    }

    @Test
    public void windowsEndExactlyAtImageEdge() {
        assertArrayEquals(new int[]{0, 512, 1024, 1536, 2048, 2560, 2976},
                SlidingWindowEngine.starts(4000, 1024, 512));
    }

    @Test
    public void lastWindowIsShiftedToFit() {
        assertArrayEquals(new int[]{0, 512, 1024, 1536, 1976},
                SlidingWindowEngine.starts(3000, 1024, 512));
    }

    @Test
    public void accumulateAndAverageOverlappingTiles() {
        float[] dst = new float[9];
        byte[] count = new byte[9];
        float[] tile = new float[]{1, 2, 3, 4}; // 2x2

        // tile at (0,0): (0,0)=1 (1,0)=2 (0,1)=3 (1,1)=4
        SlidingWindowEngine.accumulate(dst, count, tile, 2, 2, 3, 0, 0, 2, 2);
        // tile at (1,0): (1,0)+=1 (2,0)=2 (1,1)+=3 (2,1)=4
        SlidingWindowEngine.accumulate(dst, count, tile, 2, 2, 3, 1, 0, 2, 2);

        SlidingWindowEngine.average(dst, count);

        assertEquals(1.0, dst[0], 1e-6); // one tile
        assertEquals(1.5, dst[1], 1e-6); // (2 + 1) / 2
        assertEquals(2.0, dst[2], 1e-6); // one tile
        assertEquals(3.0, dst[3], 1e-6); // one tile
        assertEquals(3.5, dst[4], 1e-6); // (4 + 3) / 2
        assertEquals(4.0, dst[5], 1e-6); // one tile
        assertEquals(0.0, dst[6], 1e-6); // no tile
    }

    @Test
    public void averageHandlesZeroCount() {
        float[] prob = new float[]{2f, 4f};
        byte[] count = new byte[]{0, 2};
        SlidingWindowEngine.average(prob, count);
        assertEquals(2f, prob[0], 1e-6);
        assertEquals(2f, prob[1], 1e-6);
    }
}
