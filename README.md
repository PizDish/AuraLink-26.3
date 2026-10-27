# AuraLink 0.2.0-alpha — Minecraft 26.3 Fabric + Yandex Music companion

**Original open-source Minecraft music player, inspired by the feature set of Overtune.** This is NOT a copy of Overtune's code or UI. A new client-side mod is included with an independent Chromium browser extension for the official Yandex Music website.

## Honest status

- ✅ Java sources and project scaffolding, source detection check, YAML workflow, JSON manifests.
- ✅ Pure-Java tests for settings, playlists, and pairing; JavaScript syntax check.
- ⚠️ **Real Fabric Loom compile and in-game launch are NOT yet verified here.** Use GitHub Actions with Java 25; do **not** install older AuraMusic test JAR files.
- ⚠️ Yandex page selectors may need maintenance when their website changes; playback remains in your own browser.
- ❌ No Yandex account login/token capture, subscription bypass, Yandex audio downloads, or unofficial private streaming API.

## Features

| Feature | Status | Notes |
|---|---|---|
| Minecraft built-in soundtrack | Implemented source | Uses in-game SoundEvents |
| Minecraft music discs | Implemented source | Uses in-game SoundEvents |
| Local WAV files | Implemented source | Java Sound (PCM WAV); 60MB maximum per file |
| Local MP3/M4A/FLAC/OGG import | External conversion tool | `tools/import_audio.py`, requires own FFmpeg and legally held audio |
| Queue / remove / reorder / skip | Implemented source | QUEUE tab; Up/Down; Q to enqueue |
| Custom playlists / favorites | Implemented source | LISTS tab; saved in instance config |
| Repeat / shuffle / local playback controls | Implemented source | ALL / WAV / DISCS / LIKED tabs |
| Custom track metadata | Implemented source | META tab: local title/artist/album overrides, without altering files |
| Auto-world music | Partial | Optional on world join and dimension changes; no custom event rules yet |
| Yandex current track, artist, play/pause, next/prev | Browser-companion prototype | Requires Chrome/Edge extension and official Yandex Music tab |
| Yandex like / shuffle / repeat | Best-effort | Website selectors/accessible labels might change |
| Full Yandex library search/playlists in-game | Not supported | Would require a supported official integration |
| Overtune advanced world events / crossfade / toast overlays | Not yet | Planned; **NOT** 1:1 feature parity |

## Build the REAL JAR on GitHub

1. Create a **new** public GitHub repository such as `AuraLink-26.3`. Upload the **contents of this folder**; don't nest it one level deeper.
2. Ensure `src`, `companion-extension`, `build.gradle`, `settings.gradle`, `gradle.properties`, and `.github/workflows/build.yml` are in the repository root. Hidden `.github` folder matters.
3. Open **Actions → Build AuraLink for Minecraft 26.3**. The workflow uses Java 25 and Gradle 9.7.1 with Fabric Loom 1.18.4 and Fabric API `0.164.0+26.3`.
4. Only if successful, download artifact `AuraLink-26.3-JAR`, unpack it, and move the ordinary JAR (not sources/dev JAR) into `mods` of a dedicated Minecraft **26.3 Fabric** instance alongside Fabric API. 
5. Check game start. If compilation fails, share the **first `error:` block** in the Gradle job. If runtime fails, share the `crash-reports/crash-*.txt` file.

Note: if API/library versions change later, update Gradle dependencies. Do not treat a green GitHub job as a passed runtime test.

## Controls in Minecraft

- `M` opens AuraLink. Close it normally to return to the game.
- **ALL/DISCS/WAV/LIKED**: click a track to play, `☆` favorite, `+` add to selected playlist, `Q` enqueue.
- **QUEUE**: play, reorder, or remove queued songs.
- **META**: choose a track, edit displayed title, artist and album; overrides are saved only locally.
- **LISTS**: create/delete/select/play local playlists. `+` on a song uses the currently selected playlist.
- **YANDEX**: commands and metadata for the official Yandex Music website; `COPY PAIRING KEY` copies your locally generated secret for browser pairing.
- `JOIN:OFF` enables soundtrack on world entry; LISTS and options are stored in the game's `config` folder.

## Set up Yandex Music in Edge/Chrome

1. Start the Fabric 26.3 game with a properly compiled AuraLink JAR. Press `M` → `YANDEX` → **COPY PAIRING KEY**.
2. Extract the included `companion-extension` directory from this source project.
3. In Edge open `edge://extensions`, or Chrome `chrome://extensions`. Enable **Developer mode** and select **Load unpacked** → `companion-extension`.
4. Click the extension icon, paste the pairing key, then **Save**. Don't publish the pairing key or your game's `config/auralink-pairing.properties` file.
5. Open `https://music.yandex.ru/`, sign in normally on **Yandex's own website** and start playback. Reload that tab after installing the extension.
6. The Minecraft YANDEX tab should show `CONNECTED` if the page and loopback bridge are reachable. Yandex Music must remain open; its website is responsible for actual audio playback and user rights.

The companion extension only communicates with `http://127.0.0.1:27363`, which is bound to **loopback**, using a locally generated pairing code. The extension observes MediaSession metadata and clicks Yandex player controls in its own page; it does not read cookies or credentials. Nothing is uploaded to AuraLink servers (there are no AuraLink servers).

**Limitations:** Yandex's website is not a documented playback API for third-party Minecraft mods. A new page design could break commands, metadata might be missing, a browser may pause background tabs, and direct Yandex catalog search is not implemented. The desktop Yandex Music app is not supported by this browser extension.

## Import your own audio (optional)

Copy supported `.wav` files into `[game directory]/auralink/tracks/` and press SCAN. Other formats: install FFmpeg locally then execute:

```shell
python tools/import_audio.py "C:/Music/MySong.mp3" --game-dir "C:/Path/To/PrismInstance/minecraft"
```

Conversion is entirely local. Original tracks remain unchanged. Very long WAV files over 60 MB will be rejected by the Java Sound engine in this alpha.

## Development / checks

```shell
bash tests/run-core-tests.sh
node --check companion-extension/content.js
```

No actual Fabric build was run in this environment; the only supported build path shipped is the GitHub Actions workflow. Never install a JAR compiled against hand-written Minecraft API placeholders.

## License

MIT — original AuraLink community source code. The original Overtune JAR is **not** included or decompiled into this project. Yandex Music is a separate service and its rights/terms apply to use of that service. All trademarks belong to their owners.
