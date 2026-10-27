# Aero's Admin Panel - Fabric 26.2 (client + server)

Operator-only admin panel with a fully custom-drawn crimson GUI (not an inventory menu).

**Install the same jar on the SERVER and on every CLIENT that should use it**, plus Fabric API for 26.2.

Open it: `/adminpanel` (aliases `/ap`, `/admin`, `/fap`) or press K. Only operators can open it. The server re-checks
operator status on every click and builds the commands itself - the client never sends raw command text.

Pages (sidebar): Self, Players, Items, World, Announce, Fun, Teleport (coords + saved warps), Effects (picker with level/time
steppers), Rules (game rule on/off), Bans & Mutes (persistent, lift from the panel), Server Stats (live), Action Log.

Data is stored in `config/aeros-admin/` (mutes.json, warps.json, actions.log).

Build (JDK 25): ./gradlew build  -> build/libs/AerosAdminPanel-<version>.jar (not the -sources one)
