# saya

Glass TV with the day's prayer times (JAKIM method) under the clock and on the screensaver, and the
azan at each prayer time: `azan.mp3`, and `azan_subuh.mp3` for Subuh.

The location is left out on purpose. Before pushing the theme:

1. Copy `config.example.json` to `config.json`.
2. Add your place in degrees, for example Kuala Lumpur:

   ```json
   "prayer_lat": 3.139,
   "prayer_lon": 101.687,
   ```

3. Push it: `tools\theme.ps1 -TvHost <ip> themes\saya` (Windows) or `TV_HOST=<ip> tools/theme.sh themes/saya`.

Without `prayer_lat` and `prayer_lon` the prayer line stays hidden and no azan plays. For the card over
other apps, allow it once: `appops set com.gathin.tvhome SYSTEM_ALERT_WINDOW allow`.
