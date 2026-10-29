# AloneByDeath Client 2.0 (Fabric, Minecraft 26.3)

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
- Theme settings are under Client > ClickGUI: accent colour, gradient or rainbow, header style (Kami or Lambda), panel width, opacity, background dim or blur, custom font, text shadow, animation speed, and more.

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
- **Combat:** AutoHit (kill aura), Triggerbot, Aimbot, Criticals, AutoTotem, AutoCrystal, AutoAnchor, Surround, AutoArmor, AutoWeapon
- **Movement:** Sprint, AutoWalk, Step, NoFall, Velocity
- **Render:** ESP, Nametags, Tracers, BlockESP, HoleESP, Tracker, Xray, Fullbright, Zoom, NoRender
- **Player:** AutoMine, AutoPlace, AutoTool, AutoEat, FastPlace, AutoRespawn
- **Misc:** FakePlayer, MiddleClickFriend, AntiAFK
- **Client:** ClickGUI, HUD, Notifications

AutoCrystal, AutoAnchor and AutoTotem's Smart mode predict explosion damage the way the server calculates it. That covers distance, how much of the hitbox the blast can see, armour, toughness, Protection, Blast Protection, Resistance and difficulty.

Friends are never targeted by any combat module.

## Build
Push this folder to GitHub. The workflow in `.github/workflows/build.yml` builds it, and the jar appears under Actions > latest run > Artifacts.

To build locally instead, use JDK 25 and run `gradle build`. The jar goes to `build/libs/` (use the one without `-sources`).

Install it in your mods folder alongside Fabric API for 26.3.

## Credits
- The bundled UI font is **Outfit** by the Outfit Project Authors, under the SIL Open Font License 1.1 (see `assets/alonebydeathclient/font/Outfit-OFL.txt`).
- Module ideas follow what public clients such as Meteor, Kami and Lambda offer. The code here is original.
