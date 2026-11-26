package com.ditsjohann.warfront.client;

import com.ditsjohann.warfront.Warfront;
import com.ditsjohann.warfront.combat.Faction;
import com.ditsjohann.warfront.entity.SoldierEntity;
import com.ditsjohann.warfront.entity.SoldierRole;
import com.ditsjohann.warfront.network.WarfrontNet;
import com.ditsjohann.warfront.network.packets.SimplePackets;
import com.ditsjohann.warfront.registry.ModItems;
import com.ditsjohann.warfront.weapon.GunLogic;
import com.ditsjohann.warfront.weapon.GunType;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Development harness: WARFRONT_AUTOTEST=range. A firing range on a flat survival world. Every
 * gun is fired at enemy dummies at 15/30/60 blocks; hits, damage, ammo bookkeeping, reloads and
 * fire modes are logged, and hip / ADS / firing / third-person / inventory screenshots are saved.
 */
public final class WeaponRangeTest {
    public static final boolean ENABLED = "range".equals(System.getenv("WARFRONT_AUTOTEST"));
    private static int stage, wait, gunIndex, shotsHeld, shots;
    private static final List<SoldierEntity> DUMMIES = new ArrayList<>();
    private static BlockPos origin = BlockPos.ZERO;

    private static void log(String s) { Warfront.LOG.info("[RANGE] {}", s); }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, "range_" + String.format("%02d", ++shots) + "_" + name + ".png", mc.getMainRenderTarget(), c -> {});
    }

    private static void server(Minecraft mc, Consumer<ServerPlayer> task) {
        MinecraftServer srv = mc.getSingleplayerServer();
        if (srv == null || mc.player == null) return;
        srv.execute(() -> {
            ServerPlayer p = srv.getPlayerList().getPlayer(mc.player.getUUID());
            if (p != null) task.accept(p);
        });
    }

    private static GunType gun() { return GunType.values()[gunIndex]; }

    public static void tick(Minecraft mc) {
        if (!ENABLED) return;
        if (wait > 0) {
            wait--;
            return;
        }
        switch (stage) {
            case 0 -> {
                if (mc.getOverlay() != null || mc.screen == null || mc.level != null) return;
                mc.options.renderDistance().set(6);
                GameRules rules = new GameRules();
                rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
                rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
                LevelSettings settings = new LevelSettings("warfront_range_" + System.currentTimeMillis(), GameType.SURVIVAL, false,
                        Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel(settings.levelName(), settings, new WorldOptions(1L, false, false),
                        reg -> reg.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), new net.minecraft.client.gui.screens.TitleScreen());
                stage = 1;
            }
            case 1 -> {
                if (mc.player == null) return;
                mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
                wait = 80;
                stage = 2;
            }
            case 2 -> {
                // arm the next gun and set up the lane
                GunType g = gun();
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                server(mc, p -> {
                    ServerLevel level = p.serverLevel();
                    level.setDayTime(6000);
                    DUMMIES.forEach(d -> d.discard());
                    DUMMIES.clear();
                    p.getInventory().clearContent();
                    ItemStack stack = new ItemStack(ModItems.gun(g));
                    p.getInventory().setItem(0, stack);
                    p.getInventory().selected = 0;
                    origin = p.blockPosition();
                    p.teleportTo(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5);
                    p.setYRot(-90); // east
                    p.setXRot(0);
                    for (int d : new int[]{15, 30, 60}) {
                        SoldierEntity s = SoldierEntity.spawn(level, origin.offset(d, 0, 0), Faction.LEGION, SoldierRole.ASSAULT, -1, null);
                        if (s != null) {
                            s.removeFreeWill();
                            s.setNoAi(true);
                            s.setYRot(90);
                            s.yHeadRot = 90;
                            DUMMIES.add(s);
                        }
                    }
                });
                wait = 20;
                stage = 3;
            }
            case 3 -> {
                // aim at the middle dummy's chest
                if (mc.player != null) {
                    mc.player.setYRot(-90);
                    mc.player.setXRot(0.4f);
                    mc.player.yRotO = -90;
                }
                shot(mc, gun().id + "_hip");
                mc.options.keyUse.setDown(true);
                wait = 25;
                stage = 4;
            }
            case 4 -> {
                shot(mc, gun().id + "_ads");
                shotsHeld = 0;
                stage = 5;
            }
            case 5 -> {
                // auto: hold the trigger; semi/bolt: tap it
                GunType g = gun();
                boolean auto = g.stats.modeOrder[0] == com.ditsjohann.warfront.weapon.FireMode.AUTO;
                if (auto) mc.options.keyAttack.setDown(true);
                else mc.options.keyAttack.setDown(shotsHeld % 6 < 2);
                if (++shotsHeld == 8) shot(mc, g.id + "_firing");
                if (shotsHeld > (g == GunType.SR338 ? 70 : 40)) {
                    mc.options.keyAttack.setDown(false);
                    mc.options.keyUse.setDown(false);
                    wait = 20;
                    stage = 6;
                }
            }
            case 6 -> {
                GunType g = gun();
                int clientAmmo = mc.player == null ? -1 : GunController.displayAmmo(mc.player.getMainHandItem());
                server(mc, p -> {
                    ItemStack s = p.getMainHandItem();
                    StringBuilder hits = new StringBuilder();
                    for (LivingEntity d : DUMMIES) hits.append(String.format("%.1f ", d.isAlive() ? d.getMaxHealth() - d.getHealth() : 99f));
                    log(String.format("%-7s mag=%d/%d reserve=%d clientMag=%d reloading=%s damage@15/30/60=%s", g.id, GunLogic.ammo(s), g.stats.magSize,
                            GunLogic.reserve(s), clientAmmo, GunLogic.reloading(s), hits));
                });
                WarfrontNet.toServer(new SimplePackets.Action(SimplePackets.Action.RELOAD));
                wait = Math.round(gun().stats.reloadTicks * 0.42f);
                stage = 20;
            }
            case 20 -> {
                shot(mc, gun().id + "_reload");
                wait = gun().stats.reloadEmptyTicks + 10;
                stage = 21;
            }
            case 21 -> {
                // sprint pose
                mc.options.keySprint.setDown(true);
                mc.options.keyUp.setDown(true);
                wait = 14;
                stage = 22;
            }
            case 22 -> {
                shot(mc, gun().id + "_sprint");
                mc.options.keySprint.setDown(false);
                mc.options.keyUp.setDown(false);
                wait = 10;
                stage = gunIndex == 0 ? 23 : 7;
            }
            case 23 -> {
                if (mc.player != null) Movement.setProne(mc.player, true);
                wait = 15;
                stage = 24;
            }
            case 24 -> {
                log("prone pose=" + (mc.player == null ? "?" : mc.player.getPose()) + " eye=" + (mc.player == null ? 0 : mc.player.getEyeHeight()));
                shot(mc, gun().id + "_prone");
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                wait = 8;
                stage = 25;
            }
            case 25 -> {
                shot(mc, gun().id + "_prone_third");
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                if (mc.player != null) Movement.setProne(mc.player, false);
                wait = 10;
                stage = 7;
            }
            case 7 -> {
                GunType g = gun();
                server(mc, p -> {
                    ItemStack s = p.getMainHandItem();
                    log(String.format("%-7s after reload mag=%d reserve=%d reloading=%s", g.id, GunLogic.ammo(s), GunLogic.reserve(s), GunLogic.reloading(s)));
                });
                if (g.stats.modeOrder.length > 1) WarfrontNet.toServer(new SimplePackets.Action(SimplePackets.Action.FIRE_MODE));
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                wait = 10;
                stage = 8;
            }
            case 8 -> {
                shot(mc, gun().id + "_third_person");
                GunType g = gun();
                server(mc, p -> {
                    ItemStack s = p.getMainHandItem();
                    log(String.format("%-7s fire mode now %s", g.id, GunLogic.fireMode(s, g)));
                });
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                gunIndex++;
                stage = gunIndex < Math.min(GunType.values().length, (System.getenv("WARFRONT_RANGE_GUNS") == null ? 99 : Integer.parseInt(System.getenv("WARFRONT_RANGE_GUNS")))) ? 2 : 30;
                wait = 5;
            }
            case 30 -> {
                // gear line-up: one soldier of every role and faction, close to the camera
                server(mc, p -> {
                    ServerLevel level = p.serverLevel();
                    DUMMIES.forEach(d -> d.discard());
                    DUMMIES.clear();
                    BlockPos o = p.blockPosition();
                    int i = 0;
                    for (SoldierRole r : SoldierRole.values()) {
                        for (Faction f : Faction.values()) {
                            SoldierEntity s = SoldierEntity.spawn(level, o.offset(5, 0, -4 + i * 1), f, r, -1, null);
                            i++;
                            if (s != null) {
                                s.removeFreeWill();
                                s.setNoAi(true);
                                s.setYRot(90 - 25);
                                s.yBodyRot = 90 - 25;
                                s.yHeadRot = 90 - 25;
                                DUMMIES.add(s);
                            }
                        }
                    }
                });
                if (mc.player != null) {
                    mc.player.setYRot(-90);
                    mc.player.setXRot(12);
                }
                wait = 20;
                stage = 31;
            }
            case 31 -> {
                shot(mc, "gear_lineup");
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                wait = 5;
                stage = 9;
            }
            case 9 -> {
                server(mc, p -> {
                    p.getInventory().clearContent();
                    int i = 0;
                    for (GunType g : GunType.values()) p.getInventory().setItem(i++, new ItemStack(ModItems.gun(g)));
                    p.getInventory().setItem(i, new ItemStack(ModItems.ROCKET_LAUNCHER.get()));
                });
                wait = 10;
                stage = 10;
            }
            case 10 -> {
                if (mc.player != null) mc.setScreen(new InventoryScreen(mc.player));
                wait = 10;
                stage = 11;
            }
            case 11 -> {
                shot(mc, "inventory");
                mc.setScreen(null);
                log("done");
                wait = 10;
                stage = 12;
            }
            case 12 -> mc.stop();
            default -> {}
        }
    }

    private WeaponRangeTest() {}
}
