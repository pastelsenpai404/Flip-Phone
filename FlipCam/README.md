# Flip Cam for SHARP NP601SH

A camera interface for the NP601SH's physical keypad and rear camera.

- Photo: full-resolution JPEG, 0/3/10-second timer, rule-of-thirds grid, flash and camera zoom.
- Video: 480p MP4 with microphone audio when the phone supports that profile; falls back to the phone's low profile.
- QR: opens the phone's built-in SHARP barcode/QR reader. Scanning stays within that reader because its result API is not public.
- Gallery: opens the phone's image gallery. Photos and videos are saved in `DCIM/FlipCam` and indexed by Android.

Keys: **1** photo, **2** video, **3** QR, **OK** or camera key to shoot/start/stop, **up/down** zoom, **0** flash, **\*** timer, **#** gallery.

Build and install while the phone is connected:

```powershell
.\Build.ps1 -Install
```

The app uses Android's legacy camera API because this NP601SH exposes a Camera HAL 1 rear camera. It does not replace the phone's hardware camera shortcut unless the launcher is configured to open Flip Cam.
