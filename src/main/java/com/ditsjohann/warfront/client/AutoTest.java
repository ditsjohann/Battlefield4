package com.ditsjohann.warfront.client;

import com.ditsjohann.warfront.Warfront;
import com.ditsjohann.warfront.client.screen.DeployScreen;
import com.ditsjohann.warfront.client.screen.WarTableScreen;
import com.ditsjohann.warfront.combat.Faction;
import com.ditsjohann.warfront.entity.SoldierEntity;
import com.ditsjohann.warfront.entity.TankEntity;
import com.ditsjohann.warfront.match.BattlefieldGenerator;
import com.ditsjohann.warfront.match.ConquestMatch;
import com.ditsjohann.warfront.match.Deployment;
import com.ditsjohann.warfront.match.MatchManager;
import com.ditsjohann.warfront.match.Objective;
import com.ditsjohann.warfront.match.TowerCollapse;
import com.ditsjohann.warfront.network.packets.SimplePackets;
import com.ditsjohann.warfront.registry.ModBlocks;
import com.ditsjohann.warfront.weapon.Attachment;
import com.ditsjohann.warfront.weapon.GunType;
import com.ditsjohann.warfront.weapon.Loadout;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;

import java.util.function.Consumer;

/**
 * Development harness (only active when the WARFRONT_AUTOTEST environment variable is set):
 * creates a world, deploys a battlefield, exercises weapons, AI, the tank and the tower
 * collapse, logs battle telemetry and saves screenshots, then quits.
 */
public final class AutoTest {
    public static final boolean ENABLED = System.getenv("WARFRONT_AUTOTEST") != null && !"range".equals(System.getenv("WARFRONT_AUTOTEST"));
    private static int stage;
    private static int wait;
    private static int shots;
    private static BlockPos table;

    private static void log(String s) {
        Warfront.LOG.info("[AUTOTEST] {}", s);
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, "autotest_" + (++shots < 10 ? "0" : "") + shots + "_" + name + ".png", mc.getMainRenderTarget(), c -> {});
        log("screenshot " + name);
    }

    private static void server(Minecraft mc, Consumer<ServerPlayer> task) {
        MinecraftServer srv = mc.getSingleplayerServer();
        if (srv == null || mc.player == null) return;
        srv.execute(() -> {
            ServerPlayer p = srv.getPlayerList().getPlayer(mc.player.getUUID());
            if (p != null) task.accept(p);
        });
    }

    private static void look(Minecraft mc, float yaw, float pitch) {
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
        mc.player.yRotO = yaw;
        mc.player.xRotO = pitch;
    }

    private static void telemetry(ServerLevel level) {
        ConquestMatch m = MatchManager.active(level);
        if (m == null) {
            log("no match");
            return;
        }
        AABB area = new AABB(m.minX, level.getMinBuildHeight(), m.minZ, m.maxX, level.getMaxBuildHeight(), m.maxZ);
        int a = 0, b = 0, downed = 0, fighting = 0;
        for (SoldierEntity s : level.getEntitiesOfClass(SoldierEntity.class, area)) {
            if (s.getFaction() == Faction.COALITION) a++;
            else b++;
            if (s.isDowned()) downed++;
            if (s.getTarget() != null) fighting++;
        }
        StringBuilder flags = new StringBuilder();
        for (Objective o : m.flags) flags.append(o.letter).append('=').append(o.owner == null ? "-" : o.owner.tag).append(String.format("(%.2f %d:%d) ", o.control, o.countA, o.countB));
        int tanks = level.getEntitiesOfClass(TankEntity.class, area).size();
        log(String.format("state=%d tickets %d:%d soldiers %d:%d downed=%d engaged=%d tanks=%d tower=%d%s flags %s",
                m.state, m.tickets[0], m.tickets[1], a, b, downed, fighting, tanks, m.towerIntegrity, m.towerCollapsed ? "(down)" : "", flags));
    }

    public static void tick(Minecraft mc) {
        if (!ENABLED) return;
        if (wait > 0) {
            wait--;
            return;
        }
        switch (stage) {
            case 0 -> {
                if (mc.getOverlay() != null || mc.screen == null || mc.level != null) return; // title or first-launch onboarding
                log("creating world");
                mc.options.renderDistance().set(8);
                mc.options.fov().set(75);
                GameRules rules = new GameRules();
                rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
                rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
                LevelSettings settings = new LevelSettings("warfront_autotest_" + System.currentTimeMillis(), GameType.CREATIVE, false,
                        Difficulty.NORMAL, true, rules, WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel(settings.levelName(), settings, new WorldOptions(20251126L, false, false),
                        reg -> reg.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.NORMAL).value().createWorldDimensions(), new TitleScreen());
                stage = 1;
            }
            case 1 -> {
                if (mc.player == null || mc.level == null) return;
                log("in world");
                mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
                mc.getToasts().clear();
                wait = 160;
                stage = 2;
            }
            case 2 -> {
                // build the war table and start a medium conquest battle in front of us, facing east
                server(mc, p -> {
                    ServerLevel level = p.serverLevel();
                    level.setDayTime(6000);
                    BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.blockPosition());
                    table = at;
                    level.setBlock(at, ModBlocks.WAR_TABLE.get().defaultBlockState().setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.EAST), 3);
                    ConquestMatch m = BattlefieldGenerator.generate(level, at, Direction.EAST, 1, Faction.COALITION, 12, 150);
                    MatchManager.get(level).set(level, m);
                    m.join(p, Faction.COALITION);
                    log("battlefield generated: hq " + m.hq[0] + " / " + m.hq[1] + " tower " + m.towerBase);
                });
                wait = 60;
                stage = 3;
            }
            case 3 -> {
                if (!ClientState.match.active()) return; // generation can take a while
                mc.setScreen(new DeployScreen());
                ClientState.deployCountdown = 0;
                wait = 30;
                stage = 4;
            }
            case 4 -> {
                shot(mc, "deploy_screen");
                mc.setScreen(null);
                server(mc, p -> Deployment.deploy(p, new SimplePackets.Deploy(Deployment.SPAWN_HQ, 0, 0, GunType.AR17.id,
                        new Loadout(Attachment.RED_DOT, Attachment.NO_MUZZLE, Attachment.VERTICAL_GRIP))));
                wait = 40;
                stage = 5;
            }
            case 5 -> {
                look(mc, -90, 5); // east, toward the battle
                wait = 20;
                stage = 6;
            }
            case 6 -> {
                shot(mc, "hip_hud");
                mc.options.keyUse.setDown(true);
                wait = 25;
                stage = 7;
            }
            case 7 -> {
                shot(mc, "ads_reddot");
                mc.options.keyAttack.setDown(true);
                wait = 6;
                stage = 8;
            }
            case 8 -> {
                shot(mc, "firing");
                wait = 12;
                stage = 9;
            }
            case 9 -> {
                mc.options.keyAttack.setDown(false);
                mc.options.keyUse.setDown(false);
                server(mc, p -> telemetry(p.serverLevel()));
                wait = 400; // let the armies clash (warmup is 10s)
                stage = 10;
            }
            case 10 -> {
                server(mc, p -> telemetry(p.serverLevel()));
                // fly above the first contested area and watch the battle
                server(mc, p -> {
                    ConquestMatch m = MatchManager.active(p.serverLevel());
                    if (m == null) return;
                    Objective o = m.flags.get(1);
                    p.getAbilities().flying = true;
                    p.onUpdateAbilities();
                    p.teleportTo(o.pos.getX() - 18, o.pos.getY() + 12, o.pos.getZ() + 0.5);
                });
                wait = 60;
                stage = 11;
            }
            case 11 -> {
                look(mc, -90, 25);
                wait = 10;
                stage = 12;
            }
            case 12 -> {
                shot(mc, "battle_overview");
                wait = 300;
                stage = 13;
            }
            case 13 -> {
                server(mc, p -> telemetry(p.serverLevel()));
                server(mc, p -> {
                    ConquestMatch m = MatchManager.active(p.serverLevel());
                    if (m == null) return;
                    Objective o = m.flags.get(2);
                    p.teleportTo(o.pos.getX() - 10, o.pos.getY() + 3, o.pos.getZ() + 6);
                });
                wait = 50;
                stage = 14;
            }
            case 14 -> {
                look(mc, -120, 12);
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                wait = 10;
                stage = 15;
            }
            case 15 -> {
                shot(mc, "center_flag");
                // spawn a tank next to us and climb in
                server(mc, p -> {
                    TankEntity t = TankEntity.spawn(p.serverLevel(), p.blockPosition().offset(-4, 0, 0), Faction.COALITION, null);
                    if (t != null) {
                        t.setYRot(-90);
                        p.startRiding(t);
                    }
                });
                wait = 40;
                stage = 16;
            }
            case 16 -> {
                look(mc, -100, 10);
                wait = 10;
                stage = 17;
            }
            case 17 -> {
                shot(mc, "tank_drive");
                server(mc, p -> {
                    if (p.getVehicle() instanceof TankEntity t) t.fireCannon(p.serverLevel(), p);
                });
                wait = 3;
                stage = 18;
            }
            case 18 -> {
                shot(mc, "tank_fire");
                server(mc, p -> {
                    p.stopRiding();
                    ConquestMatch m = MatchManager.active(p.serverLevel());
                    if (m == null || m.towerBase == null) return;
                    BlockPos b = m.towerBase;
                    p.teleportTo(b.getX() - 26, b.getY() + 10, b.getZ() + 2.5);
                });
                wait = 60;
                stage = 19;
            }
            case 19 -> {
                look(mc, -90, 0);
                wait = 10;
                stage = 20;
            }
            case 20 -> {
                shot(mc, "tower_standing");
                server(mc, p -> {
                    ConquestMatch m = MatchManager.active(p.serverLevel());
                    if (m != null) {
                        m.towerIntegrity = 1;
                        m.damageTower(p.serverLevel(), 5, p);
                    }
                });
                wait = 95; // alarm is 80 ticks
                stage = 21;
            }
            case 21 -> {
                shot(mc, "tower_collapsing");
                wait = 60;
                stage = 22;
            }
            case 22 -> {
                shot(mc, "tower_rubble");
                server(mc, p -> telemetry(p.serverLevel()));
                // third-person look at a soldier
                server(mc, p -> {
                    ConquestMatch m = MatchManager.active(p.serverLevel());
                    if (m == null) return;
                    for (SoldierEntity s : p.serverLevel().getEntitiesOfClass(SoldierEntity.class, p.getBoundingBox().inflate(200))) {
                        if (s.isDowned()) continue;
                        p.teleportTo(s.getX() + 3, s.getY() + 1.2, s.getZ() + 3);
                        break;
                    }
                });
                wait = 30;
                stage = 23;
            }
            case 23 -> {
                if (mc.level != null && mc.player != null) {
                    SoldierEntity near = null;
                    for (var e : mc.level.entitiesForRendering()) if (e instanceof SoldierEntity s && (near == null || s.distanceTo(mc.player) < near.distanceTo(mc.player))) near = s;
                    if (near != null) {
                        double dx = near.getX() - mc.player.getX(), dz = near.getZ() - mc.player.getZ(), dy = near.getEyeY() - mc.player.getEyeY();
                        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90);
                        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
                        look(mc, yaw, pitch);
                    }
                }
                wait = 4;
                stage = 24;
            }
            case 24 -> {
                shot(mc, "soldier_closeup");
                mc.setScreen(new WarTableScreen(table == null ? BlockPos.ZERO : table));
                wait = 20;
                stage = 25;
            }
            case 25 -> {
                shot(mc, "war_table_screen");
                mc.setScreen(null);
                // look at the fuel depot beside flag B
                server(mc, p -> {
                    ConquestMatch m = MatchManager.active(p.serverLevel());
                    if (m == null || m.depotPos == null) return;
                    BlockPos d = m.depotPos;
                    p.teleportTo(d.getX() - 18, d.getY() + 8, d.getZ() - 8);
                });
                wait = 40;
                stage = 26;
            }
            case 26 -> {
                if (mc.player != null && ClientState.match.active()) {
                    BlockPos d = ClientState.match.depotPos();
                    double dx = d.getX() + 0.5 - mc.player.getX(), dz = d.getZ() + 0.5 - mc.player.getZ(), dy = d.getY() - mc.player.getEyeY();
                    look(mc, (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90), (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
                }
                wait = 10;
                stage = 27;
            }
            case 27 -> {
                shot(mc, "depot_standing");
                server(mc, p -> {
                    ConquestMatch m = MatchManager.active(p.serverLevel());
                    if (m != null) m.damageDepot(p.serverLevel(), 100, p);
                });
                wait = 26;
                stage = 28;
            }
            case 28 -> {
                shot(mc, "depot_explosion");
                wait = 80;
                stage = 29;
            }
            case 29 -> {
                shot(mc, "depot_smoke");
                // a buggy with an AI crew member; boarding players always take the wheel (vanilla), so the AI mans the gun
                server(mc, p -> {
                    com.ditsjohann.warfront.entity.BuggyEntity b = com.ditsjohann.warfront.entity.BuggyEntity.spawn(p.serverLevel(), p.blockPosition(), Faction.COALITION, null);
                    if (b == null) return;
                    ConquestMatch m = MatchManager.active(p.serverLevel());
                    SoldierEntity driver = SoldierEntity.spawn(p.serverLevel(), p.blockPosition(), Faction.COALITION, com.ditsjohann.warfront.entity.SoldierRole.ASSAULT, -1, m == null ? null : m.id);
                    if (driver != null) driver.startRiding(b, true);
                    p.startRiding(b, true);
                });
                wait = 40;
                stage = 30;
            }
            case 30 -> {
                shot(mc, "buggy_gunner");
                if (mc.player != null && mc.player.getVehicle() instanceof com.ditsjohann.warfront.entity.VehicleEntity v) {
                    log("client seat=" + v.seatOf(mc.player) + " passengers=" + v.getPassengers().size() + " usesControls=" + v.usesVehicleControls(mc.player));
                }
                server(mc, p -> {
                    if (p.getVehicle() instanceof com.ditsjohann.warfront.entity.VehicleEntity v) {
                        log("server seat=" + v.seatOf(p) + " gunner=" + (v.gunner() == null ? "none" : v.gunner().getName().getString()));
                    }
                });
                wait = 10;
                stage = 130;
            }
            case 130 -> {
                server(mc, p -> {
                    if (p.getVehicle() instanceof com.ditsjohann.warfront.entity.BuggyEntity b) {
                        log("buggy heat (AI gunner)=" + b.heat());
                    }
                });
                shot(mc, "buggy_firing");
                server(mc, p -> p.stopRiding());
                wait = 20;
                stage = 31;
            }
            case 31 -> {
                mc.setScreen(new com.ditsjohann.warfront.client.screen.CommoRoseScreen(false));
                wait = 6;
                stage = 32;
            }
            case 32 -> {
                shot(mc, "commo_rose");
                mc.setScreen(null);
                wait = 300;
                stage = 33;
            }
            case 33 -> {
                server(mc, p -> telemetry(p.serverLevel()));
                wait = 300;
                stage = 34;
            }
            case 34 -> {
                server(mc, p -> telemetry(p.serverLevel()));
                log("done");
                stage = 35;
                wait = 20;
            }
            case 35 -> mc.stop();
            default -> {}
        }
    }

    private AutoTest() {}
}
