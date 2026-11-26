# Philips 55OLED808: cleanup and new home screen (2026-11-26)

Philips Google TV TA1, board PH1M_WW_9972, Google TV 12 (SDK 31), build STT2.230831.001. On Wi-Fi.

## Versions

Each finished state is a git tag on the `philips-55oled808` branch: `v1.0` is the first. What changed in each
one is in `my-tv/CHANGELOG.md`. To make the next version after a change:

```powershell
git add -A my-tv launcher addons/weather
git commit -m "my-tv: what changed"
git tag -a v1.1 -m "v1.1: what changed"
git push origin philips-55oled808 v1.1
```

Add a `## v1.1` entry at the top of `CHANGELOG.md` in the same commit. To go back to an older version:
`git checkout v1.0`, then `my-tv\setup-tv.ps1 -SkipDebloat` puts that version's apps on the TV
(`git checkout philips-55oled808` returns to the newest).

## Your location (kept out of the files)

The sky, sun and moon follow the real sunrise and sunset where the TV is. The coordinates are never written
into this repository; the launcher build reads them from two environment variables (decimal degrees):

```powershell
[Environment]::SetEnvironmentVariable('TV_LATITUDE',  '50.0', 'User')
[Environment]::SetEnvironmentVariable('TV_LONGITUDE', '8.0',  'User')
```

Open a new terminal afterwards. Or pass them for one run: `setup-tv.ps1 -Latitude 50.0 -Longitude 8.0`.
Unset, the sky uses a fixed 07:00 to 19:00 day. The Weather app's city is chosen in the app on the TV.

## Redo everything in one go

```powershell
powershell -File my-tv\setup-tv.ps1 -TvHost <tv ip>
```

Debloats, builds and installs the Weather app and the launcher, turns on the Home-key helper and checks the
result. Safe to run again (after a factory reset, a firmware update, or on another 55OLED808).
`-SkipDebloat` only reinstalls the apps; `-SkipWeather` leaves the Weather app out.
Needs once on the PC: Python 3, JDK 17 and the Android SDK (platform 34, build-tools 34.0.0) in
`%LOCALAPPDATA%\Android\Sdk`, e.g. with Google's command-line tools:
`sdkmanager "platforms;android-34" "build-tools;34.0.0"`.

| File | What it is |
|---|---|
| `my-tv/setup-tv.ps1` | Redo the whole setup (above) |
| `my-tv/CHANGELOG.md` | What changed in each version (v1.0, v1.1, ...) |
| `my-tv/debloat.txt` | The 37 packages that were **disabled** (never uninstalled), with what each one is |
| `my-tv/batch1-3.txt` | The same list in the three batches it was applied in |
| `my-tv/restore.ps1` | Undo everything: re-enables the 37 packages and removes the new launcher |
| `my-tv/install-launcher.ps1` | Rebuild and reinstall the home screen after editing its config |
| `backup/20261126-2342/` (local only, never shared: holds serials and settings) | Package lists, getprop, all settings and display values from before any change |
| `shots/` (local only) | Screenshots pulled from the TV at each step |

All commands run from the repository root (this folder's parent) in PowerShell.

## Turning ADB on again (needed for every script here)

ADB was switched off at the end for safety. To use any script: TV Settings > System > Developer options >
USB debugging **on** (and Network debugging if shown). Accept "Allow USB debugging?" on the TV the first time.
Switch it off again when you're done: port 5555 is plaintext and anyone on your network could use it.

## What changed

- **Disabled**: TP Vision analytics (candeebug, gel, frameworklogger, log collector), Samba TV viewing-data
  collection, Google feedback/partner attribution/auto-installs, NFT gallery, Play Games, calendar sync,
  Play-Fi, Alexa, Control4, Matter, screenshot share, printing, the Google TV films app, the Vewd browser,
  AirPlay, and broadcast-TV extras (HbbTV, teletext, EPG, CI+, operator apps, channel installers).
- **Kept**: everything for HDMI and the input menu, Play Store and Play Services, keyboard, remote and
  Bluetooth, Google Assistant, Chromecast, Netflix, firmware updates, Ambilight, Mimi sound, the screensaver,
  and all apps you installed yourself.
- **Already disabled before**, left as they were: YouTube, Prime Video, YouTube Music, Rakuten, ivi and
  regional config packages.
- **Home screen**: the launcher from `launcher/` (`com.gathin.tvhome`). Google TV's launcher and setup wizard
  stay **enabled** because the setup wizard holds the Chromecast credentials; the launcher's Home-key
  helper (accessibility service "HomeKeyService") catches the Home button instead.
  Trade-off you chose: holding Volume moves one step per press.
- **Remote buttons** (`app_keys` in `config.xml`, handled by HomeKeyService):
  YouTube (scancode 776) opens TizenTube, Rakuten TV (770) opens Emby, the Philips menu button (765) opens
  the Philips Quick Menu. Holding Home opens Google TV's panel (notifications, Sources, Picture, Wi-Fi,
  Bluetooth, Sleep timer); a tap opens the new Home. Philips' GlobalKey also sends YouTube/Rakuten to Google
  TV's launcher, which opens a Play Store page; the `later:` prefix (a small local change in `RemoteKeys.java`)
  opens the app 0.1 s after the press and brings it forward again at 0.5 and 0.9 s so it stays on top.
  Home is `KEYCODE_HOME:library` (opens this launcher directly) and `hold:KEYCODE_HOME:notifications` (a
  `notifications` action word added locally, the accessibility "open notifications" action).
  Netflix and Source work as stock.
- **Background**: a time-of-day sky for the TV's location (`sky_intensity` 60; latitude and longitude come from the
  environment variables `TV_LATITUDE` / `TV_LONGITUDE` at build time, see below), app-colour glow off (`ambient_glow` false). It follows the sun, not the weather: the
  launcher has no internet access.
- **Weather**: the Weather add-on (`addons/weather`, package `io.github.sumyabazar1.weather`) is installed: a
  tile/card that opens a full-screen forecast from Open-Meteo (no account; it sends only the city you search and
  its coordinates). Remove it with `pm uninstall io.github.sumyabazar1.weather`.
- **Weather on Home** (local changes to both apps, not in the upstream repo):
  - The Weather app has a read-only `NowProvider` (`addons/weather/.../NowProvider.java`), locked with a
    signature permission so only apps signed with this PC's key can read it. When Home asks and the reading is
    older than 30 minutes, it fetches once; so it goes online only while Home is on screen, at most every 30 min.
  - The launcher (`WeatherNow.java`, `Sky.weather()`, `SkyView`) tints the sky by the weather (overcast greyer,
    rain darker blue-grey with faint streaks, snow lighter with flakes, fog washed out, storm violet) and shows
    "0° · Partly cloudy" under the date. The launcher itself still has no internet access.
  - Animated layer (`WeatherView.java`), drawn only while Home is in front, max 30 fps: rain (drizzle to heavy,
    icier when freezing), snow (light to heavy, snow grains), storm with lightning and hail, drifting clouds
    (one for mainly clear, a few for partly cloudy, a deck for overcast), low fog bands, by day a soft sun that
    rises on the left, stands high at noon and sets on the right (golden with a haze in the morning and evening,
    from the real sunrise and sunset at `TV_LATITUDE`/`TV_LONGITUDE`), by night tonight's moon in its real phase with twinkling stars.
  - Preview any weather over ADB for 2 minutes:
    `tools\tv.py "am start -n com.gathin.tvhome/.MainActivity --ei weather_preview 63 --ez weather_day true --ei weather_minute 480"`
    (63 = rain; WMO codes: 0 clear, 1 mainly clear, 2 partly cloudy, 3 overcast, 45 fog, 51-57 drizzle, 61-67 rain,
    71-77 snow, 80-82 showers, 85-86 snow showers, 95-99 storm; `weather_minute` 480 = 08:00).
  - Off switch: empty `weather_provider` in `config.xml` and reinstall. Change the city in the Weather app.
  - Rebuild order: Weather app first (it defines the permission), then `my-tv\install-launcher.ps1`.
- **DNS**: not changed. Your AdGuard Home on the LAN already filters the TV; Android Private DNS would bypass it.
  (If you ever want it: `settings put global private_dns_specifier dns.adguard-dns.com` then
  `settings put global private_dns_mode hostname`; undo with `settings put global private_dns_mode opportunistic`.)

## Re-enable one app

On the TV: Settings > Apps > See all apps > Show system apps, pick the app, **Enable**. Or over ADB:

```powershell
$env:TV_HOST='<tv ip>'; tools\venv\Scripts\python.exe -I tools\tv.py 'pm enable --user 0 com.mediatek.AirplayAPK'
```

Then put a `#` in front of its line in `my-tv/debloat.txt` so a later run doesn't disable it again.
(AirPlay is three packages: `com.mediatek.AirplayAPK`, `com.mediatek.airplaydaemon`, `com.mediatek.partner.airplay_sys`.)

## Undo everything

```powershell
powershell -File my-tv\restore.ps1 -TvHost <tv ip>
```

`-KeepLauncher` re-enables the apps but keeps the new home screen. Reboot the TV once afterwards.

## Change the dock order or size

On the TV, no computer needed: on any tile **hold OK** (or press Menu) > **Move**, then Left/Right/Up/Down,
OK to drop, Back to finish. "Add to dock" / "Replace in dock" swap apps in and out.

To set it in the config instead, edit `launcher/app/src/main/res/values/config.xml`:

- `dock_apps`: the packages in dock order (now JellyWatch, Emby, TizenTube, Netflix, Spotify, Tidal, SkyShowtime)
- `columns`: dock slots and grid columns (now 7)

then run `powershell -File my-tv\install-launcher.ps1 -TvHost <tv ip>`. A changed `dock_apps` replaces any
order you set on the TV.

## Change the wallpaper

Make a folder, e.g. `my-tv\mytheme\`, with a `config.json` containing `{}` (or any keys from `themes/README.md`)
and your picture as `wallpaper.jpg` (1920x1080), then:

```powershell
tools\theme.ps1 -TvHost <tv ip> my-tv\mytheme
```

Ready-made themes are in `themes/` (e.g. `tools\theme.ps1 -TvHost <tv ip> themes\cinematic`).
`tools\theme.ps1 -TvHost <tv ip> -Reset` goes back to plain black.
