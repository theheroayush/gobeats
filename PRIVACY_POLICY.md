# Privacy Policy for gobeats

**Effective Date:** October 11, 2026  
**Last Updated:** October 11, 2026  
**Application Name:** gobeats  
**Package Name:** `com.gobeats.player`  

---

## 1. Overview & Core Philosophy

At **gobeats**, your privacy is our absolute priority. We believe your media library, listening habits, and personal data belong to you and nobody else. 

**gobeats is engineered from the ground up as a 100% on-device, offline-first application.** 
- **Zero Account Creation:** You do not need to register, log in, or provide an email address, phone number, or name.
- **Zero Third-Party Advertising:** The application contains no ads, banners, or advertising SDKs.
- **Zero Analytics / Behavioral Tracking:** We do not embed Firebase Analytics, Google Analytics, telemetry beacons, or third-party tracking software.
- **Zero Cloud Uploads of Your Media:** Your music, videos, playlists, audio profiles, and subtitles never leave your device.

---

## 2. Information We Access and How It Is Used

### A. Local Audio and Video Files
- **Permissions:** `READ_MEDIA_AUDIO`, `READ_MEDIA_VIDEO`, `READ_EXTERNAL_STORAGE` (on Android 12 and below).
- **Purpose:** Used strictly to scan, organize, display, and play media files stored locally on your device or SD card. 
- **Storage:** Metadata (title, artist, album, duration, artwork, chapter markers) is indexed locally into an on-device SQLite database (`gobeats.db`) to enable fast searching and smart playlists. None of this data is ever transmitted to an external server.

### B. Background Audio & Screen-Off Playback
- **Permissions:** `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `WAKE_LOCK`.
- **Purpose:** Enables continuous, gapless audio playback when the application is minimized, when using other apps, or when your device screen is locked/turned off. A media control notification is provided so you can control playback directly from your lock screen.

### C. Local Wi-Fi PC File Transfer (PIN-Based Sync)
- **Permissions:** `FOREGROUND_SERVICE_DATA_SYNC`, `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`.
- **Purpose:** When you explicitly activate the **PC Sync** feature, gobeats runs an ephemeral, authenticated local HTTP server on your local Wi-Fi network. This allows you to transfer music files from your personal computer to your phone via a 6-digit PIN. 
- **Security:** Communications remain strictly confined to your private local area network (LAN). No files are relayed through or stored on external servers or cloud intermediaries.

### D. Offline AI Subtitles & Voice Recognition
- **Technology:** Native Android `SpeechRecognizer` (`isOnDeviceRecognitionAvailable`).
- **Purpose:** Generates timed dual subtitles directly on your device using Android's native offline speech language packs (e.g., Hindi, Tamil, Telugu, Marathi, Bengali, English).
- **Zero Cloud Processing:** Audio tracks are processed strictly on your hardware chipset. Spoken audio is never uploaded, recorded, or shared with third parties.

### E. Hardware Audio Routing & Auto Device Profiles
- **Technology:** Android `AudioManager` and `AudioDeviceCallback`.
- **Purpose:** Detects connected audio peripherals (Bluetooth Earbuds, Over-ear Headphones, Car Audio, Built-in Phone Speaker) to automatically apply your preferred Equalizer presets and remember your custom Bluetooth AV lip-sync delay offsets.
- **Storage:** Device profile preferences and AV delays are stored solely in your local SQLite database on your device.

### F. Online Synced Lyrics (LRCLIB)
- **Service:** Public LRCLIB API (`https://lrclib.net`).
- **Purpose:** When you play a track, gobeats queries public lyrics databases using the song title and artist name to retrieve time-synced `.lrc` lyrics.
- **Privacy:** No user identifier, IP tracking, device ID, or personal data is transmitted in lyrics search queries. Once retrieved, lyrics are cached locally on your device for permanent offline access.

---

## 3. Data Storage, Security & Retention

- **On-Device Storage:** All playlists, listening statistics, playback history, equalizer curves, device profiles, and cached lyrics reside solely on your physical device.
- **Data Retention & Deletion:** You retain complete control over your data at all times:
  - Clearing the application data or cache via Android Settings (`Settings > Apps > gobeats > Storage > Clear Data`) permanently deletes all local databases and cached content.
  - Uninstalling the application completely purges all app-generated databases, configurations, and cache files from your device.

---

## 4. Children’s Privacy

**gobeats** does not collect personal information from anyone, including children under the age of 13. The application is completely safe for all age groups and complies with the Children’s Online Privacy Protection Act (COPPA) and the General Data Protection Regulation (GDPR).

---

## 5. Third-Party Libraries

gobeats uses vetted, open-source libraries to deliver high-fidelity audio:
- **AndroidX Media3 / ExoPlayer** (Google) for audiophile audio/video decoding.
- **Jetpack Compose** (Google) for UI rendering.
- **Android Support Libraries** for hardware AudioFX integration.

None of these libraries collect or transmit personal information or telemetry in gobeats.

---

## 6. Changes to This Privacy Policy

We may update this Privacy Policy from time to time to reflect feature enhancements, business model updates, or regulatory requirements. Any changes will be posted with an updated "Last Updated" date.

---

## 7. Commercial Terms, Pricing & Future Monetization Rights

- **Current Application Availability:** gobeats is currently distributed free of charge with all baseline and core features unlocked.
- **Developer Commercial Autonomy:** The developer of gobeats retains full autonomy and complete commercial rights to introduce monetization models at any time in subsequent updates at the developer's sole discretion.
- **Future Monetization Models:** The developer reserves the right to introduce optional paid tiers, premium feature licensing ("gobeats PRO"), one-time lifetime licenses, optional subscription plans, or in-app billing through Google Play Billing.
- **Feature Evolution:** While core on-device playback functionality remains privacy-respecting and offline-first, future enhancements, advanced DSP/equalizer extensions, expanded on-device AI capabilities, or cloud integrations may be offered as optional paid upgrades.
- **Terms Acceptance:** Continued use of the application following the release of any update incorporating commercial features constitutes acceptance of the applicable commercial terms and Google Play In-App Purchase terms.

---

## 8. Contact Us

If you have any questions, feedback, or concerns regarding this Privacy Policy or your data, please contact us at:

- **Email:** `support@gobeats.app` (or `ayush@gobeats.app`)  
- **Website:** `https://gobeats.app`  
- **Developer:** gobeats Engineering Team  
