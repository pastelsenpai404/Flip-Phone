# Flip Cam for SHARP NP601SH

A camera interface for the NP601SH's physical keypad and rear camera.

- Photo: full-resolution JPEG, 0/3/10-second timer, rule-of-thirds grid, flash and camera zoom.
- Video: 480p MP4 with microphone audio when the phone supports that profile; falls back to the phone's low profile.
- Auto mode: choose scene (including night, portrait, landscape and HDR where supported), photo resolution, JPEG quality and video resolution.
- Manual mode: ISO 50–3200, exposure compensation from -2 to +2 EV, white balance, macro/infinity focus presets, light metering, exposure/white-balance locks, contrast, saturation and sharpness. Menus show values reported by the phone; settings are saved between launches. The NP601SH camera does not expose usable manual shutter speed through its Camera HAL.
- Video quality can be set to 480p or 720p. This was checked against the device's supported recorder profiles, and a 720p test recording was saved successfully.
- QR: opens the phone's built-in SHARP barcode/QR reader. Scanning stays within that reader because its result API is not public.
- Gallery: opens the phone's image gallery. Photos and videos are saved in `DCIM/FlipCam` and indexed by Android.

Keys: **1** photo, **2** video, **3** QR, **4** Auto/Manual, **5** settings, **OK** or camera key to shoot/start/stop, **up/down** zoom, **0** flash where available, **\*** timer, **#** gallery. The NP601SH reports no flash modes, so Flip Cam disables its flash button on this device.

Build and install while the phone is connected:

```powershell
.\Build.ps1 -Install
```

The app uses Android's legacy camera API because this NP601SH exposes a Camera HAL 1 rear camera. It does not replace the phone's hardware camera shortcut unless the launcher is configured to open Flip Cam.
