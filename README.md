# Floating Admin Panel (Fabric, Minecraft 26.2)

A neon, fully custom-drawn admin screen. Client-side mod: press **K** (rebindable) to open.
Every button runs a normal server command, so it works on **any** server where you are an operator
(vanilla, Paper, Fabric ...) - nothing needs to be installed on the server.

Pages: Self (gamemodes, heal, god, vanish, effects), Players (full per-player controls),
Items (every item, search, 1/16/32/64, give to you or a player), World (weather, time, difficulty, cleanup),
Fun (announcements, lightning, cookies, random TP ...).

## Build
Needs JDK 25.
    ./gradlew build        (Windows: gradlew.bat build)
Jar: build/libs/FloatingAdminPanel-Fabric-1.0.0.jar  (use the one WITHOUT "-sources")

Or push this folder to GitHub: the included workflow builds it and uploads the jar as an artifact.

## Install
Fabric Loader 0.20.1+ and Fabric API for 26.2, then drop the jar in your client's `mods` folder.
