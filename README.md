# WARFRONT

**Combined-arms Conquest warfare for Minecraft Java 1.21.1 (Forge 52.1+).**

Place a War Table and WARFRONT builds a full Conquest battlefield onto your world: two
headquarters, a line of fortified capture points, tank pads and a Comms Tower that can be
brought down. Two AI armies, the **Iron Coalition** and the **Ember Legion**, fight over the
flags in squads. You enlist, choose a kit, lead your own fireteam, and win by bleeding the enemy's
tickets dry.

Inspired by the design of Battlefield 4. All names, models, textures and sounds are original
(generated procedurally by the scripts in `tools/`).

## Features
- **Ballistic gunplay**: 8 weapons with real bullet travel and drop, spread bloom, first-shot
  recoil, damage falloff, headshots, wall penetration, glass shattering, suppression and tracers.
- **Attachments**: reflex, holo, 4x and 8x optics; suppressor, compensator, heavy barrel; grips
  and bipod. Each one is a trade-off, and they unlock with rank.
- **Conquest**: capture and neutralise flags, ticket bleed, reinforcement waves, commander AI,
  victory and defeat, all persistent across saves.
- **AI soldiers**: four classes with cover, suppression response, flanking moves, grenades,
  anti-tank rockets, medics who revive you, and recon spotting.
- **Your squad**: an AI fireteam follows you. Spot a flag to order it to attack or defend.
- **Kits & gadgets**: Assault (medic bag, defibrillator), Engineer (rockets, repair tool),
  Support (ammo bag, C4), Recon (spawn beacon, spotting binoculars, smoke). Everyone carries sandbags
  to fortify.
- **FAV-3 Jackal buggy**: fast two-seater with a rear heavy machine gun that overheats. Hop into an
  AI buggy and you drive while the AI crew mans the gun.
- **MBT-9 Juggernaut tank**: turret follows your view, main cannon plus coax MG, armour model
  with a disabled/burning state, engineer repairs, crushes fences and trees, AI tank crews.
- **Levolution**: blow the Comms Tower's base and it collapses onto the centre flag, cutting the
  radar uplink for good. Ignite the fuel depot by flag B and it smothers the flag in smoke that
  blinds AI and spotting.
- **Squad orders radial**: hold V to order attack/defend/follow/hold, or call for a medic or ammo.
- **Weapon mastery**: kills with a gun unlock its attachments. Scope glint gives snipers away.
- **BF-style HUD**: minimap, Conquest bar, kill feed, score popups, hit markers, deploy screen
  with a tactical map, and a downed/revive state.
- **Progression**: 14 ranks, career stats and per-weapon attachment unlocks.

## Controls (defaults, rebindable)
| Action | Key |
|---|---|
| Fire / aim down sights | LMB / RMB |
| Reload | R |
| Fire mode | B |
| Spot enemy / order squad to the flag you look at | Z |
| Squad orders radial (hold) | V |
| Deploy screen (at HQ or when dead) | J |
| Battle scoreboard | Tab |
| Give up while downed | hold Space |
| Tank: cannon / coax MG / drive | LMB / RMB / WASD |
| Buggy gunner: fire HMG | LMB |

## Playing
1. Craft a **War Table** (map, paper, compass, logs) or take one from the WARFRONT creative tab.
2. Place it facing the open ground where you want the battle and right-click it.
3. Pick a mode (3, 5 or 7 flags), your army, AI count and tickets, then press **Deploy Battlefield**.
4. Choose a kit, weapon and attachments on the deploy screen and pick a spawn point on the map.

Server options are in `serverconfig/warfront-server.toml`: destruction scope, tank crushing,
AI per team, tickets, AI accuracy, damage multiplier, friendly fire and the downed state.

## Building
Requires JDK 21 (plus JDK 25 and JDK 8 for ForgeGradle 7's Mavenizer; see `docs/DEVELOPMENT.md`).

```
./gradlew build              # jar in build/libs
./gradlew runClient          # play in a dev client
./gradlew runGameTestServer  # run the in-world automated test suite
```

Docs: [`docs/RESEARCH.md`](docs/RESEARCH.md) (research and architecture rationale) ·
[`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md) (status, known issues, roadmap).
