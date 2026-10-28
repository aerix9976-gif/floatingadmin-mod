# Aero's Admin Panel - Fabric 26.2 (client + server)

Operator-only admin panel with a fully custom-drawn crimson GUI (not an inventory menu).

**Install the same jar on the SERVER and on every CLIENT that should use it**, plus Fabric API for 26.2.

Open it: `/adminpanel` (aliases `/ap`, `/admin`, `/fap`) or press K. Only operators can open it. The server re-checks
operator status on every click and builds the commands itself - the client never sends raw command text.

Floating window: drag the title bar to move it, mouse wheel pages through lists, Esc hides it and K brings it back in the same place.

Pages (tabs): Dashboard (live stats + recent actions), Self, Players, Items (optional auto-enchant, 'Ask' opens a player menu), Forge (best-enchantment gear),
Inventory Viewer (copy/take/delete), Kits, World, Announce, Fun, Teleport & Warps, Effects, Game Rules, Bans & Mutes, Action Log.

Data is stored in `config/aeros-admin/` (mutes.json, warps.json, kits.json, actions.log).

Build (JDK 25): ./gradlew remapJar  -> build/libs/AerosAdminPanel-<version>.jar
(The first build downloads and remaps Minecraft and takes several minutes; later builds reuse the cache and take seconds.)
