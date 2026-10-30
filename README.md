# AloneByDeath Client 2.1 (Fabric, Minecraft 26.3)

## Main menu
When you're on the title screen there's an **AloneByDeath** card in the top-left:
- **Open Click GUI** opens the full menu right there, before you even join a world (it also shows the key it's bound to).
- **AloneByDeath on Spotify** opens [the artist page](https://open.spotify.com/artist/0MVEjD74djJKq2ZJcNniE7) in your browser.

Turn the card off under Client > ClickGUI > *Main menu card*.

## Click GUI (Right Shift)
- Drag a panel's header to move it. Right-click the header to fold it, and scroll inside tall panels.
- Left-click a module to toggle it, right-click to open its settings, and middle-click to bind a key.
- Settings:
  - Switches toggle on click.
  - Sliders can be dragged; right-click resets them.
  - Modes cycle: left-click for next, right-click for previous.
  - Colours: left-click opens the picker, right-click toggles rainbow.
- Every module has a **Bind** row (left-click, then press a key; right-click unbinds), **Show in list**, and **Bind mode** (Toggle or Hold).
- Type anywhere to search. Esc clears the search, then closes the GUI.
- **HUD Editor** (top right) lets you drag HUD elements and switch them on or off.
- Theme settings are under Client > ClickGUI: accent colour, gradient or rainbow, header style (Kami or Lambda), **GUI scale** (the menu is smaller by default now) and **corner radius** (rounded panels, switches and buttons), panel width, opacity, background dim or blur, custom font, text shadow, animation speed, and more.

## Saving
Everything saves automatically to `.minecraft/config/alonebydeathclient/config.json`: module toggles, settings, binds, panel and HUD positions, friends and the command prefix.

## Commands (prefix `.`)
| Command | What it does |
|---|---|
| `.help` | List all commands |
| `.t <module>` | Toggle a module |
| `.bind <module> [none]` | Bind to the next key you press (or clear the bind) |
| `.binds` | List your binds |
| `.set <module> <setting> <value>` | Change a setting, e.g. `.set autocrystal min damage 8` |
| `.reset <module>` | Reset a module's settings |
| `.drawn <module>` | Hide or show a module in the HUD list |
| `.friend add\|del\|list\|clear <name>` | Manage friends (middle-clicking a player also works) |
| `.config save\|load\|list <name>` | Save and load profiles |
| `.prefix <text>` | Change the command prefix |
| `.panic` | Turn every module off |
| `.vclip <n>` / `.hclip <n>` | Teleport up or forward |
| `.font on\|off` | Switch the custom font |
| `.gui` | Open the click GUI |

## Modules
- **Combat:** AutoHit (kill aura), Triggerbot, AutoClicker, Aimbot, Criticals, Hitbox, OldPvP, Mace, Spear, AutoTotem, AutoCrystal, AutoAnchor, Surround, AutoArmor, AutoWeapon, AutoTrap, SelfTrap, HoleFiller, AutoLog
- **Movement:** Sprint, AutoWalk, Step, NoFall, Velocity, ElytraFly, Flight, Speed, Jesus, Scaffold
- **Render:** ESP, Nametags, Tracers, BlockESP, HoleESP, Tracker, Xray, Fullbright, Zoom, NoRender, LogoutSpots, Breadcrumbs
- **Player:** AutoMine, AutoPlace, AutoTool, AutoEat, FastPlace, AutoRespawn, AutoMend, ChestStealer
- **Misc:** FakePlayer, MiddleClickFriend, AntiAFK, AutoReconnect, VisualRange, PopCounter
- **Client:** ClickGUI, HUD, Notifications

### 26.3 combat (works with the crystal modules)
- **Mace** – auto-swaps to a mace while you're falling and spoofs the drop so smash hits land for full damage, then swaps back.
- **Spear** – swaps to a spear to hit targets past normal sword reach, and to a Lunge spear to dash when you swing at air while sprinting.
- **OldPvP** – 1.8-style combat: removes the attack cooldown so every click is full damage, and clears the miss delay. Pair with AutoClicker.
- **Criticals** now defaults to **Mini Jump**: it holds your hit for a moment, does a real 0.1-block hop and sends the hit as you fall, which is exactly when the server counts a crit. No spoofed movement packets, so no *"Invalid move player packet received"* kick. **Jump** does the same with a normal jump (most legit). **Packet/NCP** are still there for servers that allow them. *Stop sprint* is on by default because sprinting cancels crits.

AutoCrystal, AutoAnchor and AutoTotem's Smart mode predict explosion damage the way the server calculates it. That covers distance, how much of the hitbox the blast can see, armour, toughness, Protection, Blast Protection, Resistance and difficulty.

### For anarchy servers
- **AutoTrap / SelfTrap / HoleFiller** – obsidian around an enemy's head (Full, Head or Top), around your own head, or into the safe holes next to enemies. They pair with AutoCrystal, AutoAnchor and Surround.
- **AutoLog** – disconnects on low health, low totems or a nearby player, and switches AutoReconnect off so you don't rejoin into the fight.
- **PopCounter** – "Name popped 3 totems!" in chat, plus a message when someone dies after popping.
- **ElytraFly** – Control mode (W/S speed, Space/Shift height) or Cruise mode (fixed speed, hover), with instant takeoff and no fireworks.
- **Flight, Speed (Strafe/Ground), Jesus, Scaffold** – getting around. Flight has an anti-kick dip.
- **LogoutSpots** – a box and name tag where a player logged out, with their health and how long ago.
- **Breadcrumbs** – a fading trail of where you've been.
- **ChestStealer** – empties a chest, barrel or shulker into your inventory the moment you open it.
- **AutoMend** – throws XP bottles at your feet to repair Mending armour without moving your view.
- **AutoReconnect** – adds Reconnect / AutoReconnect buttons (with a countdown) to the disconnect screen and rejoins after queue drops or restarts.
- **VisualRange** – warns in chat when another player comes into your render range.

Friends are never targeted by any combat module.

## Fixes in 2.1
- **Criticals kick** – fixed by defaulting to Mini Jump (real movement, the hit sent while you fall, no fake packets). See above.
- **Mace** – the spoofed drop now also works from the ground (the main use), and only fires when the hit will land, so you never keep a fake fall.
- **Xray ores looked dark** – the lightmap is now forced to full brightness while Xray (or Fullbright's Gamma mode) is on, and the vanilla and Fabric-Indigo block renderers both honour Xray, so ores underground are bright and everything else is hidden.
- **BlockESP** is cleaner – outlines by default with a lighter fill, and clustered ore veins render as a tidy wireframe instead of overlapping blobs (Client > BlockESP > *Merge veins*).

## Build
Push this folder to GitHub. The workflow in `.github/workflows/build.yml` builds it, and the jar appears under Actions > latest run > Artifacts.

To build locally instead, use JDK 25 and run `gradle build`. The jar goes to `build/libs/` (use the one without `-sources`).

Install it in your mods folder alongside Fabric API for 26.3.

## License & credits
This client is licensed under the **GNU GPL v3.0** (see `LICENSE.txt`), because it adapts code from GPL-3.0 clients. Any redistribution must stay GPL-3.0 and keep these credits.

- **3D world rendering** (the ESP/tracer render pipeline, `render/WorldRender.java`, the line shader and `LevelRendererMixin`) and the **Criticals** / **Mace** packet sequences are adapted from **[Wurst Client](https://github.com/Wurst-Imperium/Wurst7)** (GPL-3.0).
- The **Xray / Fullbright** render hooks (`LightmapMixin`, `ModelBlockRendererMixin`, the Indigo mixin) and the **Spear** / **AutoReconnect** approaches are adapted from **[Meteor Client](https://github.com/MeteorDevelopment/meteor-client)** (GPL-3.0), with ideas also from **[LiquidBounce](https://github.com/CCBlueX/LiquidBounce)** (GPL-3.0).
- The GUI design takes inspiration from **Kami** and **Lambda**.
- Ported files carry an attribution header; everything else is original to this project.
- The bundled UI font is **Outfit** by the Outfit Project Authors, under the SIL Open Font License 1.1 (see `assets/alonebydeathclient/font/Outfit-OFL.txt`).
