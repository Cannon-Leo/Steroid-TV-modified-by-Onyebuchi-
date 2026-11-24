# Steroid TV, modified by Onyebuchi

**A personal, heavily customised version of [Steroid TV](https://github.com/sanchit0212s/steroid-tv) by [Sanchit Singhal](https://github.com/sanchit0212s).**

> **Credit where it is due.** Steroid TV, its idea, design language, Retro TV, Radio, The World's Windows,
> Little Office, the build system and the cleanup playbook are the original work of **Sanchit Singhal**.
> This repository starts from his project, keeps his commit history and his MIT licence, and only adds my
> own changes and improvements on top, for my TV. If you want the original, clean, general purpose
> version, use **[sanchit0212s/steroid-tv](https://github.com/sanchit0212s/steroid-tv)**. His original
> README is kept word for word in [`docs/ORIGINAL-README.md`](docs/ORIGINAL-README.md).

Built and tested on a **Xiaomi Mi TV Stick 4K** (MiTV-AYFR0, Android TV 11, Amlogic, 2 GB RAM),
from an **Intel MacBook Pro** without Homebrew. Changes were made with Claude as a pair programmer.

---

## Contents

1. [What I changed](#what-i-changed)
2. [What stays from the original](#what-stays-from-the-original)
3. [Requirements: Mac](#requirements-mac)
4. [Requirements: Windows](#requirements-windows)
5. [First setup](#first-setup)
6. [Everyday commands](#everyday-commands)
7. [Little Office on Mac and Windows](#little-office-on-mac-and-windows)
8. [Your photos and profile picture](#your-photos-and-profile-picture)
9. [Troubleshooting](#troubleshooting)
10. [Honest limits](#honest-limits)
11. [Licences and credits](#licences-and-credits)

---

## What I changed

### Home screen
- **All apps, always:** every installed app, TV or phone (sideloaded APKs included), in a grid that
  always fits the screen. New installs appear by themselves; phone apps show their icon on a tile in the
  icon's own colour instead of a blank card.
- **Watch row you control:** a **+** card adds any app; hold OK on a card to move, remove, hide,
  open App info or uninstall. No rebuild needed, everything is saved on the TV.
- **My apps:** a second personal row after Retro TV and Radio.
- **Recently played rows:** one row per streaming app (YouTube, YouTube Music, Spotify, Prime, Netflix...)
  that fills itself as you watch and updates in real time. Opens the exact video or track when the app
  shares its id, otherwise the app itself (which resumes where you left off).
- **Greeting with your name** ("Good evening, Onyebuchi."), a profile picture, a gently breathing
  salutation, and a settings page to change both.
- **Top bar icons** from [Lucide](https://lucide.dev): Assistant, Recent apps, Wi‑Fi, Bluetooth,
  Sound, Settings. They stand alone, and get a dark disc when selected for contrast.
- **Photo background:** a slow crossfading slideshow (the World's Windows views plus your own photos),
  with a strong overlay so text always stays readable. Optional slow zoom.
- **Frosted glass** panels (blurred once per photo, no per frame cost) and a soft **light sweep** when an
  item gets focus.

### Focus and navigation
- **One gliding focus ring** for the whole screen that traces the exact shape of what is selected:
  complete circles on round buttons, pills on pills, the card's own corners on cards. It lives on its own
  layer, so rows, scrolling and screen edges can never clip it.
- Rows scroll with breathing room so zoomed cards are never cut at the screen edges.

### Widgets
- **Music widget** for any app that plays (YouTube, YouTube Music, Spotify, Prime, Netflix...): artwork
  backdrop under a heavy overlay, previous, play/pause, next, close, a progress bar, and the artwork opens
  the app. Fixed size, so it never pushes the page around. A swap button flips between it and the system
  card; when nothing plays, a **Play music** card lists every app that can play.
- **System widget:** RAM, storage, CPU clock and network speed, each with its icon.
- **Recent apps** screen: open or close recent apps, or Close all.

### Settings
- A redesigned settings page (grouped cards, Lucide icons, real switches): your name, photo slideshow,
  slow zoom, how often photos change, **Arrange home** (move sections, sizes S / M / L, show or hide), and
  hidden apps.

### Features of the original, extended
- **Retro TV:** added the **'60s** and **'70s** next to the '80s to '10s.
- **Radio:** **+ Add station** (name and stream link), saved on the TV.
- **The World's Windows:** grid fixed so cards in the edge columns are never cut; shared focus ring.
- **Little Office:**
  - **Gemini (Antigravity)** alongside Claude Code and Codex.
  - **More than one computer:** the TV finds every computer running Little Office (Mac and Windows PC)
    and merges them. Windows installer included.
  - An honest card: the last task per agent, and "Not on this computer" when an agent is not installed.
  - A full screen panel: online computers, a spotlight on the agent that matters now (icon, pulsing halo
    while working, status), one card per agent, and "Needs you" beside "Recent activity".

### Cleanup and speed (for the Mi TV Stick)
- Removes Projectivy and old shortcut wrappers, protects the Amlogic system services, switches off Xiaomi
  and Google ads and telemetry, faster animations, an optional offline photo screensaver. Fully reversible.

### Tooling
- `scripts/buchi.sh`: one entry point (`check`, `build`, `plan`, `install`, `clean`, `tune`, `photos`,
  `update`, `undo`).
- `scripts/diag.sh` and `scripts/shot.sh`: screenshots and logs from the TV for debugging.
- Builds with **Java 25** (needs build-tools 36, see Troubleshooting).

---

## What stays from the original

Everything in [`docs/ORIGINAL-README.md`](docs/ORIGINAL-README.md): the launcher architecture, Retro TV
(programming from [MyRetroTVs](https://www.myretrotvs.com/)), Radio, The World's Windows, Little Office,
the debloat approach, `backup/restore.sh`, and the phase guides in [`docs/`](docs/).

---

## Requirements: Mac

Tested on an **Intel Mac without Homebrew**. Apple Silicon works the same way.

| What | Why | How to get it |
|---|---|---|
| **Java JDK 17 or newer** (25 tested) | compiles the launcher | [adoptium.net](https://adoptium.net): Temurin, macOS, x64 (Intel) or aarch64 (Apple Silicon), `.pkg` |
| **Android command line tools** | installs the SDK pieces below | developer.android.com: "Command line tools only", Mac zip |
| **Android SDK: platform-tools, build-tools 36, platform 34** | adb, d8, aapt2, apksigner, android.jar | one command, below |
| **python3** | scripts | comes with macOS (Xcode command line tools) |
| curl, zip, unzip | scripts | built into macOS |
| ffmpeg (optional) | smaller preview images | not required |

Install the SDK (adjust the zip name if yours differs):

```bash
S=~/Library/Android/sdk
rm -rf /tmp/ct; mkdir -p $S/cmdline-tools
unzip -q ~/Downloads/commandlinetools-mac*.zip -d /tmp/ct && mv /tmp/ct/cmdline-tools $S/cmdline-tools/latest
yes | $S/cmdline-tools/latest/bin/sdkmanager --sdk_root=$S "platform-tools" "build-tools;36.0.0" "platforms;android-34"
```

## Requirements: Windows

**Little Office** runs natively on Windows. **Building and installing** the launcher uses the bash
scripts, so on Windows run them inside **WSL 2** (Ubuntu), or use the Mac.

| What | Why | How to get it |
|---|---|---|
| **Python 3** | Little Office | [python.org](https://www.python.org/downloads/), tick **"Add python.exe to PATH"** |
| **PowerShell 5+** | Little Office installer | built into Windows 10 and 11 |
| **zeroconf** (Python package) | lets the TV find the PC | installed automatically by `install-windows.ps1` |
| WSL 2 + Ubuntu *(only to build)* | runs the bash scripts | `wsl --install` in an admin PowerShell |
| Java 17+, Android command line tools *(only to build)* | same as Mac | in Ubuntu: `sudo apt install openjdk-17-jdk unzip zip curl python3`, then the Linux command line tools and the same `sdkmanager` line with `--sdk_root=$HOME/Android/Sdk` |

Your Wi‑Fi network on Windows must be set to **Private** (Settings, Network, your Wi‑Fi, Private) so
the TV can reach it.

---

## First setup

On the TV: Settings, Device Preferences, About, press **Build** 7 times. Then Developer options, turn on
**USB debugging** (and **Network debugging** if listed). Note the TV's IP address (About, Status).

```bash
git clone https://github.com/Cannon-Leo/Steroid-TV-modified-by-Onyebuchi-.git steroid
cd steroid
scripts/connect.sh <TV-IP>          # accept "Allow debugging" on the TV, tick Always allow
scripts/buchi.sh check              # tools ready?
scripts/buchi.sh build              # builds launcher/out/SteroidHome.apk
scripts/buchi.sh plan               # shows what cleanup would change, changes nothing
scripts/buchi.sh install            # installs, removes Projectivy, makes Steroid the Home app
scripts/buchi.sh clean              # debloat (writes backup/restore.sh first)
little-office/install.sh            # Mac: agent status for the TV (optional)
```

On the TV, choose **Allow** when it asks about TV listings.

## Everyday commands

| Command | What it does |
|---|---|
| `scripts/buchi.sh update` | build, install, refresh Little Office, take diagnostics: the usual after a change |
| `scripts/buchi.sh photos <folder>` | push your photos (and `profile.jpg`) to the TV |
| `scripts/buchi.sh tune` | offline photo screensaver, ad id off |
| `scripts/diag.sh` | 11 screenshots and logs from the TV into `diag/` |
| `scripts/shot.sh` | one screenshot of the TV right now: `diag/now.png` |
| `scripts/buchi.sh undo` | put everything back (re-enables apps and the stock launcher) |

---

## Little Office on Mac and Windows

The TV shows what your coding agents are doing: **Claude Code**, **Codex** and **Gemini (Antigravity)**.
Read only. Only project names, states and one short sentence leave the computer, and only to devices on
your home network.

- **Mac:** `little-office/install.sh` (runs at login via a LaunchAgent).
- **Windows:** copy the `little-office` folder to the PC, open **PowerShell as administrator** in it, and run:

  ```powershell
  Set-ExecutionPolicy -Scope Process Bypass; .\install-windows.ps1
  ```
  It installs `zeroconf`, opens port 8765 for **private networks only**, and starts at every sign in
  (Task Scheduler, task "Little Office").

Each computer announces itself under its own name, so the TV sees all of them at once.

**What it reads:** Claude Code logs in `~/.claude/projects`, Codex in `~/.codex`, and Antigravity's task
list in `~/.gemini/antigravity/conversation_summaries.db`. Claude sessions that run in the cloud (the
Claude app) leave nothing on the computer, so the card says the app is open but cannot show their status.
Run Claude Code in a terminal to get full status.

---

## Your photos and profile picture

Put your photos (jpg, png or webp) in one folder, plus a square photo named **`profile.jpg`** for the
picture next to the greeting, then:

```bash
scripts/buchi.sh photos ~/Pictures/tv
```

They join the slideshow immediately (your photos come first in each loop). Settings, **Photo slideshow**,
**Slow zoom** and **Change photo every** control it.

---

## Troubleshooting

Every issue below actually happened while building this, and each has its fix.

| Problem | Fix |
|---|---|
| **Homebrew will not install / `brew: command not found`** (Intel Mac) | Not needed. Install Java from adoptium.net and the SDK with the commands in [Requirements: Mac](#requirements-mac). |
| **`mv: rename ... /usr/local/bin/android: No such file or directory`** | That installer (Android CLI) needs `/usr/local/bin`. Skip it and use the command line tools zip instead. |
| **The SDK command "does not work" with the downloaded zip** | The zip is named `commandlinetools-mac_x86_64-...`; use the pattern `commandlinetools-mac*.zip` as above. |
| **Build fails: `NullPointerException: Cannot invoke "String.length()"` in `d8`** | build-tools 34 cannot read classes compiled by Java 25. Install build-tools 36: `sdkmanager --sdk_root=$HOME/Library/Android/sdk "build-tools;36.0.0"`. The scripts pick the newest automatically. |
| **`WARNING: A restricted method in java.lang.System has been called`** | Harmless Java 25 notice. Ignore it. |
| **`adb: device '192.168.x.x:5555' not found`** (after a power cut) | The stick may have a new IP. Wake the TV and reconnect by its network name: `IP=$(ping -c1 <stick-id>.local \| sed -n 's/.*(\([0-9.]*\)).*/\1/p'); scripts/connect.sh $IP`. Find the name with `dns-sd -Z _googlecast._tcp local` (look for the `SRV` line). Reserve the IP in your router to avoid this. |
| **`failed to connect ...: No route to host`** | TV asleep, a different Wi‑Fi network, or router client isolation on. Wake the TV and check `ping <TV-IP>`. |
| **`zsh: event not found`** | A `!` in a pasted command. Use a version without `$!`. |
| **`Home app not switched (got com.google.android.tvlauncher/...)`** | Android TV keeps its own launcher first. The current `install` handles it: it checks Steroid opens, then switches the stock launchers off. If Steroid ever breaks: `adb shell pm enable com.google.android.tvlauncher`. |
| **`zsh: permission denied: scripts/xyz.sh`** | `chmod +x scripts/*.sh little-office/*.sh` |
| **Recently played rows are empty** | They fill as you play things. Android does not share any app's own Continue Watching with launchers that are not part of the system (it returns 0 items), so Steroid keeps its own history. Check the listener permission: `adb shell cmd notification allow_listener tv.steroid.home/tv.steroid.home.NotifListener`. |
| **Music widget empty or controls do nothing** | Same listener permission as above, then press Home once. |
| **Recent apps screen is empty** | `adb shell appops set tv.steroid.home GET_USAGE_STATS allow` |
| **Little Office: "Looking for the computer..."** | Mac: run `little-office/install.sh` again and allow incoming connections for Python. Windows: the network must be **Private**; rerun `install-windows.ps1` as administrator. Check `curl http://127.0.0.1:8765/status` on the computer. |
| **Little Office says "All quiet" while Claude is working** | Claude app sessions run in the cloud, so there is nothing local to read. Use Claude Code in a terminal for live status. |
| **YouTube stops when I leave it** | The YouTube TV app pauses itself when hidden; launchers cannot override that. The widget's play button reopens YouTube at the same video. Spotify and YouTube Music keep playing in the background. |
| **"Up next: permission refused" during `install`** | Harmless if you already allowed it on the TV. |
| **Need to undo everything** | `scripts/buchi.sh undo` |

---

## Honest limits

- A launcher cannot remove ads inside apps (YouTube, Prime...).
- Android TV 11 cannot blur live, so glass is blurred once per photo (cheap, but it does not follow motion).
- Recommendations ("For you") are not available to launchers that are not part of the system; only your
  own play history is.
- Close in the music widget pauses the app and frees its memory when Android allows it; it cannot force
  stop other apps.
- CPU shows clock speed, not a usage percentage (Android blocks reading CPU load); the temperature sensor
  is blocked on this stick.

---

## Licences and credits

- **Steroid TV** by **Sanchit Singhal**, MIT licence: <https://github.com/sanchit0212s/steroid-tv>.
  His copyright notice is kept in [`LICENSE`](LICENSE), as the MIT licence requires.
- **Modifications** in this repository by **Onyebuchi Omoigberale** ([@Cannon-Leo](https://github.com/Cannon-Leo)),
  also under MIT.
- **Icons:** [Lucide](https://lucide.dev), ISC licence ([`launcher/LUCIDE-LICENSE.txt`](launcher/LUCIDE-LICENSE.txt)).
- **Fonts:** Instrument Serif, Hanken Grotesk, DM Mono, SIL Open Font Licence (in `launcher/assets/fonts/`).
- **Retro TV programming:** [MyRetroTVs](https://www.myretrotvs.com/) by Joey Cato.
- **Radio streams and World's Windows videos:** their creators, credited in the original README; nothing
  is hosted or redistributed here.

Thank you, Sanchit, for building something this good to start from.
