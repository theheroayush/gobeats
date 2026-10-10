# Privacy Policy for GoBeats

**Effective Date:** October 10, 2026  
**Last Updated:** October 10, 2026  

GoBeats ("we", "our", or "the app") is developed as a 100% free, privacy-first, offline-first music and video player. We believe that your personal media and listening habits are private. 

This Privacy Policy explains how GoBeats handles user data and permissions.

---

## 1. Zero Data Collection & Storage
- **No Personal Data Collected**: GoBeats does not collect, record, harvest, transmit, or sell any personal information (including names, email addresses, phone numbers, location data, or device identifiers).
- **No Third-Party Analytics or Tracking**: GoBeats does not contain advertising SDKs, behavioral analytics (such as Google Analytics or Firebase Analytics), or user tracking trackers of any kind.
- **Local Storage Only**: All playlists, favorites, playback history, tag edits, and video resume timestamps are stored strictly on your local device in a private SQLite database.

---

## 2. Device Permissions & Why They Are Needed

GoBeats requests only the essential Android system permissions required to provide media playback functionality:

| Permission | Purpose |
| :--- | :--- |
| `READ_MEDIA_AUDIO` / `READ_MEDIA_VIDEO` (Android 13+) | Allows GoBeats to index and display your locally stored songs, audio tracks, and video files in the library. |
| `READ_EXTERNAL_STORAGE` (Android 12 and below) | Allows reading media files on older Android versions. |
| `POST_NOTIFICATIONS` (Android 13+) | Displays media playback controls (Play, Pause, Skip, Album Art) in the Android notification panel and lock screen. |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Keeps audio playback active and uninterrupted when the screen is turned off or when switching to other apps. |
| `FOREGROUND_SERVICE_DATA_SYNC` | Ensures local Wi-Fi PC Connect file transfers do not get terminated prematurely by Android Doze mode. |
| `INTERNET` & `ACCESS_NETWORK_STATE` | Allows playing user-entered online stream URLs (e.g., direct MP3/M3U8 streams) and connecting with your PC over your local Wi-Fi network. GoBeats never transmits personal data over the internet. |
| `WAKE_LOCK` | Prevents the CPU from sleeping during active audio playback or active Wi-Fi sync transfers. |

---

## 3. Scoped Storage & File Modifications
When editing audio tags (MP3 ID3, FLAC Vorbis, M4A/MP4 tags), GoBeats adheres strictly to Android Scoped Storage guidelines. Modifying files in public directories (e.g. `Download` or SD card) triggers Android's official system confirmation dialog (`MediaStore.createWriteRequest`). Files are modified directly on your device with transactional atomic safety and are never uploaded to any remote server.

---

## 4. Children’s Privacy
GoBeats is suitable for general audiences. Because we collect zero data from any user, we do not knowingly or unknowingly collect personal information from children under the age of 13.

---

## 5. Changes to This Privacy Policy
If we update this Privacy Policy, the revised version will be published in this repository with an updated "Effective Date".

---

## 6. Contact Us
If you have any questions or feedback regarding this Privacy Policy or GoBeats, please reach out via GitHub or email:
- **Developer:** Ayush  
- **Project:** GoBeats Player  
- **GitHub Repository:** [GoBeats on GitHub](https://github.com)
