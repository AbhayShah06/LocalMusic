package com.abhayshah.localmusic;

/** Keep the current song in a bounded Jam snapshot, even after a long session. */
final class JamQueueWindow {
    private JamQueueWindow() { }

    static int offset(int currentIndex, int total) {
        if (total <= 200 || currentIndex < 0) return 0;
        return Math.min(Math.max(0, currentIndex - 20), total - 200);
    }
}
