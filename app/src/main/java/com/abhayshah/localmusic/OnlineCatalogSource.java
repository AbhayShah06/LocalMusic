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
import java.util.HashSet;
import java.util.Set;

/** Searches public streaming catalogues and resolves playback entirely on the phone. */
final class OnlineCatalogSource implements CatalogSource {
    static final class Resolution {
        final Track playable; final List<Track> radio;
        Resolution(Track playable, List<Track> radio) { this.playable = playable; this.radio = radio; }
    }
    private static boolean initialized;
    private final boolean avoidCleanVersions;

    OnlineCatalogSource() { this(true); }
    OnlineCatalogSource(boolean avoidCleanVersions) { this.avoidCleanVersions = avoidCleanVersions; ensureInitialized(); }

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

    private void search(StreamingService service, String query, List<Track> output, int limit) {
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
                        stream.getName(), artistFor(stream), service.getServiceInfo().getName(), artwork));
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

    private boolean isUnwantedVersion(StreamInfoItem item, String query) {
        String title = item.getName().toLowerCase(java.util.Locale.ROOT);
        String wanted = query.toLowerCase(java.util.Locale.ROOT);
        if (item.getDuration() > 15 * 60 || (item.getDuration() > 0 && item.getDuration() < 45)) return true;
        String[] variants = {"instrumental", "karaoke", "reaction", "nightcore", "slowed", "sped up", "live", "remix", "interview", "podcast", "documentary", "news", "trailer", "review", "explained"};
        for (String variant : variants) if (title.contains(variant) && !wanted.contains(variant)) return true;
        if (avoidCleanVersions && (title.contains("clean") || title.contains("radio edit")) && !wanted.contains("clean") && !wanted.contains("radio edit")) return true;
        return title.contains("music video") && !title.contains("official audio");
    }

    private static boolean isLikelySong(StreamInfoItem item, String seedArtist) {
        long duration = item.getDuration();
        if (duration > 0 && (duration < 75 || duration > 10 * 60)) return false;
        String title = item.getName().toLowerCase(java.util.Locale.ROOT);
        String uploader = item.getUploaderName().toLowerCase(java.util.Locale.ROOT);
        String artist = seedArtist.toLowerCase(java.util.Locale.ROOT).replace(" - topic", "").trim();
        boolean trustedMusicChannel = uploader.endsWith(" - topic") || uploader.contains("vevo")
                || uploader.contains("music") || uploader.contains("records")
                || uploader.contains("recordings") || uploader.contains("official");
        boolean labelledMusic = title.contains("official audio") || title.contains("audio") || title.contains("lyrics") || title.contains("visualizer");
        boolean artistMatch = artist.length() > 2 && (uploader.contains(artist) || title.contains(artist));
        // Related feeds are useful specifically because they cross artists and genres. Once
        // obvious spoken/video content and bad durations are excluded, retain normal song-length
        // results instead of narrowing the radio to the seed artist.
        boolean normalSongLength = duration >= 75 && duration <= 10 * 60;
        return trustedMusicChannel || labelledMusic || artistMatch || normalSongLength;
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
            Set<String> identities = new HashSet<>(); identities.add(identity(track.title));
            for (InfoItem item : info.getRelatedItems()) {
                if (!(item instanceof StreamInfoItem) || radio.size() >= 12) continue;
                StreamInfoItem related = (StreamInfoItem) item;
                if (isUnwantedVersion(related, track.title + " " + track.artist) || !isLikelySong(related, track.artist)) continue;
                Track candidate = unresolved(NewPipe.getService(serviceId), related); String identity = identity(candidate.title);
                if (identity.isEmpty() || tooSimilar(identity(track.title), identity) || !identities.add(identity)) continue;
                radio.add(candidate);
            }
            if (radio.size() < 6) {
                List<Track> fallback = new ArrayList<>(); String discovery = track.artist.replaceAll("(?i)\s*-\s*topic$", "").trim() + " songs";
                search(ServiceList.YouTube, discovery, fallback, 20);
                for (Track candidate : fallback) { String identity = identity(candidate.title); if (!identity.isEmpty() && !tooSimilar(identity(track.title), identity) && identities.add(identity)) radio.add(candidate); if (radio.size() >= 12) break; }
            }
            Collections.shuffle(radio);
            return new Resolution(new Track(track.stableId, Uri.parse(stream.getContent()), track.title, track.artist, track.album, track.artworkUri), radio);
        } catch (Exception error) { throw new IllegalStateException("Could not start this stream", error); }
    }

    static String identity(String title) {
        return title.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("\\([^)]*\\)|\\[[^]]*]", " ")
                .replaceAll("(?i)official|music video|video|audio|lyrics?|english translation|translation|full version|hd|4k|with|and", " ")
                .replaceAll("[^\\p{L}\\p{N}]+", " ").trim().replaceAll("\\s+", " ");
    }

    static boolean tooSimilar(String seed, String candidate) {
        if (seed.equals(candidate) || seed.contains(candidate) || candidate.contains(seed)) return true;
        Set<String> left = new HashSet<>(java.util.Arrays.asList(seed.split(" "))), right = new HashSet<>(java.util.Arrays.asList(candidate.split(" ")));
        left.removeIf(word -> word.length() < 3); right.removeIf(word -> word.length() < 3); if (left.isEmpty() || right.isEmpty()) return false;
        Set<String> overlap = new HashSet<>(left); overlap.retainAll(right);
        return overlap.size() >= 2 && overlap.size() * 1.0 / Math.min(left.size(), right.size()) >= .72;
    }

    private static Track unresolved(StreamingService service, StreamInfoItem stream) {
        String encoded = Base64.encodeToString(stream.getUrl().getBytes(StandardCharsets.UTF_8), Base64.URL_SAFE | Base64.NO_WRAP);
        Uri artwork = stream.getThumbnails().isEmpty() ? null : Uri.parse(stream.getThumbnails().get(stream.getThumbnails().size() - 1).getUrl());
        return new Track("online:" + service.getServiceId() + ":" + encoded, Uri.parse(stream.getUrl()), stream.getName(), artistFor(stream), service.getServiceInfo().getName(), artwork);
    }

    private static String artistFor(StreamInfoItem stream) {
        String title = stream.getName().trim(), uploader = stream.getUploaderName().trim();
        String[] separators = {" - ", " – ", " — "};
        for (String separator : separators) {
            int split = title.indexOf(separator);
            if (split > 0 && split <= 60) {
                String candidate = title.substring(0, split).replaceAll("(?i)^(official|audio)\\s+", "").trim();
                if (candidate.length() >= 2 && candidate.length() <= 60) return candidate;
            }
        }
        String normalized = uploader.replaceAll("(?i)\\s*-\\s*topic$", "").replaceAll("(?i)vevo$", "").trim();
        String generic = normalized.toLowerCase(java.util.Locale.ROOT);
        if (generic.endsWith(" media") || generic.endsWith(" fan") || generic.endsWith(" channel")) {
            normalized = normalized.replaceAll("(?i)\\s+(media|fan|channel)$", "").trim();
        }
        return normalized.isEmpty() ? uploader : normalized;
    }
}
