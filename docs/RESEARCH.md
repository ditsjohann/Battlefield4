# WARFRONT — Research Notes

Research that shaped the design and architecture of WARFRONT, a Battlefield 4-inspired
combined-arms mod for Minecraft Java 1.21.1 / Forge. This is a living document: new
findings are appended as specific engineering problems come up.

> Naming: WARFRONT is an original work *inspired by* Battlefield 4's design. It uses no
> EA/DICE names, factions, maps, weapon names, art or audio. Factions (Iron Coalition,
> Ember Legion), weapon designations and every asset are original. Textures and sounds are
> generated procedurally by scripts in `tools/`.

---

## 1. Battlefield 4: what actually defines it

### 1.1 Conquest is the core loop
- Teams fight over **capture points (flags)**. Holding a flag requires out-numbering the
  enemy inside its radius. The flag is first neutralised, then captured.
- Each team has a pool of **tickets**. A team that holds **more than half** of the flags makes
  the enemy **bleed** tickets over time. Every death/respawn also costs a ticket. Bleed is the
  dominant way to win, and kills are the slower one.
  ([Battlefield Wiki – Conquest](https://battlefield.fandom.com/wiki/Conquest),
  [Prima – Game modes](https://primagames.com/eguides/battlefield-4-eguide/battlefield-bootcamp/game-modes),
  [Gamepressure – Conquest](https://www.gamepressure.com/battlefield4/conquest-and-conquest-large/z35a85))
- Owned flags are **spawn points**, and some spawn vehicles. Taking territory therefore
  moves the front line, which pulls combat across the map.

**Design takeaway:** the strategic layer (flags + tickets + spawning on owned flags) gives
every gunfight a purpose. That layer is the backbone of WARFRONT. AI armies make it work in
singleplayer.

### 1.2 Infantry gunplay
Community data (Symthic) documents the BF4 weapon model:
- Each shot picks an aim point with **radial random spread**. Spread **increases per shot**
  (bloom) and **decreases per second** while ready. Hip-fire spread is far larger than ADS.
- **Recoil** kicks the view: vertical recoil has a random 0.9–1.0 multiplier, horizontal
  recoil is randomised left/right, and a **first-shot recoil multiplier** makes the first round
  kick harder.
- **Damage falloff**: max damage up to a start range, linear down to min damage at an end range.
- Bullets are **projectiles with velocity and gravity**, not hitscan.
- Attachments trade stats. The heavy barrel, for example, cuts spread 50% but adds 50% recoil.
  ([sym.gg BF4 weapon mechanics](https://sym.gg/legacy/pages/bf4/bf4_dataWeapon.html),
  [Symthic – Algorithms for the shooting mechanics](https://forum.sym.gg/t/algorithms-for-the-shooting-mechanics-in-bf4/81),
  [gameranx – heavy barrel change](https://gameranx.com/updates/id/18685/article/battlefield-4-heavy-barrel-no-longer-a-vital-accessory/))

**Design takeaway:** implement exactly this model: per-weapon stats with spread, bloom, decay,
first-shot multiplier, falloff, velocity and gravity. Simulate bullets server-side as ballistic
segments. This is the single biggest "feels like BF" lever.

### 1.3 Teamplay systems
- **Four classes:** Assault (frontline + medic: med bag, defibrillator), Engineer (anti-vehicle
  launchers + repair tool), Support (ammo box, C4, LMG) and Recon (sniper, spotting, radio beacon).
  ([Prima – Complete class guide](https://primagames.com/news/battlefield-4-complete-class-guide),
  [MP1st – classes, gadgets, suppression](https://mp1st.com/news/battlefield-4-factions-classes-gadgets-suppression-and-field-upgrades-detailed))
- **Spotting:** look at an enemy and press the spot key. They appear on the minimap/3D for
  teammates, and the spotter gets a **spot assist bonus** when a teammate kills the spotted target.
- **Suppression:** near-miss fire degrades the target's accuracy and blurs the screen, and the
  shooter earns **suppression assist** points.
  ([Battlefield Wiki – Suppression](https://battlefield.fandom.com/wiki/Suppression),
  [EA – suppressive fire tips](https://www.ea.com/en-gb/news/battlefield-4-tips-surpressive-fire))
- **Squad orders:** attack/defend markers on objectives grant bonus points for playing them.
  ([Prima – helping the team](https://primagames.com/tips/battlefield-4-tip-5-tips-helping-team))
- Firing an unsuppressed weapon shows you on the enemy minimap.

### 1.4 Levolution
- Map-scale, player-triggered events. The best known is the Siege of Shanghai skyscraper, which
  collapses after enough damage to its support pillars. DICE wanted these events to **matter
  to gameplay**, not just look good. The collapse changes sightlines and the objective and can
  help either team.
  ([GamesRadar Levolution guide](https://www.gamesradar.com/battlefield-4-levolution-guide/5),
  [PCGamesN](https://pcgamesn.com/battlefield/battlefield-4-s-levolution-lets-you-flood-cities-block-alleys-shoot-out-power),
  [EA – Levolution & commander mode](https://ea.com/en-gb/news/battlefield-4-single-player-levolution-commander-mode-battlelog))

**Design takeaway:** a destructible *set-piece* with a gameplay consequence. In WARFRONT the
centre flag's **Comms Tower** grants an automatic radar uplink (auto-spotting) to whoever holds
the flag. Enough explosive damage collapses it, which ends the uplink for both sides and buries
the flag in rubble.

### 1.5 HUD and feedback
- Bottom-left **minimap**; bottom-right **ammo counter** (big = mag, small = reserve) and health;
  **kill feed** top-right; **hit-marker** bloom on hits; score popups under the crosshair.
  ([Prima – Getting started](https://primagames.com/eguides/battlefield-4-eguide/battlefield-bootcamp/getting-started),
  [Wikipedia – Battlefield 4](https://en.wikipedia.org/wiki/Battlefield_4))
- A number-based ammo display measurably changes player behaviour (BF3 HUD study:
  [York U, 2015](https://www.yorku.ca/mack/ieee_gem2015.html)).

### 1.6 What makes it *Battlefield* (priority order for WARFRONT)
1. Conquest with tickets and territory-based spawning.
2. Ballistic gunplay with readable recoil, spread and damage falloff.
3. Classes that create interdependence (revive, resupply, repair, spot).
4. Combined arms: at least one vehicle that infantry must answer with specific tools.
5. Destruction that changes the battlefield, plus one big set-piece.
6. Score/ribbon feedback and progression unlocks.

---

## 2. Minecraft Forge 1.21.1: technical findings

All of these were verified against the decompiled 1.21.1 + Forge 52.1.21 sources produced by
the build (`.gradle/mavenizer/repo/.../forge-1.21.1-52.1.21-sources.jar`).

| Area | Finding |
|---|---|
| Toolchain | Official MDK = **ForgeGradle 7** (`net.minecraftforge.gradle [7.0.3,8)`), **Gradle 9.3.1**, Forge **52.1.21** (latest 1.21.1 build), official Mojang mappings, Java 21 target. FG7's *Mavenizer* runs its tools on JDK 25 and JDK 8. Offline/proxied machines must provide them (see `docs/DEVELOPMENT.md`). |
| Mod entry | `@Mod` class with constructor `(FMLJavaModLoadingContext)`; `context.getModEventBus()`. |
| Event bus | Forge 52 still uses EventBus 6 (`@SubscribeEvent`, `MinecraftForge.EVENT_BUS`, `@Mod.EventBusSubscriber(bus = Bus.MOD/FORGE)`). Tick events are split `TickEvent.*.Pre/Post` classes (no phase field). |
| Registration | `DeferredRegister` + `RegistryObject`, `ForgeRegistries.*` or `Registries.*` keys (data components, creative tabs). |
| Item data | 1.21 **data components** replace stack NBT: `DataComponentType.builder().persistent(codec).networkSynchronized(streamCodec)`. Gun ammo, reserve, reload state, fire mode and attachments live here. |
| Networking | `ChannelBuilder.named(rl).networkProtocolVersion(n).simpleChannel()` → `messageBuilder(Class, NetworkDirection.PLAY_TO_SERVER)`, `.codec(StreamCodec)`, `.consumerMainThread(...)`, `.add()`. Send via `CHANNEL.send(msg, PacketDistributor.PLAYER.with(p))`. |
| HUD | `AddGuiOverlayLayersEvent` (mod bus) → `ForgeLayeredDraw.add(name, layer)`. Vanilla layers (`CROSSHAIR`, etc.) can be hidden with `addConditionTo(PRE_SLEEP_STACK, CROSSHAIR, cond)`. |
| First-person | `RenderHandEvent` (cancelable): render the gun ourselves for ADS, recoil and reload animations. `ComputeFovModifierEvent` / `ViewportEvent.ComputeFov` for zoom; `ViewportEvent.ComputeCameraAngles` for camera shake. |
| Input | `InputEvent.InteractionKeyMappingTriggered` cancels vanilla attack/use while holding a gun; `RegisterKeyMappingsEvent` adds keys. |
| Persistence | No `PERSISTED_NBT_TAG` in this version: player progression goes in `getPersistentData()` and is copied in `PlayerEvent.Clone`. Match state is `SavedData` on the level. |
| Damage | Data-driven `damage_type` JSON; `LivingDeathEvent`/`LivingHurtEvent` exist. Bullets reset `invulnerableTime` so automatic fire is not swallowed by the 10-tick i-frames. |
| Explosions | `Level.explode(Entity, DamageSource, ExplosionDamageCalculator, x, y, z, power, fire, ExplosionInteraction)`; `ExplosionEvent.Detonate` exposes the affected blocks/entities (used for tower integrity). |
| Testing | `gameTestServer` run plus `@GameTestHolder`/`@GameTest` for automated in-world tests. Client can run headless under Xvfb + Mesa for screenshots. |

### Vehicles
The boat/horse model is the proven approach in vanilla: the **controlling passenger's client
simulates the vehicle** (`isControlledByLocalInstance`) and the server accepts the movement via
vanilla move-vehicle packets, then relays it to other clients. Mods that fight this model get
jitter for other players (see Forge forum threads:
[vehicle motion](https://forums.minecraftforge.net/topic/120868-119-vehiclemount-motion),
[custom entity movement](https://forums.minecraftforge.net/topic/100814-1165-what-is-the-proper-approach-for-handling-a-custom-entitys-movement),
[Mojang MC-108765](https://bugs.mojang.com/browse/MC-108765)).
WARFRONT's tank follows that pattern: client-driven hull movement for the driver, server-side
health, damage, weapons and AI driving.

---

## 3. Existing projects surveyed

| Project | What we learned | Status / licence | Used? |
|---|---|---|---|
| **TaCZ** – Timeless & Classics Zero ([github mirror](https://github.com/thefakedevs/TACZ), [CurseForge](https://curseforge.com/minecraft/mc-mods/timeless-and-classics-zero)) | Fully data-driven gun packs (JSON + Lua), kinetic bullet entities; a separate addon replaced them with raycasts for reliability ([TaCZ Raycast](https://modrinth.com/mod/tacz-raycast)). Shows that bullet *entities* have hit-registration problems at high velocity. | Forge 1.20.1 (community 1.21.1 ports); code GPL-3, assets CC BY-NC-ND | Design reference only. We use non-entity segment-raycast bullets, which avoids the issue the Raycast addon fixes. |
| **Superb Warfare** ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/superb-warfare), [Modrinth](https://modrinth.com/mod/superb-warfare)) | The most complete modern Forge military mod: vehicles, helicopters, GeckoLib animation, Curios. Shows the scope modern players expect, and how heavy the dependency chain gets (GeckoLib, Curios, now Kotlin for Forge). | Forge 1.20.1; code GPL-3, assets all rights reserved | Reference for feature expectations; we deliberately stay dependency-free. |
| **MrCrayfish's Gun Mod** ([releases](https://www.github.com/MrCrayfish/MrCrayfishGunMod/releases)) | Projectile bullets with gravity; simple attachment system; long-lived codebase. | Older versions; LGPL | Reference for attachments UX. |
| **AshVehicle** ([GitHub](https://github.com/AshViper/AshVehicle)) | Vehicle addon for SBW. Confirms entity-based vehicles with seat logic are the norm. | 1.20.1 | Reference. |
| **GeckoLib** (Forge 1.21.1 builds 4.7–4.8.x exist: [CurseForge](https://www.curseforge.com/minecraft/mc-mods/geckolib), [Cloudsmith](https://cloudsmith.io/~geckolib3/repos/geckolib/packages/)) | The standard animation library. Compatible with Forge 1.21.1. | MIT | **Not used.** All our animations are procedural in Java (gun sway/kick/reload, turret rotation, flag cloth), so players install one jar. Revisit if keyframed soldier animations become a priority. |

### Engineering problems → chosen solutions
| Problem | Options considered | Decision |
|---|---|---|
| Bullet hit registration at 300–600 m/s | Bullet entities (miss thin targets, network heavy), pure hitscan (no drop, no travel time), **swept segment raycast per tick** | Server-side `BulletManager` advances each bullet one segment per tick and raycasts blocks + entity AABBs along it. Clients get one tracer packet and simulate the visual path themselves. |
| Recoil feel | Server-driven rotation (laggy) vs **client-applied camera recoil** | Client applies recoil to the camera immediately. The server receives the final aim vector, checked against the server-known look direction (±12°). |
| Fire rate validation | Trust client | Token-bucket rate limiter per player on the server; ammo is authoritative in the stack's data component. |
| AI at scale | Brain/memory system vs **GoalSelector** | GoalSelector goals (cover, capture, squad follow, revive, grenade) plus a per-team *Commander* that assigns squads to flags every few seconds. Cheap and debuggable. |
| Long AI paths across a battlefield | Increase follow range (expensive) | Waypoint stepping: path toward intermediate points about 24 blocks along the route. |
| Destruction | Custom voxel physics vs **vanilla explosions with resistance-aware block removal** + scripted set-piece | Vanilla `Level.explode` (TNT interaction) for gadgets, plus bullet-vs-block rules (glass/leaves shatter, thin blocks penetrate). The Levolution tower collapses into `FallingBlockEntity`s for a physical fall. |
| Animations without GeckoLib | Library vs procedural | Procedural transforms in `RenderHandEvent` and box-built entity models. |

---

## 4. Opportunities unique to Minecraft
- **The battlefield is your world.** The War Table deploys a Conquest battle onto *existing
  terrain* (flags, fortifications, HQs, a tower) instead of shipping a fixed map. Every match is
  different.
- **Terrain is cover, and cover is destructible.** Explosions carve craters, bullets shatter
  glass and leaves, and thin wood is penetrable. Engineers can drop sandbags to rebuild cover.
  The front line physically changes over a match.
- **Tanks crush soft blocks** (leaves, fences, glass, crops) as they drive, so armour opens
  new routes.
- **AI armies** make Conquest playable solo or in small co-op groups. Minecraft servers rarely
  have 64 players.

## 5. Limitations and open questions
- Physics-level vehicle simulation (suspension, rigid bodies) is out of scope; the tank uses
  arcade kinematics with step-up and slope following.
- Mob pathfinding cannot plan through blocks it would need to break, so destroyed terrain helps
  AI more than it hinders it. Ladders and towers are mostly ignored by AI.
- Very large AI counts (more than 60 soldiers) are CPU-bound by pathfinding. The default caps
  per team are conservative and configurable.
- Helicopters and jets are not planned until ground combined arms is solid.
- Multiplayer has been tested on an integrated server only. A dedicated server smoke test is
  in the test plan.
