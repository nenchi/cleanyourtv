# Prompt: put this Philips 55OLED808 setup on your own TV

Paste everything below the line into Claude Code (or another coding assistant with shell access) on a
Windows PC on the same network as the TV. It applies the setup from the `philips-55oled808` branch of
https://github.com/nenchi/cleanyourtv: the debloat list, the Apple TV style launcher with the animated
weather sky, and the remote-button fixes. It asks you about everything that depends on you.

Before you start, on the TV: Settings > System > About > press OK on "Build" 7 times, then go back >
Developer options > turn on USB debugging (and Network debugging if shown). Keep the TV on.

---

I want to apply the Philips 55OLED808 setup from https://github.com/nenchi/cleanyourtv (branch
`philips-55oled808`) to my TV. Work on the real TV, step by step, verify with screenshots from the TV,
and ask me before anything that depends on my preferences.

Get the files
- Clone the repo (`git -c core.longpaths=true clone https://github.com/nenchi/cleanyourtv`), list its tags
  with `git tag -l "v*"`, and check out the newest one (or the one I name). Read `my-tv/README.md`,
  `my-tv/CHANGELOG.md` and the repo's `AGENTS.md` and `PROMPT.md` first.
- Every safety rule in the repo's `PROMPT.md` and `AGENTS.md` applies here too: disable, never uninstall;
  back up first; never disable Android's core packages, the TV's input/HDMI services, Play Store or Play
  Services; never start a factory or test package; close ADB at the end. If a rule there and a step here
  disagree, the rule wins.

Check it is the same TV
- Connect over ADB on port 5555 (`tools/tv.py --probe`, with `TV_HOST` set to the TV's IP; find it in my
  router's list, the PC's ARP table, or ask me). Tell me when the "Allow USB debugging?" prompt appears.
- This setup was made on `ro.product.device=PH1M_WW_9972`, Google TV 12 (SDK 31), build `STT2.230831.001`.
  - Same device and build: go on.
  - Same device, other build (for example Google TV 14 / `UKN2.241117.001`): tell me, compare
    `my-tv/debloat.txt` with `examples/philips-google-tv-ta1/` and `examples/philips-55oled708/`, and
    only use lines whose package exists on my TV.
  - Another device: stop and tell me to use the repo's main `PROMPT.md` instead.

Back up
- Before any change, save the package lists, disabled list, getprop and all settings to `backup/`, as the
  repo's `PROMPT.md` describes. Ask me what is plugged into the USB ports, and whether the network comes
  through a USB Ethernet adapter.

Ask me (checklist questions)
1. Which of these I use, so their lines in `my-tv/debloat.txt` get a `#` and stay enabled: AirPlay,
   broadcast TV by antenna/cable/satellite (teletext, EPG, HbbTV, CI+, channel installers, operator apps),
   the Google TV films app, the Vewd browser, Philips Play-Fi, Alexa, Control4, Matter. Assistant,
   Chromecast, Netflix and firmware updates are kept by this setup anyway.
2. My dock: how many slots (4 to 7) and which of my installed apps in which order. Set `columns` and
   `dock_apps` in `launcher/app/src/main/res/values/config.xml`. The ones there now are the original
   owner's apps; any I don't have are skipped.
3. The remote's app buttons (`app_keys` in the same file). This setup maps YouTube (scancode 776) to
   TizenTube and Rakuten TV (scancode 770) to Emby, with the `later:` prefix because Philips also sends
   them to Google TV's launcher. Ask what each button should open on my TV; if I don't have the app,
   remove that line. Keep the Philips menu button (765) and the Home entries.
4. My location for the sky, sun and moon, as decimal degrees rounded to two places. Set them as
   `TV_LATITUDE` / `TV_LONGITUDE` environment variables (user level) or pass `-Latitude`/`-Longitude`;
   never write them into a file in the repo.
5. Whether I want the weather on Home (the Weather add-on, which goes online only while Home is shown, at
   most every 30 minutes). If not, run with `-SkipWeather` and leave `weather_provider` empty.
6. Whether my router already filters DNS (Pi-hole, AdGuard Home). This setup changes no DNS settings.

Apply
- Install once if missing: Python 3, JDK 17, and the Android SDK command-line tools with
  `platforms;android-34` and `build-tools;34.0.0` in `%LOCALAPPDATA%\Android\Sdk` (ask me before
  downloading, with the size).
- Run `powershell -File my-tv\setup-tv.ps1 -TvHost <tv ip>`. It disables the list with the repo's safety
  checks (`tools/disable.ps1`), builds and installs the Weather app, then builds and installs the launcher
  with Google TV's launcher kept for Chromecast and the Home-key helper on.
- If a step fails, stop and show me the message; don't retry in a loop.
- If the Weather app is new, ask me to open it once and pick my city.

Verify
- Screenshot Home (`tools/tv.py --screenshot shots/home.png`), check the dock, D-pad (Up/Down one row,
  Up from the dock to the top bar) and the Inputs menu.
- Reboot once (wait 15 s after the last change first) and check `pm list packages -d` against the list,
  and that Home comes back to the new launcher (`dumpsys activity activities | grep mFocusedApp`).
- Ask me to press the real remote buttons: Home (tap and hold: hold opens the notifications panel),
  YouTube, Rakuten, Netflix, Source, the Philips menu button, the mic. Holding Volume moves one step per
  press with this setup; tell me that up front.
- Preview the weather looks if I want: `tools/tv.py "am start -n com.gathin.tvhome/.MainActivity --ei
  weather_preview 63 --ez weather_day true"` (codes in `my-tv/README.md`).

Finish
- Give me `my-tv/restore.ps1` (undo everything) and point me to `my-tv/README.md`.
- Close ADB: ask me to revoke USB debugging authorisations and switch Network debugging and USB debugging
  off (or Developer options off), restart the TV, then check with `tools/adb-closed.py <tv ip>` that port
  5555 refuses the connection. Only that check counts.
