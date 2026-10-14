# Local Music

A separate offline-first Android player for audio files already on the phone, inspired by the speed and ownership model of Musicolet/Phonograph—not a streaming service and not tied to Personal Stream.

## Starter included

- Correct Android 13+ and legacy audio-library permissions
- MediaStore scan limited to music files
- Alphabetical native track list with title and artist
- Tap-to-play and explicit Stop
- Clear permission, empty-library, and unreadable-track states
- No account, subscription, tracking, ads, or network permission

## Hard release plan

1. **Playback core:** Media3 background service, notification controls, audio focus, headset/Bluetooth actions, seek, shuffle, repeat, gapless playback, and sleep timer.
2. **Library:** albums, artists, genres, folders, search, multi-queue management, favorites, and editable playlists with import/export.
3. **File behavior:** rescan on MediaStore changes, deleted/moved file recovery, embedded artwork, tag editing, ReplayGain, and format capability reporting.
4. **UX:** dense one-handed layout, configurable tabs, persistent mini-player, no ornamental AI-dashboard design, full offline behavior.
5. **Release gate:** 10k+ track library, corrupt files, missing SD card, permission revocation, calls/audio focus, process death, and Android 10–15 devices.

## Build status

The source foundation is present. No APK was produced because Java and the Android SDK were not installed on this machine.

