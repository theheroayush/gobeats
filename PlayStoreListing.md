# gobeats — Google Play Store Listing & Production Submission Package (v3.2.0)

**Target Release:** Version `3.2.0` (versionCode `5`)  
**Package Name:** `com.gobeats.player`  
**Bundle File:** [`gobeats-v3.2.0-release.aab`](file:///c:/Users/ayush/OneDrive/Documents/gobeats/gobeats-v3.2.0-release.aab)  

---

## 1. App Title & Subtitle (ASO Optimized)

- **App Title (Max 30 chars):** `gobeats: Offline Music Player`
- **Short Description (Max 80 chars):** `Offline player with AI dual subtitles, auto device profiles & synced lyrics.`

---

## 2. Full Store Description (Google Play Formatted)

**gobeats** is a precision-engineered, audiophile-grade offline music and video player designed for true sound lovers. Built with zero cloud dependence, gobeats leverages the raw processing power of your Android device to deliver bit-perfect playback, dynamic real-time artwork visuals, intelligent offline playlists, hardware-aware audio profiles, and millisecond-accurate synchronized lyrics.

No ads. Zero behavioral tracking. 100% offline-first. Just pure media, the way artists and filmmakers intended. All features currently unlocked for community release.

---

### 🌟 KEY FEATURES (v3.2)

#### 🎙️ Offline AI Generated Subtitles & Dual Subtitles
Generate speech-to-text subtitles directly on your phone with zero internet connection:
• **Indian Regional Focus**: Native offline recognition for Hindi, Tamil, Telugu, Marathi, Bengali, and English.
• **Dual Subtitle Stacking**: Display primary dialogue (bold amber) and secondary translated subtitles (crisp cyan) simultaneously on video playback.
• **Zero App Bloat**: Leverages Android’s on-device speech language models with direct one-tap download shortcuts.

#### 🎧 Hardware Auto Device Profile & Persistent AV Delay
Never re-adjust your equalizer or lip-sync timing again:
• **Auto Hardware Detection**: Detects whether you are connected to TWS Earbuds, Over-ear Headphones, Car Audio, or Phone Speakers.
• **Auto EQ Switching**: Instantly applies tuned acoustic curves for each device profile.
• **Persistent Bluetooth Lip-Sync Delay**: Remembers custom audio delay offsets (±500 ms) per Bluetooth device MAC/name so audio and video dialogue stay perfectly in sync every time you reconnect.

#### 🗣️ Dialogue Boost (Formant Speech Clarity Filter)
Stop struggling to hear movie dialogue over explosive sound effects. Our acoustic formant filter isolates spoken speech frequencies (300 Hz – 3.4 kHz) and attenuates low-end rumble (at 60 Hz), providing crystal-clear actor voices in noisy environments.

#### ⏩ 100% Offline Video Skip Intro
Enjoy binge-watching without manual scrubbing. gobeats detects embedded chapter markers and opening sequence energy patterns completely offline, showing a floating, responsive `[ ⏩ Skip Intro ]` action button.

#### 📴 Screen-Off Video Audio Playback
Keep listening to video concerts, podcasts, and lectures even when your screen is locked or turned off. Background playback continues uninterrupted without audio stutter or app crashes.

#### 🎨 Dynamic Artwork Mesh Gradients & 60 FPS Visualizer
Experience a living Now Playing interface. gobeats extracts dominant, vibrant, and deep tonal palettes directly from your album covers, transforming the background into an animated, fluid mesh gradient. Watch your music come alive with a hardware-accelerated 60 FPS audio visualizer reacting in real time to bass drops and transients.

#### 📜 Automatic Synced Lyrics (LRCLIB + Offline Cache)
Sing along with word-for-word, time-synced lyrics. gobeats automatically fetches synced `.lrc` lyrics in the background without interrupting your playback, indexing them directly to your device storage. Once cached, lyrics are permanently accessible completely offline. Also supports companion `.lrc` files and embedded ID3/USLT/SYLT tags.

#### 🧠 On-Device Smart Playlists & Telemetry
Let your device curate your soundtrack without sending a single byte to the cloud. gobeats tracks your listening habits locally to dynamically generate:
• **Most Played**: Your all-time high-rotation anthems.
• **Heavy Rotation**: What you can’t stop playing over the past 14 days.
• **Recently Played**: Instant access to your recent playback history.
• **Forgotten Favorites**: Rediscover buried gems you used to love.
• **Liked Songs**: Instant one-tap bookmarking.

#### 🎛️ Studio-Grade 5-Band Equalizer & ReplayGain
Fine-tune every frequency with our low-latency DSP equalizer. 
• Custom presets: Studio Reference, Deep Bass Punch, Vocal Clarity, Warm Analog, Electronic/EDM, and Acoustic.
• Independent Bass Boost & Surround Sound Virtualizer.
• ITU-R BS.1770-4 ReplayGain with peak-clipping prevention for uniform, distortion-free loudness across all albums.

#### ⚡ Instant Background Media Scanner
Never wait for your library to load. gobeats uses non-blocking asynchronous scanning with windowed SQLite transactions. Start browsing, searching, and listening immediately while your entire music and video archive indexes smoothly in the background.

#### 💻 Seamless PC Sync (Pin-Based Local Transfer)
Connect your phone and PC over local Wi-Fi with a clean, instant 6-digit PIN. Transfer songs, albums, and playlists directly from your desktop browser to your phone at full gigabit network speeds.

---

### 🎧 TECHNICAL SPECIFICATIONS & SUPPORTED FORMATS
• **Audio Engine**: AndroidX Media3 / ExoPlayer with 32-bit Float bit-perfect decoding
• **Audio Formats**: FLAC, ALAC, WAV, AIFF, DSD, APE, MP3, AAC, OGG Vorbis, Opus, M4A
• **Video Formats**: MP4, MKV, WebM, 3GP, AVI
• **Tag & Subtitle Support**: ID3v1, ID3v2.3, ID3v2.4, Vorbis Comments, MP4 Atoms, SRT, VTT, LRC
• **Permissions**: Zero invasive permissions. Audio and media storage only.

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

