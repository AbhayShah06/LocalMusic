# Local Music private cloud

Private R2 bucket plus a read-only Worker. The Android app stores only the Worker URL and a scoped read token in encrypted preferences. It never receives R2 credentials.

Upload audio using `Artist/Album/Track.ext` keys. Optional R2 custom metadata keys `title`, `artist`, and `album` override path-derived values. The Worker lists up to 5,000 objects, searches the catalog, and supports HTTP range streaming for seeking.

Before deployment, enable R2 in the Cloudflare dashboard, create the `local-music-private` bucket, deploy the Worker, and save a strong `READ_TOKEN` Worker secret. Keep the bucket's public development URL disabled.
