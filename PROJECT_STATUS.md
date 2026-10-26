# Photo Motion (Android)
- Architecture: Android WebView shell (MainActivity.java) + app/src/main/assets/index.html (all app logic). Native bridge: HTTP to fal.ai, clip download, save video to Movies/Photo Motion.
- Done: photo list, motions (zoom/pan/rotate), fit-whole-photo, preview, MP4/WebM export, AI Orbit via fal.ai (single + multi photo), key saved on phone, % progress.
- Build: GitHub Actions (.github/workflows/build.yml) -> release "latest" -> PhotoMotion.apk
- Untested: on-phone run (logic tested in desktop Chromium only).
- Next: test on phone, fix issues found.
