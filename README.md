# Util Client (Fabric, Minecraft 26.3, singleplayer)

Right Shift opens the click GUI. Modules: Xray, ESP, Tracker, AutoHit, AutoMine, AutoPlace, Fullbright.

## Build
Requires JDK 25 and internet access for Gradle (to fetch Minecraft + Fabric).
1. Add a Gradle wrapper (copy from a project made at fabricmc.net/develop/template, or `gradle wrapper --gradle-version 9.7.1`).
2. `./gradlew build`  ->  jar in `build/libs/utilclient-1.0.0.jar` (use the one WITHOUT `-sources`).
3. Put the jar in `.minecraft/mods` together with Fabric API 0.160.7+26.3 (or newer for 26.3) and Fabric Loader 0.19.5+.

## Glint colours
Not part of the jar. Drop the `UtilClient-Glint-*.zip` packs into `.minecraft/resourcepacks`
and enable one under Options > Resource Packs. If the game flags a pack as incompatible, you can still enable it.

## No local setup: build the jar on GitHub
Push this folder to a new GitHub repo. The workflow in `.github/workflows/build.yml` builds it automatically.
Open the repo's Actions tab, click the latest run, and download the `utilclient-jar` artifact.
