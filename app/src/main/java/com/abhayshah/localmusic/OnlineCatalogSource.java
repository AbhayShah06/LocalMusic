package com.abhayshah.localmusic;

import android.net.Uri;
import android.util.Base64;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Searches public streaming catalogues and resolves playback entirely on the phone. */
final class OnlineCatalogSource implements CatalogSource {
    static final class Resolution {
        final Track playable; final List<Track> radio;
        Resolution(Track playable, List<Track> radio) { this.playable = playable; this.radio = radio; }
    }
    private static boolean initialized;

    OnlineCatalogSource() { ensureInitialized(); }

    private static synchronized void ensureInitialized() {
        if (!initialized) { NewPipe.init(new ExtractorDownloader()); initialized = true; }
    }

    @Override public List<Track> page(String query, int offset, int limit) {
        if (query == null || query.trim().isEmpty() || offset > 0) return Collections.emptyList();
        List<Track> tracks = new ArrayList<>();
        search(ServiceList.YouTube, query.trim(), tracks, limit);
        if (tracks.size() < Math.min(8, limit)) search(ServiceList.SoundCloud, query.trim(), tracks, limit);
        return tracks;
    }

    private static void search(StreamingService service, String query, List<Track> output, int limit) {
        try {
            String request = service == ServiceList.YouTube ? query + " official audio" : query;
            SearchInfo result = SearchInfo.getInfo(service, service.getSearchQHFactory().fromQuery(request, Collections.emptyList(), ""));
            List<StreamInfoItem> candidates = new ArrayList<>();
            for (InfoItem item : result.getRelatedItems()) if (item instanceof StreamInfoItem) candidates.add((StreamInfoItem) item);
            candidates.sort((left, right) -> Integer.compare(score(right, query), score(left, query)));
            for (StreamInfoItem stream : candidates) {
                if (output.size() >= limit) break;
                if (isUnwantedVersion(stream, query)) continue;
                String encoded = Base64.encodeToString(stream.getUrl().getBytes(StandardCharsets.UTF_8), Base64.URL_SAFE | Base64.NO_WRAP);
                Uri artwork = stream.getThumbnails().isEmpty() ? null : Uri.parse(stream.getThumbnails().get(stream.getThumbnails().size() - 1).getUrl());
                output.add(new Track("online:" + service.getServiceId() + ":" + encoded, Uri.parse(stream.getUrl()),
                        stream.getName(), stream.getUploaderName(), service.getServiceInfo().getName(), artwork));
            }
        } catch (Exception ignored) { }
    }

    private static int score(StreamInfoItem item, String query) {
        String title = item.getName().toLowerCase(java.util.Locale.ROOT);
        String uploader = item.getUploaderName().toLowerCase(java.util.Locale.ROOT);
        int score = 0;
        for (String word : query.toLowerCase(java.util.Locale.ROOT).split("\\s+")) if (word.length() > 1 && title.contains(word)) score += 8;
        if (uploader.endsWith(" - topic") || uploader.contains("vevo")) score += 55;
        if (title.contains("official audio") || title.contains("audio")) score += 40;
        if (title.contains("official music video") || title.contains("music video")) score -= 70;
        if (title.contains("clean") || title.contains("radio edit")) score -= 100;
        if (title.contains("reaction") || title.contains("interview")) score -= 100;
        return score;
    }

    private static boolean isUnwantedVersion(StreamInfoItem item, String query) {
        String title = item.getName().toLowerCase(java.util.Locale.ROOT);
        String wanted = query.toLowerCase(java.util.Locale.ROOT);
        String[] variants = {"clean", "radio edit", "instrumental", "karaoke", "reaction", "nightcore", "slowed", "sped up", "live", "remix"};
        for (String variant : variants) if (title.contains(variant) && !wanted.contains(variant)) return true;
        return title.contains("music video") && !title.contains("official audio");
    }

    Track resolve(Track track) {
        return resolveWithRadio(track).playable;
    }

    Resolution resolveWithRadio(Track track) {
        try {
            String[] pieces = track.stableId.split(":", 3);
            int serviceId = Integer.parseInt(pieces[1]);
            String pageUrl = new String(Base64.decode(pieces[2], Base64.URL_SAFE), StandardCharsets.UTF_8);
            StreamInfo info = StreamInfo.getInfo(NewPipe.getService(serviceId), pageUrl);
            AudioStream stream = info.getAudioStreams().stream().filter(AudioStream::isUrl)
                    .max(Comparator.comparingInt(AudioStream::getAverageBitrate)).orElseThrow(() -> new IllegalStateException("No playable audio stream"));
            List<Track> radio = new ArrayList<>();
            for (InfoItem item : info.getRelatedItems()) {
                if (!(item instanceof StreamInfoItem) || radio.size() >= 12) continue;
                StreamInfoItem related = (StreamInfoItem) item;
                if (isUnwantedVersion(related, track.title + " " + track.artist)) continue;
                radio.add(unresolved(NewPipe.getService(serviceId), related));
            }
            Collections.shuffle(radio);
            return new Resolution(new Track(track.stableId, Uri.parse(stream.getContent()), track.title, track.artist, track.album, track.artworkUri), radio);
        } catch (Exception error) { throw new IllegalStateException("Could not start this stream", error); }
    }

    private static Track unresolved(StreamingService service, StreamInfoItem stream) {
        String encoded = Base64.encodeToString(stream.getUrl().getBytes(StandardCharsets.UTF_8), Base64.URL_SAFE | Base64.NO_WRAP);
        Uri artwork = stream.getThumbnails().isEmpty() ? null : Uri.parse(stream.getThumbnails().get(stream.getThumbnails().size() - 1).getUrl());
        return new Track("online:" + service.getServiceId() + ":" + encoded, Uri.parse(stream.getUrl()), stream.getName(), stream.getUploaderName(), service.getServiceInfo().getName(), artwork);
    }
}
