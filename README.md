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

### First time tour
A guided tour on first launch (and on the first visit to Little Office): the screen dims, a spotlight glides
to each part with a short note on using it with the remote, and some steps let you try the thing for real.
Skip any time; replay it from the **Take the tour** card at the end of the home screen, the Control
centre, or the compass in Little Office.

### Settings
- A redesigned settings page (grouped cards, Lucide icons, real switches): your name, photo slideshow,
  slow zoom, how often photos change, **Arrange home** (move sections, sizes S / M / L, show or hide), and
  hidden apps, and header options (clock, seconds, date, year, greeting, system card, glass or solid).
- **Control Centre** (the gear), laid out like iOS and HyperOS: a grid of rounded modules on frosted glass.
  The connectivity module (Wi‑Fi, Bluetooth, Mute, Screensaver as round toggles that light up when on;
  hold OK for their settings) sits beside a now playing module (art, title, previous, play, next); then a
  tall volume slider (up and down, OK mutes) beside square shortcuts (Display, Recents, Inputs, Apps,
  Date & time, Settings), then Launcher, Glass (white when on), Tour and Privacy.
- **Mini player rescue:** when any app is left floating in a mini player (picture in picture), a bar at
  the top of the home screen says so, with **Full screen** (already selected: one OK) and **Close**. The
  Control centre has a **Mini player** button too, and pressing **Home** while you're already on the home
  screen pushes the mini player back up into its app. From the Mac: `scripts/buchi.sh pip full` or `pip close`.
- **Recent apps → Close all** closes everything: every player is stopped (including the background YouTube
  player and the radio), apps are freed from memory, the Resume card and the music widget clear, and the
  list starts empty; only apps you open afterwards appear again.

### Features of the original, extended
- **Retro TV:** added the **'60s** and **'70s** next to the '80s to '10s; the '00s and '10s start faster
  (ads and trackers blocked, the set boots as soon as its player loads, pages cached).
- **Live TV (new):** official 24 hour streams from each channel's own YouTube: Channels TV, Arise News,
  Al Jazeera, DW, France 24, Sky News, Bloomberg, Euronews and NASA. Up and down change channel, OK opens
  the guide. The current stream is looked up on each tune, so a channel that restarts its stream still works.
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

### Claude Code notices on the TV

When Claude Code needs you or finishes, on **any** computer running Little Office (every Mac and Windows
PC you installed it on), a small notice slides in from the right side of the TV over whatever you're
watching, then slides back by itself after a few seconds. OK opens Little Office. Approval requests work
the same way from every computer the TV is paired with (pair once per computer in Little Office).

The installers turn both on (`little-office/install.sh` on a Mac, `install-windows.ps1` on Windows), using
`little-office/hooks.py`, which works on Mac, Windows and Linux: `python3 hooks.py off` removes them,
`hooks.py on notify` or `hooks.py on approve` turns on just one. Only the project name and one sentence are sent.

### Keep YouTube playing

Press Home while YouTube or YouTube Music plays: the same track carries on from the same second in the
music widget, then continues with YouTube's mix of similar tracks. Play, pause, next, previous and close
work from the widget; Open goes back into the app at the current track. Turn it off in Launcher settings
(**Keep YouTube playing**).

### Claude Code notices on the TV

`little-office/install.sh` also runs `little-office/notify.sh on`, which adds Claude Code **Notification**
and **Stop** hooks (with a backup of `~/.claude/settings.json`). The moment Claude needs you or finishes,
a notice slides in from the right edge of the TV (project, state, one sentence), a thin line shows the time
left, and it slides back by itself after 8 seconds. OK opens Little Office; any other key closes it.
`notify.sh off` removes the hooks. This works for Claude Code running on the computer (terminal, the
desktop app or an editor); sessions that run in the cloud leave nothing on the computer to report.

### Approve Claude Code requests from the TV

When Claude Code (in a terminal on the computer) is about to ask permission, the TV shows the request:
project, computer, a one line summary and **Reject** / **Approve**. Left and right choose, OK sends,
Back leaves it for the computer.

1. On the Mac: `little-office/approvals.sh on` (adds a `PermissionRequest` hook to `~/.claude/settings.json`,
   with a backup). `approvals.sh off` removes it.
2. On the TV: **Little Office → Approve from the TV → Pair this TV with …**, then type the 8 digit code that
   appears on the computer's screen. A pairing lasts 90 days; the TV warns you a week before and pairs again
   in one step.

Security: the code never crosses the network (the TV proves it with an HMAC and receives its token
encrypted with it); 3 wrong codes end the attempt; every TV request is signed, time stamped and single use;
private network only; tokens expire after 90 days.

Guardrails on the computer:
- **Strict schemas** (Zod style, `little-office/schema.py`) for every body, header, id and query value:
  unknown fields, wrong types, bad lengths or formats are refused, nothing is coerced.
- **Rate limits** per address; pairing at most 4 tries per address and 10 per computer every 10 minutes;
  10 bad signatures in 10 minutes block that address for 15 minutes.
- JSON only, bodies capped at 8 KB, 10 second socket timeout, unknown methods refused.
- **Host checks** stop browser tricks (DNS rebinding) from reaching the service through a web page.
- One pairing at a time, at most 5 paired TVs, at most 20 waiting requests.
- **Audit log** at `~/.little-office/audit.log`: pairings, unpairings, every decision and every refusal.
- The TV checks everything it receives too: malformed ids and tokens are dropped, text is cut to size. `python3 ~/.little-office/office.py devices` lists
paired TVs and `office.py revoke all` removes them.

Safe by default: Reject is selected first; if the TV doesn't answer within 4 minutes the normal prompt
appears on the computer; commands like `sudo`, `rm -rf`, forced pushes, piping into a shell or touching
`.env` and `~/.ssh` are never sent to the TV and are always decided at the keyboard.

Codex and Antigravity have no permission hook, so they can only notify the TV, not be approved from it.

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
- YouTube and YouTube Music stop their own player when you leave them. The launcher carries the track on
  in its own background player (YouTube's official embedded player), so YouTube may play its own ads there,
  and a few tracks whose owners block embedding can't continue (the Resume card brings you back to them).
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
