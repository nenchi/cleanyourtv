# Build the home screen from launcher\ and (re)install it on the Philips 55OLED808.
# Run from the repository root:  powershell -File my-tv\install-launcher.ps1 -TvHost <tv ip>
# Edit launcher\app\src\main\res\values\config.xml first (dock, columns, wallpaper keys...).
# Needs JDK 17 and the Android SDK in %LOCALAPPDATA%\Android\Sdk (already installed on this PC),
# and ADB on the TV: Developer options > USB debugging on (+ Network debugging if shown).
param(
    [string]$TvHost = $env:TV_HOST,
    [string]$Latitude = $env:TV_LATITUDE,     # the TV's location for the sky, sun and moon (decimal degrees),
    [string]$Longitude = $env:TV_LONGITUDE    # read by app/build.gradle; never stored in the repository
)
$ErrorActionPreference = 'Stop'
if (-not $TvHost) { throw 'pass -TvHost <tv ip> or set $env:TV_HOST' }
$env:TV_LATITUDE = $Latitude
$env:TV_LONGITUDE = $Longitude
if (-not $Latitude -or -not $Longitude) {
    Write-Warning 'TV_LATITUDE / TV_LONGITUDE not set: the sky uses a fixed 07:00-19:00 day (see README.md).'
}
if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk" }

# StockLauncher none: Google TV's launcher and setupwraith stay enabled, because setupwraith holds the
# Chromecast credentials and would otherwise take over Home. HomeKeyService catches the Home key so the
# new launcher opens instantly (stock_home_redirect + home_long_press_home in config.xml).
# Trade-off: a held Volume key moves one step per press.
& launcher\build-and-install.ps1 -TvHost $TvHost -StockLauncher none -HomeKeyService
