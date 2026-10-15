package com.maxrave.simpmusic.viewModel

import androidx.lifecycle.viewModelScope
import com.eygraber.uri.Uri
import com.maxrave.domain.data.model.importdata.ImportData
import com.maxrave.domain.data.model.importdata.ImportPlaylist
import com.maxrave.domain.data.model.importdata.ImportSong
import com.maxrave.domain.data.model.searchResult.songs.SongsResult
import com.maxrave.domain.repository.ImportProgress
import com.maxrave.domain.repository.ImportRepository
import com.maxrave.domain.repository.SearchRepository
import com.maxrave.domain.utils.Resource
import com.maxrave.logger.LogLevel
import com.maxrave.simpmusic.viewModel.base.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.import_invalid_file

/**
 * Drives an import of a file produced by the SimpMusic web converter.
 *
 * The picked file is read here rather than in the repository because only the app module knows
 * what a picked file is: a content Uri on Android, a plain path on Desktop. [readPickedFile] opens
 * either, the same way restoreNative opens a backup.
 */
class ImportViewModel(
    private val importRepository: ImportRepository,
    private val searchRepository: SearchRepository,
) : BaseViewModel() {
    private val _importState: MutableStateFlow<ImportProgress?> = MutableStateFlow(null)

    /** `null` while idle; otherwise the latest step of the running or finished import. */
    val importState: StateFlow<ImportProgress?> = _importState.asStateFlow()

    private var importJob: Job? = null

    fun import(uri: Uri) {
        importJob?.cancel()
        importJob =
            viewModelScope.launch {
                _importState.value = ImportProgress.Preparing
                val invalidFileMessage = getString(Res.string.import_invalid_file)
                val contents =
                    withContext(Dispatchers.IO) {
                        runCatching { readPickedFile(uri).decodeToString() }
                    }.getOrElse { throwable ->
                        log("import: cannot read picked file - ${throwable.message}", LogLevel.WARN)
                        _importState.value = ImportProgress.Error(invalidFileMessage)
                        return@launch
                    }
                val json = if (looksLikeNativeImport(contents)) contents else convertTrackList(contents)
                if (json == null) {
                    _importState.value = ImportProgress.Error(invalidFileMessage)
                    return@launch
                }
                importRepository.import(json, invalidFileMessage).collect { progress ->
                    _importState.value = progress
                }
            }
    }

    /** Back to idle, which is what dismisses the progress/result dialog. */
    fun dismiss() {
        importJob?.cancel()
        importJob = null
        _importState.value = null
    }

    private fun looksLikeNativeImport(text: String): Boolean =
        text.trimStart().startsWith("{") && (text.contains("\"songs\"") || text.contains("\"playlists\""))

    /**
     * Converts common Spotify, Apple Music, Soundiiz and TuneMyMusic CSV exports, plus simple
     * M3U files, without sending the library to a conversion server. Each row is matched against
     * YouTube Music using deterministic metadata; no on-device AI model is needed.
     */
    private suspend fun convertTrackList(text: String): String? {
        val tracks = parseTrackList(text)
        if (tracks.isEmpty()) return null

        val matched = mutableListOf<ImportSong>()
        tracks.forEachIndexed { index, source ->
            _importState.value = ImportProgress.Importing(index, tracks.size)
            val result = findBestMatch(source) ?: return@forEachIndexed
            matched += result.toImportSong()
        }
        if (matched.isEmpty()) return null

        return Json.encodeToString(
            ImportData(
                songs = matched.distinctBy { it.videoId },
                playlists = listOf(ImportPlaylist(title = "Imported playlist", videoIds = matched.map { it.videoId })),
            ),
        )
    }

    private suspend fun findBestMatch(source: SourceTrack): SongsResult? {
        val query = listOf(source.title, source.artist).filter { it.isNotBlank() }.joinToString(" ")
        val response = searchRepository.getSearchDataSong(query).first()
        val candidates = (response as? Resource.Success)?.data.orEmpty()
        return candidates.maxByOrNull { candidate -> matchScore(source, candidate) }
            ?.takeIf { matchScore(source, it) >= 45 }
    }

    private fun matchScore(source: SourceTrack, candidate: SongsResult): Int {
        val sourceTitle = normalize(source.title)
        val resultTitle = normalize(candidate.title.orEmpty())
        val sourceArtist = normalize(source.artist)
        val resultArtists = normalize(candidate.artists.orEmpty().joinToString(" ") { it.name })
        var score = when {
            sourceTitle == resultTitle -> 60
            resultTitle.contains(sourceTitle) || sourceTitle.contains(resultTitle) -> 45
            else -> 0
        }
        if (sourceArtist.isNotBlank() && (resultArtists.contains(sourceArtist) || sourceArtist.contains(resultArtists))) score += 30
        if (source.album.isNotBlank() && normalize(candidate.album?.name.orEmpty()) == normalize(source.album)) score += 10
        val candidateDuration = candidate.durationSeconds
        if (source.durationSeconds > 0 && candidateDuration != null &&
            kotlin.math.abs(source.durationSeconds - candidateDuration) <= 4
        ) score += 15
        return score
    }

    private fun SongsResult.toImportSong(): ImportSong {
        val resultArtists = artists
        return ImportSong(
            videoId = videoId,
            title = title.orEmpty(),
            artistName = resultArtists?.map { it.name },
            artistId = resultArtists?.mapNotNull { it.id }?.takeIf { it.size == resultArtists.size },
            albumName = album?.name,
            albumId = album?.id,
            duration = duration.orEmpty(),
            durationSeconds = durationSeconds ?: 0,
            isExplicit = isExplicit ?: false,
            thumbnails = thumbnails?.lastOrNull()?.url,
            videoType = videoType.orEmpty(),
        )
    }

    private fun parseTrackList(text: String): List<SourceTrack> {
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) return emptyList()
        if (lines.first().trim().startsWith("#EXTM3U", ignoreCase = true)) {
            return lines.filter { it.startsWith("#EXTINF:") }.mapNotNull { line ->
                val label = line.substringAfter(',', "").trim()
                val duration = line.substringAfter(':').substringBefore(',').toIntOrNull() ?: 0
                val parts = label.split(" - ", limit = 2)
                if (parts.size == 2) SourceTrack(parts[1], parts[0], "", duration) else SourceTrack(label, "", "", duration)
            }
        }

        val rows = lines.map(::parseCsvLine)
        val header = rows.first().map(::normalize)
        fun column(vararg names: String): Int = names.firstNotNullOfOrNull { name -> header.indexOf(normalize(name)).takeIf { it >= 0 } } ?: -1
        val titleColumn = column("track name", "track", "title", "name")
        val artistColumn = column("artist name", "artist", "artists")
        val albumColumn = column("album name", "album")
        val durationColumn = column("duration ms", "duration (ms)", "duration", "track duration")
        if (titleColumn < 0) return emptyList()
        return rows.drop(1).mapNotNull { row ->
            val title = row.getOrNull(titleColumn)?.trim().orEmpty()
            if (title.isBlank()) null else SourceTrack(
                title = title,
                artist = row.getOrNull(artistColumn)?.trim().orEmpty(),
                album = row.getOrNull(albumColumn)?.trim().orEmpty(),
                durationSeconds = row.getOrNull(durationColumn)?.let(::parseDuration) ?: 0,
            )
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val value = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && quoted && line.getOrNull(index + 1) == '"' -> { value.append('"'); index++ }
                char == '"' -> quoted = !quoted
                char == ',' && !quoted -> { result += value.toString(); value.clear() }
                else -> value.append(char)
            }
            index++
        }
        result += value.toString()
        return result
    }

    private fun parseDuration(value: String): Int {
        val trimmed = value.trim()
        trimmed.toLongOrNull()?.let { return if (it > 10_000) (it / 1000).toInt() else it.toInt() }
        val parts = trimmed.split(':').mapNotNull(String::toIntOrNull)
        return when (parts.size) { 2 -> parts[0] * 60 + parts[1]; 3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]; else -> 0 }
    }

    private fun normalize(value: String): String = value.lowercase().filter { it.isLetterOrDigit() || it == ' ' }.replace(Regex("\\s+"), " ").trim()

    private data class SourceTrack(val title: String, val artist: String, val album: String, val durationSeconds: Int)
}
