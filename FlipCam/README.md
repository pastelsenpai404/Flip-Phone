# Flip Cam for SHARP NP601SH

A camera interface for the NP601SH's physical keypad and rear camera.

- Photo: full-resolution JPEG, 0/3/10-second timer, rule-of-thirds grid, flash and camera zoom.
- Video: 480p MP4 with microphone audio when the phone supports that profile; falls back to the phone's low profile.
- Auto mode: choose scene (including night, portrait, landscape and HDR where supported), photo resolution, JPEG quality and video resolution.
- Manual mode: ISO 50–3200, exposure compensation from -2 to +2 EV, white balance, macro/infinity focus presets, light metering, exposure/white-balance locks, contrast, saturation and sharpness. Menus show values reported by the phone; settings are saved between launches. The NP601SH camera does not expose usable manual shutter speed through its Camera HAL.
- Video quality can be set to 480p or 720p. This was checked against the device's supported recorder profiles, and a 720p test recording was saved successfully.
- QR: scans the live camera preview in Flip Cam using bundled ZXing Core 3.3.3. A result is shown before the user chooses to copy it, open a web link or scan again. The scanner does not launch SHARP Reader.
- Storage: photos and videos use the removable SD card by default. On this NP601SH they are saved in `/storage/sdcard1/Android/data/dev.codex.flipcam/files/DCIM/FlipCam`. If the SD card is unavailable, Flip Cam uses the phone's `DCIM/FlipCam` folder. The phone's Gallery does not index the SD card's `Android/data` folder; use a file manager to browse those files. Files in `Android/data` may be removed when Flip Cam is uninstalled.

Keys: **1** photo, **2** video, **3** QR, **4** Auto/Manual, **5** settings, **OK** or camera key to shoot/start/stop, **up/down** zoom, **0** flash where available, **\*** timer, **#** gallery. The NP601SH reports no flash modes, so Flip Cam disables its flash button on this device.

Build and install while the phone is connected:

```powershell
.\Build.ps1 -Install
```

The app uses Android's legacy camera API because this NP601SH exposes a Camera HAL 1 rear camera. The installed Flip Deck launcher and its shortcut service route the camera-icon hardware key to Flip Cam.

ZXing Core 3.3.3 is bundled under Apache License 2.0; see `lib/LICENSE-ZXING.txt`. The license is also packaged in the APK under `res/raw/license_zxing.txt`.
