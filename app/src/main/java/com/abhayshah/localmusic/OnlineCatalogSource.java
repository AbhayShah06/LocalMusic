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
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONArray;
import org.json.JSONObject;

/** Searches public streaming catalogues and resolves playback entirely on the phone. */
final class OnlineCatalogSource implements CatalogSource {
    private static final String CLOUD_SEARCH = "https://localmusic-jam.vercel.app/api/search?q=";
    private static final long RESOLUTION_TTL_MS = 8 * 60_000L;
    private static final long SEARCH_TTL_MS = 5 * 60_000L;
    private static final java.util.LinkedHashMap<String, CachedTrack> RESOLVED = new java.util.LinkedHashMap<String, CachedTrack>(32, .75f, true) {
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<String, CachedTrack> eldest) { return size() > 32; }
    };
    private static final java.util.LinkedHashMap<String, CachedSearch> SEARCHED = new java.util.LinkedHashMap<String, CachedSearch>(24, .75f, true) {
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<String, CachedSearch> eldest) { return size() > 24; }
    };
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
        String cacheKey = query.trim().toLowerCase(java.util.Locale.ROOT) + "|" + avoidCleanVersions;
        synchronized (SEARCHED) {
            CachedSearch cached = SEARCHED.get(cacheKey);
            if (cached != null && System.currentTimeMillis() - cached.createdAt < SEARCH_TTL_MS) return new ArrayList<>(cached.tracks.subList(0, Math.min(limit, cached.tracks.size())));
            if (cached != null) SEARCHED.remove(cacheKey);
        }
        List<Track> tracks = new ArrayList<>();
        searchCloud(query.trim(), tracks, limit);
        tracks.removeIf(track -> isUnwantedTitle(track.title, query));
        if (tracks.size() < Math.min(8, limit)) search(ServiceList.YouTube, query.trim(), tracks, limit);
        if (tracks.size() < Math.min(8, limit)) search(ServiceList.SoundCloud, query.trim(), tracks, limit);
        List<Track> unique = new ArrayList<>(); Set<String> identities = new HashSet<>();
        for (Track track : tracks) { String identity = identity(track.title) + "|" + identity(track.artist); if (!identity.trim().equals("|") && identities.add(identity)) unique.add(track); }
        synchronized (SEARCHED) { SEARCHED.put(cacheKey, new CachedSearch(new ArrayList<>(unique))); }
        return new ArrayList<>(unique.subList(0, Math.min(limit, unique.size())));
    }

    private boolean isUnwantedTitle(String value, String query) {
        String title = value.toLowerCase(java.util.Locale.ROOT), wanted = query.toLowerCase(java.util.Locale.ROOT);
        String[] spoken = {"reaction", "interview", "podcast", "documentary", "news", "trailer", "review", "explained"};
        for (String term : spoken) if (title.contains(term) && !wanted.contains(term)) return true;
        return VersionPolicy.isMusicVideo(value) || avoidCleanVersions && VersionPolicy.exclude(value,query);
    }

    /** Music-specific cloud search is faster and avoids general-video false negatives. */
    private static void searchCloud(String query, List<Track> output, int limit) {
        HttpURLConnection connection = null;
        try {
            String encoded = java.net.URLEncoder.encode(query, "UTF-8");
            connection = (HttpURLConnection)new URL(CLOUD_SEARCH + encoded).openConnection();
            connection.setConnectTimeout(5_000); connection.setReadTimeout(8_000); connection.setRequestProperty("User-Agent", "LocalMusic/0.12");
            if (connection.getResponseCode() != 200) return;
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192]; for (int read; (read = input.read(buffer)) >= 0; ) bytes.write(buffer, 0, read);
                JSONArray values = new JSONObject(bytes.toString("UTF-8")).optJSONArray("tracks");
                if (values == null) return;
                Set<String> known = new HashSet<>(); for (Track track : output) known.add(track.stableId);
                for (int i = 0; i < values.length() && output.size() < limit; i++) {
                    JSONObject item = values.optJSONObject(i); if (item == null) continue;
                    String id = bounded(item.optString("id"), 700), title = bounded(item.optString("title"), 160), artist = bounded(item.optString("artist"), 160), album = bounded(item.optString("album", "YouTube Music"), 160), artwork = bounded(item.optString("artwork"), 900);
                    if (!id.startsWith("online:") || !known.add(id)) continue;
                    output.add(new Track(id, Uri.EMPTY, title, artist, album, artwork.startsWith("https://") ? Uri.parse(artwork) : null));
                }
            }
        } catch (Exception ignored) { }
        finally { if (connection != null) connection.disconnect(); }
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
                Uri artwork = bestArtwork(stream.getThumbnails());
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
        if (VersionPolicy.exclude(item.getName(),query)) score -= 100;
        if (title.contains("reaction") || title.contains("interview")) score -= 100;
        return score;
    }

    private boolean isUnwantedVersion(StreamInfoItem item, String query) {
        String title = item.getName().toLowerCase(java.util.Locale.ROOT);
        String wanted = query.toLowerCase(java.util.Locale.ROOT);
        if (item.getDuration() > 15 * 60 || (item.getDuration() > 0 && item.getDuration() < 45)) return true;
        String[] variants = {"instrumental", "karaoke", "reaction", "nightcore", "slowed", "sped up", "live", "remix", "interview", "podcast", "documentary", "news", "trailer", "review", "explained"};
        for (String variant : variants) if (title.contains(variant) && !wanted.contains(variant)) return true;
        if (avoidCleanVersions && VersionPolicy.exclude(item.getName(),query)) return true;
        return VersionPolicy.isMusicVideo(item.getName());
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

    static void invalidate(String id) { synchronized (RESOLVED) { RESOLVED.remove(id); } }

    Track resolve(Track track) {
        synchronized (RESOLVED) {
            CachedTrack cached = RESOLVED.get(track.stableId);
            if (cached != null && System.currentTimeMillis() - cached.createdAt < RESOLUTION_TTL_MS) return cached.track;
            if (cached != null) RESOLVED.remove(track.stableId);
        }
        Track resolved;
        try {
            String[] pieces=track.stableId.split(":",3);String url=new String(Base64.decode(pieces[2],Base64.URL_SAFE),StandardCharsets.UTF_8);
            StreamInfo info=StreamInfo.getInfo(NewPipe.getService(Integer.parseInt(pieces[1])),url);
            AudioStream stream=info.getAudioStreams().stream().filter(AudioStream::isUrl).max(Comparator.comparingInt(AudioStream::getAverageBitrate)).orElseThrow(()->new IllegalStateException("No playable audio stream"));
            resolved=new Track(track.stableId,Uri.parse(stream.getContent()),track.title,track.artist,track.album,track.artworkUri);
        }catch(Exception failure){throw new IllegalStateException("Could not start this stream",failure);}
        synchronized (RESOLVED) { RESOLVED.put(track.stableId, new CachedTrack(resolved)); }
        return resolved;
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
            Uri resolvedArtwork = bestArtwork(info.getThumbnails());
            return new Resolution(new Track(track.stableId, Uri.parse(stream.getContent()), track.title, track.artist, track.album, resolvedArtwork == null ? track.artworkUri : resolvedArtwork), radio);
        } catch (Exception error) { throw new IllegalStateException("Could not start this stream", error); }
    }

    List<Track> songMix(Track seed) {return songMix(seed,java.util.Collections.emptySet());}
    List<Track> songMix(Track seed,Set<String> excludedIds) {
        List<Track> candidates=new ArrayList<>();candidates.add(seed.original());
        try {
            String[] parts=seed.stableId.split(":",3);String page=new String(Base64.decode(parts[2],Base64.URL_SAFE),StandardCharsets.UTF_8);
            if(Integer.parseInt(parts[1])==0){String video=Uri.parse(page).getQueryParameter("v");if(video!=null){
                org.schabi.newpipe.extractor.playlist.PlaylistInfo info=org.schabi.newpipe.extractor.playlist.PlaylistInfo.getInfo(ServiceList.YouTube,"https://www.youtube.com/watch?v="+video+"&list=RD"+video);
                for(StreamInfoItem item:info.getRelatedItems())if(!isUnwantedVersion(item,seed.title+" "+seed.artist)&&isLikelySong(item,seed.artist))candidates.add(unresolved(ServiceList.YouTube,item));
                org.schabi.newpipe.extractor.Page next=info.getNextPage();
                for(int pageNumber=0;pageNumber<3&&org.schabi.newpipe.extractor.Page.isValid(next)&&candidates.size()<120;pageNumber++){
                    org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage<StreamInfoItem> more=org.schabi.newpipe.extractor.playlist.PlaylistInfo.getMoreItems(ServiceList.YouTube,info.getUrl(),next);
                    for(StreamInfoItem item:more.getItems())if(!isUnwantedVersion(item,seed.title+" "+seed.artist)&&isLikelySong(item,seed.artist))candidates.add(unresolved(ServiceList.YouTube,item));next=more.getNextPage();
                }
            }}
        }catch(Exception unavailable){}
        if(candidates.size()<8)try{candidates.addAll(resolveWithRadio(seed).radio);}catch(RuntimeException unavailable){}
        List<Track> result=new ArrayList<>();Set<String> known=new HashSet<>();java.util.Map<String,Integer> artistCounts=new java.util.HashMap<>();
        for(Track candidate:candidates){
            if(excludedIds.contains(candidate.stableId)||VersionPolicy.isMusicVideo(candidate.title)||avoidCleanVersions&&VersionPolicy.exclude(candidate.title,seed.title))continue;
            String artist=RecordingIdentity.artist(candidate.artist),key=artist+"|"+RecordingIdentity.title(candidate.title,candidate.artist);
            if(artistCounts.getOrDefault(artist,0)>=3||!known.add(key))continue;
            artistCounts.put(artist,artistCounts.getOrDefault(artist,0)+1);result.add(candidate.original());if(result.size()==20)break;
        }
        return result;
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

    PlaylistImporter.Result importPlaylist(String url, PlaylistImporter.Progress progress) throws Exception {
        ensureInitialized();
        String host = new java.net.URI(url).getHost();
        if (host == null || !(host.equals("youtube.com") || host.endsWith(".youtube.com") || host.equals("youtu.be") || host.equals("soundcloud.com") || host.equals("www.soundcloud.com")))
            throw new IllegalArgumentException("Use a public YouTube or SoundCloud playlist link. For Spotify or Apple Music, import an exported CSV or paste the track names.");
        StreamingService service = NewPipe.getServiceByUrl(url);
        org.schabi.newpipe.extractor.playlist.PlaylistInfo info = org.schabi.newpipe.extractor.playlist.PlaylistInfo.getInfo(service, url);
        List<Track> tracks = new ArrayList<>(); Set<String> seen = new HashSet<>();
        List<StreamInfoItem> page = info.getRelatedItems(); org.schabi.newpipe.extractor.Page next = info.getNextPage();
        int pages = 0;
        while (true) {
            for (StreamInfoItem item : page) {
                Track track = unresolved(service, item);
                if (seen.add(track.stableId)) tracks.add(track);
                if (tracks.size() > PlaylistText.MAX_TRACKS) throw new IllegalArgumentException("Import up to 500 tracks at a time.");
            }
            if (!progress.update(tracks.size(), Math.max(tracks.size(), (int)Math.min(500, info.getStreamCount())))) throw new java.util.concurrent.CancellationException();
            if (!org.schabi.newpipe.extractor.Page.isValid(next)) break;
            if (++pages > 30) throw new IllegalArgumentException("This playlist is too large. Try a smaller playlist.");
            org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage<StreamInfoItem> more = org.schabi.newpipe.extractor.playlist.PlaylistInfo.getMoreItems(service, url, next);
            page = more.getItems(); next = more.getNextPage();
        }
        return new PlaylistImporter.Result(info.getName(), tracks, 0);
    }

    private static Track unresolved(StreamingService service, StreamInfoItem stream) {
        String encoded = Base64.encodeToString(stream.getUrl().getBytes(StandardCharsets.UTF_8), Base64.URL_SAFE | Base64.NO_WRAP);
        Uri artwork = bestArtwork(stream.getThumbnails());
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

    private static Uri bestArtwork(java.util.List<org.schabi.newpipe.extractor.Image> images) {
        org.schabi.newpipe.extractor.Image best = null; long bestArea = -1;
        for (org.schabi.newpipe.extractor.Image image : images) {
            long width = Math.max(1, image.getWidth()), height = Math.max(1, image.getHeight()), area = width * height;
            if (best == null || area > bestArea || area == bestArea && image.getEstimatedResolutionLevel().ordinal() > best.getEstimatedResolutionLevel().ordinal()) { best = image; bestArea = area; }
        }
        return best == null ? null : Uri.parse(best.getUrl());
    }
    private static String bounded(String value, int max) { String clean = value == null ? "" : value.trim(); return clean.length() <= max ? clean : clean.substring(0, max); }
    private static final class CachedTrack {
        final Track track; final long createdAt = System.currentTimeMillis();
        CachedTrack(Track track) { this.track = track; }
    }
    private static final class CachedSearch {
        final List<Track> tracks; final long createdAt = System.currentTimeMillis();
        CachedSearch(List<Track> tracks) { this.tracks = tracks; }
    }
}
