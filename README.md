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
3. [What you need](#what-you-need)
4. [Install, step by step](#install-step-by-step)
5. [Everyday commands](#everyday-commands)
6. [Little Office on Mac and Windows](#little-office-on-mac-and-windows)
7. [Your photos and profile picture](#your-photos-and-profile-picture)
8. [Troubleshooting](#troubleshooting)
9. [Honest limits](#honest-limits)
10. [Licences and credits](#licences-and-credits)

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
- **Control centre** (the gear), in the style of Xiaomi HyperOS 4 on frosted glass, all Lucide icons:
  Wi‑Fi and Bluetooth tiles that turn light when on, a now playing card beside two tall sliders (volume,
  and how dark the background photo is), a capsule of shortcuts (Recent apps, Inputs, Display, All
  settings), round toggles that turn light when on (Screensaver, Mute, Glass, Mini player) and buttons
  (Launcher settings, Apps, Date and time, Privacy), and a Tour pill. Round buttons have no labels, as on
  HyperOS: the line under the date names whatever is selected. Hold OK on a toggle for its settings.
- **Notification centre** (the bell at the top, with a dot when something is new): every Little Office
  notice you may have missed, newest first; requests waiting for approval at the top with Approve and
  Reject right there; OK opens Little Office, hold OK clears one, **Clear all** clears them all.
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

## What you need

- **A TV with Android TV 8 or newer** (Google TV and Mi TV Stick, Mi Box, Chromecast with Google TV,
  Android TVs). Built and tested on a Xiaomi Mi TV Stick 4K.
- **A Mac** (Intel or Apple Silicon) on the **same Wi‑Fi** as the TV. A Windows PC can run Little Office;
  to build the launcher on Windows, use WSL (see the end of this section).
- About **20 minutes** and **3 GB** of free space.

## Install, step by step

### 1. Put two tools on the Mac (once)

1. **Java**: go to [adoptium.net](https://adoptium.net), download **Temurin** for macOS (the `.pkg`) and
   open it. Click through the installer.
2. **Android tools**: go to [developer.android.com/studio](https://developer.android.com/studio#command-line-tools-only),
   scroll to **Command line tools only** and download the **Mac** zip. Leave it in Downloads.
3. Open **Terminal** (Spotlight, type Terminal) and paste this, then press Enter. It sets up the Android
   pieces the build needs (it asks nothing; it takes a few minutes):

   ```bash
   S=~/Library/Android/sdk; mkdir -p $S/cmdline-tools; rm -rf /tmp/ct
   unzip -q ~/Downloads/commandlinetools-mac*.zip -d /tmp/ct && mv /tmp/ct/cmdline-tools $S/cmdline-tools/latest
   yes | $S/cmdline-tools/latest/bin/sdkmanager --sdk_root=$S "platform-tools" "build-tools;36.0.0" "platforms;android-34"
   ```

Python is already on your Mac. Nothing else is needed.

### 2. Let the Mac talk to the TV (once)

On the TV:
1. **Settings → Device Preferences → About**, then press OK on **Build** seven times. It says you're a developer.
2. Go back: **Device Preferences → Developer options**, turn on **USB debugging** (and **Network debugging**
   if you see it).
3. Write down the TV's **IP address**: **Device Preferences → About → Status**.

### 3. Download this project and connect

In Terminal (put your TV's IP address in place of `192.168.1.50`):

```bash
cd ~/Public
git clone https://github.com/Cannon-Leo/Steroid-TV-modified-by-Onyebuchi-.git steroid
cd steroid
scripts/connect.sh 192.168.1.50
```

The TV asks **"Allow debugging?"**: tick **Always allow**, then choose **OK**.

### 4. Install everything with one command

```bash
scripts/buchi.sh all
```

That's it. It builds the launcher, installs it on the TV, makes it the home screen, sets up Little Office,
sends your photos (see [Your photos](#your-photos-and-profile-picture)) and turns on the photo screensaver.
On the TV, choose **Allow** if it asks about TV listings. Run the same command again any time; it's safe.

### Optional extras

| Want to… | Run |
|---|---|
| Remove ads, trackers and apps you never use (writes an undo file first) | `scripts/buchi.sh plan` to see, then `scripts/buchi.sh clean` |
| Add or change your photos | put them in `personal/photos`, then `scripts/buchi.sh myphotos` |
| Check that Little Office reaches the TV | `scripts/buchi.sh office` |
| Put everything back as it was | `scripts/buchi.sh undo` |

### On a Windows PC

- **Little Office** (agent status, notices and approvals from this PC): install
  [Python](https://www.python.org/downloads/) and tick **"Add python.exe to PATH"**. Copy the `little-office`
  folder to the PC, right click **PowerShell → Run as administrator**, go to that folder and run
  `Set-ExecutionPolicy -Scope Process Bypass; .\install-windows.ps1 -TV <your TV's address>` (Settings →
  Network on the TV shows it). Only your TV can reach the PC, and the Wi‑Fi can stay **Public**. Without `-TV`,
  set your Wi‑Fi to **Private** instead.
- **Building the launcher on Windows**: run `wsl --install` in an administrator PowerShell, open Ubuntu,
  run `sudo apt install openjdk-17-jdk unzip zip curl python3`, download the **Linux** command line tools,
  and use step 1's last line with `$HOME/Android/Sdk` instead of `~/Library/Android/sdk`. Then steps 3 and 4.

---

## Everyday commands

| Command | What it does |
|---|---|
| `scripts/buchi.sh all` | everything in one go (safe to repeat) |
| `scripts/buchi.sh update` | build, install, refresh Little Office, take diagnostics: the usual after a change |
| `scripts/buchi.sh myphotos` | send the photos in `personal/photos` (and `profile.jpg`, your avatar) to the TV |
| `scripts/buchi.sh screensaver` | make Steroid photos the TV's screensaver |
| `scripts/buchi.sh office` | check Little Office end to end (`office approve` also tests an approval) |
| `scripts/buchi.sh pip full` | a mini player stuck on screen: back to full screen (`pip close` closes it) |
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
  Set-ExecutionPolicy -Scope Process Bypass; .\install-windows.ps1 -TV 192.168.1.51
  ```
  Use your TV's address. It adds pip if Python came without it, installs `zeroconf`, lets **only the TV**
  reach port 8765 on any network, and starts at every sign in (Task Scheduler, task "Little Office").
  Leave out `-TV` to allow any device on **private** networks instead. Fix the TV's address in your router
  (DHCP reservation) so it never changes. The pairing code pops up on top of every window.

  Everyday commands afterwards, in any PowerShell window: `& "$HOME\.little-office\lo.ps1" status`
  (also `start`, `restart`, `test` for a mock approval on the TV, `tv <address>` after the TV gets a new
  address, and `connect` to reach the TV with adb from the PC).

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

### Antigravity and Codex

Antigravity has no hooks, so Little Office watches each task's own file: while it changes the task is
**working**; when it stops, a **finished** notice goes to the TV; a step left part way through means it's
**waiting on you**. Codex sends a **finished** notice through its `notify` setting, which `hooks.py` adds to
`~/.codex/config.toml` (even before Codex is installed). Neither has a way for anything outside it to
approve its actions, so TV approvals stay Claude Code only.

### Screensaver

**Steroid photos**, in the style of a HyperOS lock screen: your photos woven through the free photos and
views at full colour, the date and **weather** (Open-Meteo, no account; place from your internet address or
Launcher settings, Weather place) over a large clock, and the **depth effect**: on photos with a clear
subject, the subject stands in front of the clock. Depth masks are made on the computer by
`little-office/depth.py` (rembg, once: `pip3 install "rembg[cpu]"`), automatically by `buchi.sh myphotos`,
only for photos where the subject is solid and leaves the time readable. Turn depth off in Launcher
settings, Background. The clock is 12 hour with AM/PM and running seconds, the weather icon moves with the weather (the sun
turns, clouds drift, rain and snow fall, storms flash), and the contrast follows each photo: the brighter
the picture behind the clock, the deeper the shade and the firmer the shadows. The clock shifts a few
pixels each minute (no burn in). Turn it on with
`scripts/buchi.sh screensaver`. Google's Backdrop can't take your photos: Google decides what it shows.

### Something not reaching the TV?

`scripts/buchi.sh office` checks every link (service, Claude Code hooks, paired TVs, the TV's notice service
and permission), sends a test notice and shows what the TV logged. `buchi.sh office approve` also sends a
harmless test approval request. Notices and approvals come from **Claude Code running on the computer**;
sessions in the Claude app or on the web run in the cloud and can't reach your home network.

### Keep YouTube playing

Press Home while YouTube or YouTube Music plays: the same track carries on from the same second in the
music widget, then continues with YouTube's mix of similar tracks. Play, pause, next, previous and close
work from the widget; Open goes back into the app at the current track. Turn it off in Launcher settings
(**Keep YouTube playing**).

### Approve Claude Code requests from the TV

When Claude Code (in a terminal on the computer) is about to ask permission, the TV shows the request:
project, computer, a one line summary and **Reject** / **Approve**. Left and right choose, OK sends,
Back leaves it for the computer.

1. On the Mac: `little-office/approvals.sh on` (adds a `PermissionRequest` hook to `~/.claude/settings.json`,
   with a backup). `approvals.sh off` removes it.
2. On the TV: **Little Office → Approve from the TV → Pair this TV with …**, then type the 8 digit code that
   appears on the computer's screen. A pairing lasts 90 days; the TV warns you a week before and pairs again
   in one step.

Security: the code never crosses the network. Pairing first agrees a fresh secret with Diffie Hellman
(2048 bit), and the key is that secret plus the code, so recording the Wi‑Fi during pairing gives nothing to
guess at. 3 wrong codes end the attempt; every TV request is signed, time stamped and single use; the list
of requests the TV shows is signed back for that TV and that request, so nobody on the network can change
what you approve; private network only; tokens expire after 90 days. Pair at home, not on shared Wi‑Fi.

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
appears on the computer. Always decided at the keyboard, never sent to the TV: `sudo`, `rm -rf`, forced
pushes, `git reset --hard`, piping into a shell or interpreter, secrets (`.env`, `~/.ssh`, `~/.aws`, keys,
credentials), publishing (`npm publish`), destructive Windows commands, and anything that touches the
agent's own guardrails (Claude Code settings, Codex config, Little Office and its hooks).

Codex and Antigravity have no permission hook, so they can only notify the TV, not be approved from it.

---

## Your photos and profile picture

1. In the project folder, make a folder called **`personal/photos`** and put your photos in it
   (jpg, png or webp). Add a square photo named **`profile.jpg`**: it becomes the round picture next to
   the greeting.
2. Optional: a file **`personal/name`** with your first name on one line, for "Good evening, *name*".
   (Without it the TV says **User**, and you can type your name in Launcher settings.)
3. Run `scripts/buchi.sh myphotos`, then press Home on the TV. Your photos arrive within a minute.

How it works and what it keeps private:
- **Never on GitHub.** `personal/` is ignored by git, and `scripts/privacy-guard.sh` (run by
  `buchi.sh all`) adds a guard that refuses to commit or push anything in `personal/` or any image outside
  the app's own icons and the original screenshots.
- **Safe delivery.** Little Office on the Mac hands the photos only to your paired TV (signed requests), and
  the TV saves them in its own folder, so they always load.
- **Mixed slideshow.** Your photos alternate with 50 free photos the TV downloads itself (Unsplash
  licence: 16 from Unsplash's TV wallpaper search with no words or watermarks, 34 via Lorem Picsum) and the
  World's Windows views. Turn the free ones off in Launcher settings, Background.
- **Depth effect.** In the screensaver, photos with a clear subject get it in front of the clock (masks
  made on the Mac by `little-office/depth.py`).
- To leave a photo out, write its file name in **`personal/skip-photos`** (one per line).
- Keep the folder in the project, not in Downloads, Desktop or Documents: macOS can block Terminal there.

---

## Troubleshooting

Every issue below actually happened while building this, and each has its fix.

| Problem | Fix |
|---|---|
| **Homebrew will not install / `brew: command not found`** (Intel Mac) | Not needed. Install Java from adoptium.net and the SDK with the commands in [step 1 of the install](#1-put-two-tools-on-the-mac-once). |
| **`mv: rename ... /usr/local/bin/android: No such file or directory`** | That installer (Android CLI) needs `/usr/local/bin`. Skip it and use the command line tools zip instead. |
| **The SDK command "does not work" with the downloaded zip** | The zip is named `commandlinetools-mac_x86_64-...`; use the pattern `commandlinetools-mac*.zip` as above. |
| **Build fails: `NullPointerException: Cannot invoke "String.length()"` in `d8`** | build-tools 34 cannot read classes compiled by Java 25. Install build-tools 36: `sdkmanager --sdk_root=$HOME/Library/Android/sdk "build-tools;36.0.0"`. The scripts pick the newest automatically. |
| **`WARNING: A restricted method in java.lang.System has been called`** | Harmless Java 25 notice. Ignore it. |
| **`adb: device '192.168.x.x:5555' not found`**, **No route to host** or **Connection refused** | Run `scripts/buchi.sh connect` (no address needed). It retries, restarts adb, tries the TV's network name, then looks for it on your Wi‑Fi, and says exactly what to do: wake the TV, accept "Allow debugging?", or turn **Network debugging** back on. `scripts/buchi.sh update` does this first by itself. Reserve the TV's address in your router to avoid new addresses. Don't type `adb` yourself: use `scripts/buchi.sh adb <args>`. |
| **`failed to connect ...: No route to host`** | TV asleep, a different Wi‑Fi network, or router client isolation on. Wake the TV and check `ping <TV-IP>`. |
| **`zsh: event not found`** | A `!` in a pasted command. Use a version without `$!`. |
| **`Home app not switched (got com.google.android.tvlauncher/...)`** | Android TV keeps its own launcher first. The current `install` handles it: it checks Steroid opens, then switches the stock launchers off. If Steroid ever breaks: `adb shell pm enable com.google.android.tvlauncher`. |
| **`zsh: permission denied: scripts/xyz.sh`** | `chmod +x scripts/*.sh little-office/*.sh` |
| **Recently played rows are empty** | They fill as you play things. Android does not share any app's own Continue Watching with launchers that are not part of the system (it returns 0 items), so Steroid keeps its own history. Check the listener permission: `adb shell cmd notification allow_listener tv.steroid.home/tv.steroid.home.NotifListener`. |
| **Music widget empty or controls do nothing** | Same listener permission as above, then press Home once. |
| **Recent apps screen is empty** | `adb shell appops set tv.steroid.home GET_USAGE_STATS allow` |
| **Little Office: "Looking for the computer..."** | Mac: run `little-office/install.sh` again and allow incoming connections for Python. Windows: rerun `install-windows.ps1 -TV <your TV's address>` as administrator (or set the network to **Private**); if the TV's address changed, run it with the new one. Check `curl http://127.0.0.1:8765/status` on the computer. |
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
