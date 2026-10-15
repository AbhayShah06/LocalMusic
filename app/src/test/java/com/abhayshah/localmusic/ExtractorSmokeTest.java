package com.abhayshah.localmusic;

import org.junit.Test;
import org.junit.Ignore;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.Collections;
import java.net.HttpURLConnection;
import java.net.URL;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public final class ExtractorSmokeTest {
    @Ignore("Manual live catalogue smoke test")
    @Test public void findsAndResolvesGoingBad() throws Exception {
        NewPipe.init(new ExtractorDownloader());
        SearchInfo search = SearchInfo.getInfo(ServiceList.YouTube,
                ServiceList.YouTube.getSearchQHFactory().fromQuery("Going Bad Meek Mill Drake official audio", Collections.emptyList(), ""));
        StreamInfoItem match = null;
        for (InfoItem item : search.getRelatedItems()) if (item instanceof StreamInfoItem) { match = (StreamInfoItem) item; break; }
        assertNotNull(match);
        StreamInfo stream = StreamInfo.getInfo(ServiceList.YouTube, match.getUrl());
        assertFalse(stream.getAudioStreams().isEmpty());
        String audioUrl = stream.getAudioStreams().get(0).getContent();
        assertNotNull(audioUrl);
        HttpURLConnection audio = (HttpURLConnection) new URL(audioUrl).openConnection();
        audio.setRequestProperty("User-Agent", "LocalMusic/0.7");
        audio.setRequestProperty("Range", "bytes=0-4095");
        audio.setConnectTimeout(15_000); audio.setReadTimeout(15_000);
        int code = audio.getResponseCode();
        if (code != 200 && code != 206) throw new AssertionError("Audio URL returned " + code);
        assertFalse(audio.getInputStream().readNBytes(32).length == 0);
        audio.disconnect();
    }
}
