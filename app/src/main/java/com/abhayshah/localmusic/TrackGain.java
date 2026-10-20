package com.abhayshah.localmusic;

/** A single track-wide attenuation. Never estimates gain from live audio samples. */
final class TrackGain {
    static float fromReplayGain(String value) {
        if (value == null) return 1f;
        try {
            String number = value.trim().replaceFirst("(?i)\\s*dB$", "").trim();
            double db = Double.parseDouble(number);
            // Reject corrupt/extreme tags. No positive gain: preserve headroom without a limiter.
            if (!Double.isFinite(db) || db < -30 || db > 30) return 1f;
            return (float) Math.pow(10, Math.min(0, db) / 20);
        } catch (NumberFormatException invalid) { return 1f; }
    }
    private TrackGain() { }
}
