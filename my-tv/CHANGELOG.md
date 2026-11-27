# Changelog

Versions of this TV setup, newest first. Each one is a git tag (`v1.0`, `v1.1`, ...) on the
`philips-55oled808` branch; README.md, "Versions", says how to make the next one.

## v1.1 (2026-11-27)

- `my-tv/PROMPT.md`: a prompt for an AI assistant to apply this setup to another 55OLED808, asking about the
  apps to keep, the dock, the remote buttons, the location and the weather.

## v1.0 (2026-11-27)

First version, on a Philips 55OLED808 (Google TV 12, STT2.230831.001).

- Debloat: 37 packages disabled in three batches (telemetry, Philips extras, AirPlay and broadcast TV);
  Assistant, Chromecast, Netflix and firmware updates kept.
- Launcher: 7-app dock (JellyWatch, Emby, TizenTube, Netflix, Spotify, Tidal, SkyShowtime), Google TV's
  launcher kept for Chromecast with the Home key caught by the helper.
- Remote: YouTube opens TizenTube, Rakuten opens Emby, the Philips menu button opens the Quick Menu,
  holding Home opens the notifications panel.
- Weather: the Weather add-on shares its reading; Home shows "0° · Partly cloudy" under the date and animates
  rain, snow, storm, clouds, fog, the sun on its arc and tonight's moon with stars.
- Location from the `TV_LATITUDE` / `TV_LONGITUDE` environment variables, never stored in the files.
- Scripts: `setup-tv.ps1` (everything), `install-launcher.ps1`, `restore.ps1`.
