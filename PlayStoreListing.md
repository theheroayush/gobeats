# gobeats — Google Play Store Listing & Production Submission Package (v3.2.0)

**Target Release:** Version `3.2.0` (versionCode `5`)  
**Package Name:** `com.gobeats.player`  
**Bundle File:** [`gobeats-v3.2.0-release.aab`](file:///c:/Users/ayush/OneDrive/Documents/gobeats/gobeats-v3.2.0-release.aab)  

---

## 1. App Title & Subtitle (ASO Optimized)

- **App Title (Max 30 chars):** `gobeats: Offline Music Player`
- **Short Description (Max 80 chars):** `Offline player with AI dual subtitles, auto device profiles & synced lyrics.`

---

## 2. Full Store Description (Google Play Formatted — 3,407 / 4,000 Chars)

gobeats is a precision-engineered, audiophile-grade offline music and video player built for true sound lovers. Engineered with zero cloud dependence, gobeats leverages the on-device processing power of your phone to deliver bit-perfect playback, dynamic real-time artwork visuals, intelligent offline playlists, hardware-aware audio profiles, and millisecond-accurate synchronized lyrics.

Zero ads. Zero behavioral tracking. 100% offline-first. Just pure media, the way artists and filmmakers intended.

━━━━━━━━━━━━━━━━━━━━━━━━
🌟 KEY FEATURES
━━━━━━━━━━━━━━━━━━━━━━━━

🎙️ OFFLINE AI SUBTITLES & DUAL SUBTITLE STACKING
• On-Device Speech Recognition: Generate subtitles directly on your phone with zero internet connection.
• Indian Regional Focus: Offline recognition for Hindi, Tamil, Telugu, Marathi, Bengali, and English.
• Dual Subtitle Stacking: Display primary dialogue (bold amber) and secondary translated subtitles (crisp cyan) simultaneously on video playback.
• Zero App Bloat: Uses native Android on-device speech packs with one-tap shortcuts.

🎧 HARDWARE AUTO DEVICE PROFILES & AV LIP-SYNC DELAY
• Auto Peripheral Detection: Automatically detects connected TWS Earbuds, Over-ear Headphones, Car Audio, or Phone Speakers.
• Dynamic EQ Switching: Instantly activates tuned acoustic curves for each device profile.
• Persistent Bluetooth Lip-Sync Delay: Remembers custom audio delay offsets (±500 ms) per device so video dialogue stays permanently in sync.

🗣️ DIALOGUE BOOST (ACOUSTIC FORMANT FILTER)
• Never struggle to hear movie dialogue over loud sound effects again.
• Isolates spoken speech frequencies (300 Hz – 3.4 kHz) while reducing low-end rumble (at 60 Hz) for crystal-clear voices.

⏩ 100% OFFLINE VIDEO SKIP INTRO
• Smart Chapter & Opening Detection: Detects opening themes and chapter markers completely offline.
• Responsive Skip Button: Fluid floating action pill to jump straight into the episode.

📴 SCREEN-OFF VIDEO AUDIO CONTINUITY
• Background Playback: Keep listening to video concerts, lectures, and podcasts with your screen locked or turned off.
• Full system media notification controls.

🎨 DYNAMIC MESH GRADIENTS & 60 FPS VISUALIZER
• Living Album Art: Extracts dominant and vibrant tonal palettes from album covers to render fluid mesh gradients.
• Hardware-Accelerated Visualizer: Real-time 60 FPS visualizer reacting dynamically to bass drops and transients.

📜 AUTOMATIC SYNCED LYRICS (LRCLIB + OFFLINE CACHE)
• Word-by-word synchronized lyrics fetched in the background and cached locally for permanent offline access.
• Also supports companion .lrc files and embedded ID3/USLT tags.

🧠 ON-DEVICE SMART PLAYLISTS
• Most Played, Heavy Rotation (last 14 days), Recently Played, and Forgotten Favorites curated entirely on your device.

🎛️ STUDIO 5-BAND EQUALIZER & REPLAYGAIN
• Custom presets: Studio Reference, Deep Bass Punch, Vocal Clarity, Warm Analog, Electronic/EDM, and Acoustic.
• ITU-R BS.1770-4 ReplayGain with peak-clipping prevention.

💻 PIN-BASED LOCAL WI-FI PC SYNC
• Transfer songs, albums, and playlists directly from your desktop browser to your phone over your local network using a secure 6-digit PIN.

━━━━━━━━━━━━━━━━━━━━━━━━
🎧 SUPPORTED FORMATS
━━━━━━━━━━━━━━━━━━━━━━━━
• Audio: FLAC, ALAC, WAV, AIFF, DSD, APE, MP3, AAC, OGG Vorbis, Opus, M4A
• Video: MP4, MKV, WebM, 3GP, AVI
• Subtitles: SRT, VTT, Embedded MKV/MP4 tracks, LRC

---

## 3. What’s New in Version 3.2.0 (Release Notes)

- **Offline AI Subtitles & Dual Subtitle Stacking**: Generate on-device subtitles for Hindi, Tamil, Telugu, Marathi, Bengali, and English with dual-language simultaneous rendering.
- **Hardware-Aware Auto Device Profiles**: Automatic detection of Earbuds, Headphones, Car Audio, and Speakers with auto-switching EQ profiles.
- **Persistent Bluetooth AV Lip-Sync Delay**: Remembers custom audio delay offsets per Bluetooth device to guarantee permanent video lip-sync.
- **Dialogue Boost**: Formant vocal intelligibility DSP filter (+4 dB / +7 dB speech boost, rumble cut) for crystal-clear actor voices.
- **100% Offline Skip Intro**: Automatically detects opening sequences and provides a one-tap skip button without internet.
- **Screen-Off Video Audio Continuity**: Seamless background audio playback when the screen is locked.
- **Distinct Audio Pipelines**: Video audio enhancements operate independently without altering bit-perfect music curves.

---

## 4. Privacy Policy URL Options (Google Play Required)

Google Play Console requires a live public HTTPS URL for your privacy policy. You have 3 instant options:

### Option A: GitHub Pages (Recommended — 100% Free & Permanent)
If your repository is on GitHub:
1. Push [`privacy-policy.html`](file:///c:/Users/ayush/OneDrive/Documents/gobeats/privacy-policy.html) to your repository.
2. Go to **Repository Settings > Pages > Source** and select `main` branch.
3. Your Privacy Policy URL will be:  
   `https://<your-github-username>.github.io/gobeats/privacy-policy.html`

### Option B: Free Google Sites / Notion
1. Copy the text from [`PRIVACY_POLICY.md`](file:///c:/Users/ayush/OneDrive/Documents/gobeats/PRIVACY_POLICY.md).
2. Paste it into a public Notion page or Google Sites page.
3. Enter that public link in the Google Play Console **App Content > Privacy Policy** field.

### Option C: Hosted via RawGit / GitHub Gist
Paste the contents of [`PRIVACY_POLICY.md`](file:///c:/Users/ayush/OneDrive/Documents/gobeats/PRIVACY_POLICY.md) into a public GitHub Gist or your personal domain (e.g., `https://gobeats.app/privacy-policy`).

---

## 5. Google Play Console — Data Safety Form Answers

When filling out the **Data Safety Questionnaire** in Google Play Console, answer as follows:

| Question | Your Answer | Reason / Explanation |
| :--- | :--- | :--- |
| **Does your app collect or share any user data?** | **No** | gobeats processes everything 100% on-device. Media files, playlists, and settings remain solely in the local SQLite database. |
| **Is data transferred over an encrypted connection?** | **Yes** | Any external public requests (e.g. LRCLIB public lyrics queries) use HTTPS. |
| **Can users request that their data be deleted?** | **Yes** | Users can delete all playlists, history, and cached lyrics by tapping "Clear Data" in Android Settings or uninstalling the app. |

---

## 6. Google Play Declarations & Foreground Service Justifications

### A. Foreground Service (`FOREGROUND_SERVICE_MEDIA_PLAYBACK`)
- **Category:** Audio / Video Playback
- **Justification for Reviewers:**  
  *"gobeats is a local media player that allows users to listen to music and video audio continuously in the background and when the device screen is locked. The foreground service with notification controls is essential to provide uninterrupted playback and system media controls (Play/Pause, Seek, Skip)."*

### B. Foreground Service (`FOREGROUND_SERVICE_DATA_SYNC`)
- **Category:** Data Sync / File Transfer
- **Justification for Reviewers:**  
  *"Required for our local Wi-Fi PC Sync feature, which allows users to wirelessly transfer music files between their desktop computer and phone over their private local area network without interruption while large files are being copied."*

### C. Target Audience & Content Rating
- **Target Age:** 13 and older (or General Audience)
- **Contains Ads:** **No**
- **App Category:** Music & Audio / Media Player
- **Tags:** Music Player, Offline Audio, Video Player, Equalizer, Subtitles

---

## 7. Commercial Terms & Developer Monetization Autonomy

- **Current Status:** Free of charge with 100% features unlocked for community launch.
- **Future Pricing Rights:** The developer reserves complete autonomy and commercial rights to introduce optional monetization models (including paid PRO upgrades, lifetime licenses, or optional in-app subscriptions via Google Play Billing) in subsequent versions.
- **Offline Integrity:** Any future monetization will maintain the offline-first, zero-telemetry foundation of the app without predatory restrictions.

