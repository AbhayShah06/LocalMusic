# LocalMusic

A music player for Android. Stream, play your own files, or bring a Navidrome / Backblaze library.

- Live queue, lyrics, favorites and playlists
- Background downloads, offline playback and copies in Music/LocalMusic
- Playlist imports from links, exports or pasted song names
- Recently Played, personal mixes and a shared Jam queue

[Get the latest APK](https://github.com/AbhayShah06/LocalMusic/releases/latest)

## Build

JDK 17 + Android SDK 35. Run `./gradlew assemblePublicBuildDebug`.

Work in progress. Streaming depends on upstream services; artist/album browsing is still basic. App downloads are removed on uninstall; exported music-folder copies stay. Keep personal credentials and signing keys out of Git.

GPL-3.0-or-later. Uses [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor); not affiliated with the streaming services. Use content you have permission to access and download.
