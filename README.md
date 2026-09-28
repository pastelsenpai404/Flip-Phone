# Flip Phone apps for SHARP NP601SH

Each app has its own source, build script, README, and APK output:

- [Pocket Hearts](Pocket%20Hearts/README.md) — offline game
- [FlipDeck](FlipDeck/README.md) — launcher
- [FlipCam](FlipCam/README.md) — camera
- [PocketMusic](PocketMusic/README.md) — music and Bluetooth tools
- [FlipBrowse](FlipBrowse/README.md) — lightweight single-page browser for the globe key

The apps share the Android SDK in `.tools` and the signing key `pocket-hearts.keystore` in this directory. Build an app by running its `Build.ps1` from its own folder. The signing key is created by the Pocket Hearts build if it does not exist.
