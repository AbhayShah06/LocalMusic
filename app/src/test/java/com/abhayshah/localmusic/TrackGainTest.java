package com.abhayshah.localmusic;

import org.junit.Test;
import static org.junit.Assert.*;

public class TrackGainTest {
    @Test public void trackAttenuationPreservesQuietAndLoudPassageRatio() {
        float gain = TrackGain.fromReplayGain("-6.0206 dB");
        assertEquals(.5f, gain, .00001f);
        double quiet = .05 * gain, chorus = .8 * gain;
        assertEquals(16, chorus / quiet, .00001);
    }
    @Test public void noBoostOrPeakDependentLimiting() {
        assertEquals(1f, TrackGain.fromReplayGain("+8.7 dB"), 0f);
        assertEquals(1f, TrackGain.fromReplayGain("0"), 0f);
        assertTrue(TrackGain.fromReplayGain("-12") < 1f);
    }
    @Test public void missingAndMalformedTagsLeaveAudioUnchanged() {
        for (String value : new String[]{null, "", "NaN", "Infinity", "-Infinity", "-100", "100", "oops", "6 dB garbage"})
            assertEquals(String.valueOf(value), 1f, TrackGain.fromReplayGain(value), 0f);
        assertEquals(.501187f, TrackGain.fromReplayGain("  -6 DB  "), .00001f);
    }
}
