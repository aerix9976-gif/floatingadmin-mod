# Floating Admin Panel - Fabric 26.2 (client + server)

Operator-only admin panel with a fully custom-drawn GUI (not an inventory menu).

**Install the same jar on the SERVER and on every CLIENT that should use it**, plus Fabric API for 26.2.

Open it: `/adminpanel` (aliases `/ap`, `/admin`, `/fap`) or press K.
Only operators can open it. The server re-checks operator status on every single click, and it builds the
commands itself - the client never sends raw command text.

Pages: Self, Players (heal, kill, gamemode, spy, freeze, mute, kick, ban, op ...), Items (every item, search, amounts),
World (weather, time, difficulty, kill mobs/items, tick freeze, border), Fun (announcements, lightning, summon, gather ...).

Build (JDK 25): ./gradlew build  -> build/libs/FloatingAdminPanel-Fabric-<version>.jar (not the -sources one)
Or push to GitHub: the included workflow builds it.
