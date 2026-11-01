# Planned changes

## Song preview while adding to a playlist

Requested November 1, 2026. When browsing songs in an **Add to playlist** flow, tapping a song should play it from the beginning as a quick, low commitment preview. Tapping must not add the song; adding remains a separate explicit action. Keep the user in the picker so they can continue browsing.

Before implementation, inspect the existing playback and picker flows and decide how a preview should stop or restore the previous song and queue when the picker closes or another preview starts. Verify on Android that previewing does not silently alter Jam playback, the active queue, or playlist contents.
