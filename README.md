# Aurora Liquid Glass (Minecraft 1.21.11, Fabric)

Dark liquid-glass UI, HUD editor and YouTube live stream overlay.

## Build
- GitHub Actions: push this folder to a repo; the `Build` workflow produces `liquidglass-jar`.
- Local: `gradle build` (Java 21, Gradle 8.14+). Jar is in `build/libs/`.

## Use
1. Press Right Shift (or Mod Menu > Configure) to open the GUI.
2. Open the stream workspace panel, paste a YouTube live URL and your own YouTube Data API v3 key.
3. Press Connect. Use Edit HUD to move and resize elements.

## YouTube API key
Create a key in Google Cloud Console (enable "YouTube Data API v3"). The key is stored only in
`config/liquidglass.json` on your machine. Default quota is 10,000 units/day. Live chat polling costs
about 5 units per request, so one stream can use a large part of the quota within a few hours.

## Not built or tested
This project was assembled without a Minecraft build environment. Compile it first and report any errors.
