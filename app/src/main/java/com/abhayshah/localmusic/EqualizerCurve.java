package com.abhayshah.localmusic;

final class EqualizerCurve {
    static final int[] FREQUENCIES = {60, 230, 910, 3600, 14000};
    static int safe(int db) { return Math.max(-6, Math.min(6, db)); }
    static int at(int frequency, int[] db) {
        if (frequency <= FREQUENCIES[0]) return safe(db[0]) * 100;
        for (int i = 1; i < FREQUENCIES.length; i++) if (frequency <= FREQUENCIES[i]) {
            double fraction = Math.log(frequency / (double) FREQUENCIES[i-1]) / Math.log(FREQUENCIES[i] / (double) FREQUENCIES[i-1]);
            return (int) Math.round(100 * (safe(db[i-1]) + fraction * (safe(db[i]) - safe(db[i-1]))));
        }
        return safe(db[4]) * 100;
    }
    static float headroom(int[] db) {
        int boost = 0;
        for (int level : db) boost = Math.max(boost, safe(level));
        return (float) Math.pow(10, -boost / 20d);
    }
    private EqualizerCurve() { }
}
