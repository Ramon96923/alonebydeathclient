# Notes on other clients

What popular clients do well, which of it DonutWaffen uses, and what's still on the wishlist.
"Reference" means the source was read for ideas; "port" means GPL-3.0 code was adapted (credited in the file).

## Open source (GPL-3.0, code can be adapted with credit)

### Meteor Client (+ addons)
- **Rotation system**: swaps a wanted rotation into the normal movement packet (`sendPosition` head/tail) instead of
  sending extra look packets. *Used: `RotationManager`.*
- Freecam via `Camera.alignWithEntity`, `Entity.turn`, `KeyboardInput.tick`, plus treating the camera as a spectator
  for rendering. *Used.*
- Xray/Fullbright lightmap + block renderer hooks. *Ported.*
- AttributeSwap (spear/mace/breach swaps), AutoReconnect, StorageESP block-entity iteration. *Used as reference.*
- Wishlist: PacketMine/SpeedMine, AutoCity, BedAura, NewChunks, Nuker, InventoryTweaks, Notebot.

### Wurst 7 (and the CevAPI fork on GitLab)
- 3D render pipeline for ESP lines/boxes. *Ported (`WorldRender`).*
- Criticals packet sequence, MaceDMG, ExtraElytra, LastServerRememberer. *Ported/reference.*
- Wishlist: Search (custom block list), Excavator, TreeBot, AutoFarm, Tunneller.

### LiquidBounce (nextgen)
- SpearKill (charged-spear lunge), Hitbox via pick radius, Reach via interaction-range attributes. *Reference.*
- Strong anticheat awareness: per-anticheat Criticals modes, sprint handling for crits, rotations that "settle" before
  acting. *Adopted the idea of acting only once the server already has the right rotation (AutoCrystal Legit/Silent).*
- Wishlist: proper simulation-based Scaffold, TickBase, Blink, Backtrack.

## DonutSMP clients (feature ideas only — no code used)

### Glazed (Meteor addon, all rights reserved)
- RTP base finder, AutoSell / AH tools, Spawner protect/order/drop, TPA macro, hide scoreboard, stash finder,
  rotated-deepslate / tunnel / hole ESPs, crystal & anchor macros, key pearl, lunge macro, inv/hover totem.
- *Done in DonutWaffen (original code):* RTPBaseFinder, AutoSell, TpaAccept, StreamerMode, BaseFinder,
  StorageESP spawners, TunnelESP, rotated deepslate in BlockESP, CrystalMacro, AnchorMacro, KeyPearl, InvTotem.
- *Skipped on purpose:* Fake Pay, Fake Scoreboard, Fake Ban Screen (made for tricking other players).
- Wishlist: AH sniper/restocker, spawner protect (break + store when a player shows up), region map, webhooks.

### Krypton, 67 Client, Anubis (closed source)
- Ghost-client style: everything works through your real crosshair with human-ish timing — legit crystal/anchor
  macros, hover totem, jump reset, W-tap, shield breaker, aim assist, hitboxes, self-destruct.
- *Done:* crystal/anchor macros, InvTotem, JumpReset, WTap, ShieldBreaker, Aimbot (only-while-attacking), Hitbox.

## Why combat modules get people banned (and what DonutWaffen does about it)
| Problem | Anticheat check that catches it | Fix in 3.1 |
|---|---|---|
| Extra rotation packets on top of the movement packet | Timer / packet count (Grim, Vulcan) | `RotationManager` rides on the normal packet |
| Clicking after the movement packet in a tick | Grim "Post" checks | Modules now run at the **start** of the tick, like vanilla input |
| Instant snaps to the target | Aim checks | Turn-speed limits (AutoHit Smooth, AutoCrystal Legit/Silent) |
| Placing with a rotation the server hasn't seen yet | Rotation-place / raytrace checks | AutoCrystal Legit acts only when the *already-sent* rotation's ray hits the block/crystal, and places with that exact hit point |
| Place + break, or swap + click + swap back, in one tick | Multi-action / bad-packets checks | One action per tick, no same-tick silent swap in Legit |
| Moving items with the inventory closed | Inventory checks | AutoTotem *Legit*: hotbar items go to the offhand like pressing F; other moves wait for an open inventory (InvTotem) |
| 0% knockback | Velocity checks | Velocity defaults to 85%, JumpReset as the legit option |
| Perfectly regular timing | Statistical checks | Random delays, miss chance, CPS ranges |

| Rotation steps that aren't whole mouse steps for your sensitivity | GCD / sensitivity / Aim checks (Grim, Vulcan, Polar) | 3.2 Humanizer: every rotation snapped to mouse pixels, sent through `Entity.turn` |
| Constant turn speed, identical deltas, always the exact centre | Aim pattern / duplicate-look checks | 3.2: accelerate/ease aim with changing pace, wobble and a moving aim spot |
| Silent rotation snapping back to the real view | Big single-packet rotation (snap) checks | 3.2: rotation glides back over a few ticks |
| Hitting a crystal the tick it spawns, every time | Statistical / reaction checks | 3.2: *Crystal age* + gaussian reaction |
| Hotbar swap and click in the same tick (crystal/anchor macros) | Bad-packets / autoclicker checks | 3.2: swaps settle 1–2 ticks before clicking |
| Many modules clicking together (crystal + totem + aura) | CPS / multi-action checks | 3.2: one shared CPS budget incl. your own clicks |
| Placing with a Packet rotation the server hasn't received yet | Rotation-place checks | 3.2: Placer waits for the sent rotation and clicks that ray's exact hit |

Still risky by nature: Flight, Speed, packet Criticals, Mace spoof, Blatant AutoCrystal.
