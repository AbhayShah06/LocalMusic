# Navidrome setup

Local Music can connect to any HTTPS OpenSubsonic-compatible server. Navidrome is the recommended option.

1. Put owned audio files in one folder on a computer or NAS.
2. Install Navidrome from its official release or container image and point its music library at that folder.
3. Create separate Navidrome users for each listener. Do not share the administrator account.
4. Expose Navidrome through a trusted HTTPS address. Tailscale HTTPS or a Caddy reverse proxy are suitable for a private family setup.
5. In Local Music, tap **Library: This phone**, choose **Set up Navidrome**, and enter the HTTPS address and account.

The app encrypts the saved server credentials using Android Keystore-backed encrypted preferences. It sends Subsonic token authentication rather than the raw password on API requests. Plain HTTP is intentionally refused.

The computer or NAS must be awake while streaming. Music already stored on the phone remains available without the server.
