package com.ditsjohann.warfront.match;

import com.ditsjohann.warfront.Warfront;
import com.ditsjohann.warfront.network.WarfrontNet;
import com.ditsjohann.warfront.network.packets.SimplePackets;
import com.ditsjohann.warfront.progression.Scoring;
import com.ditsjohann.warfront.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Levolution: when the Comms Tower's integrity hits zero, alarms sound and after a few seconds
 * the whole structure topples toward the centre flag as physical falling blocks, crushing
 * anything beneath it, burying the flag in rubble and permanently cutting the radar uplink.
 */
@Mod.EventBusSubscriber(modid = Warfront.MODID)
public final class TowerCollapse {
    private static final int ALARM_TICKS = 80;

    private static final class Pending {
        final ServerLevel level;
        final ConquestMatch match;
        final @Nullable Entity cause;
        int t;

        Pending(ServerLevel level, ConquestMatch match, @Nullable Entity cause) {
            this.level = level;
            this.match = match;
            this.cause = cause;
        }
    }

    private static final List<Pending> PENDING = new ArrayList<>();

    static void begin(ServerLevel level, ConquestMatch m, @Nullable Entity cause) {
        for (Pending p : PENDING) if (p.match == m) return;
        PENDING.add(new Pending(level, m, cause));
        m.broadcast(level, new SimplePackets.Announce("COMMS TOWER COLLAPSING", "Get clear of the tower!", 2));
        BlockPos b = m.towerBase;
        level.playSound(null, b.getX() + 2, b.getY() + 10, b.getZ() + 2, ModSounds.get("tower_alarm"), SoundSource.BLOCKS, 6f, 1f);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) return;
        PENDING.removeIf(p -> {
            p.t++;
            BlockPos b = p.match.towerBase;
            if (b == null) return true;
            if (p.t % 20 == 0 && p.t < ALARM_TICKS) {
                p.level.playSound(null, b.getX() + 2, b.getY() + 10, b.getZ() + 2, ModSounds.get("tower_alarm"), SoundSource.BLOCKS, 6f, 1f);
                p.level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, b.getX() + 2.5, b.getY() + 1, b.getZ() + 2.5, 10, 2, 0.5, 2, 0.02);
            }
            if (p.t == ALARM_TICKS) {
                collapse(p.level, p.match, p.cause);
                return true;
            }
            return false;
        });
    }

    public static void collapse(ServerLevel level, ConquestMatch m, @Nullable Entity cause) {
        BlockPos b = m.towerBase;
        if (b == null || m.towerCollapsed) return;
        m.towerCollapsed = true;
        m.towerIntegrity = 0;
        // fall toward the tower's flag
        Vec3 towerCenter = new Vec3(b.getX() + 2.5, b.getY(), b.getZ() + 2.5);
        Vec3 dir = new Vec3(1, 0, 0);
        if (m.towerFlag >= 0 && m.towerFlag < m.flags.size()) {
            Vec3 f = Vec3.atBottomCenterOf(m.flags.get(m.towerFlag).pos);
            dir = f.subtract(towerCenter).multiply(1, 0, 1).normalize();
        }
        int count = 0;
        for (int h = 0; h <= 32; h++) {
            for (int dx = 0; dx <= 4; dx++) {
                for (int dz = 0; dz <= 4; dz++) {
                    BlockPos q = b.offset(dx, h, dz);
                    BlockState s = level.getBlockState(q);
                    if (s.isAir() || s.getDestroySpeed(level, q) < 0 && !s.is(com.ditsjohann.warfront.registry.ModBlocks.TOWER_CORE.get())) continue;
                    if (h == 0) {
                        // the base stays as a ruined stump
                        continue;
                    }
                    FallingBlockEntity fb = FallingBlockEntity.fall(level, q, s);
                    double lean = h / 30.0;
                    fb.setDeltaMovement(dir.x * lean * 0.9 + (level.random.nextDouble() - 0.5) * 0.12,
                            0.05 + level.random.nextDouble() * 0.1,
                            dir.z * lean * 0.9 + (level.random.nextDouble() - 0.5) * 0.12);
                    fb.dropItem = false;
                    fb.setHurtsEntities(2.0f, 30);
                    count++;
                }
            }
        }
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, towerCenter.x, towerCenter.y + 2, towerCenter.z, 3, 2, 1, 2, 0);
        level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, towerCenter.x, towerCenter.y + 4, towerCenter.z, 80, 4, 6, 4, 0.04);
        level.playSound(null, towerCenter.x, towerCenter.y + 8, towerCenter.z, ModSounds.get("tower_collapse"), SoundSource.BLOCKS, 8f, 1f);
        for (ServerPlayer p : level.players()) {
            double d = p.position().distanceTo(towerCenter);
            if (d < 220) WarfrontNet.toPlayer(p, new SimplePackets.Shake((float) Math.max(0.4, 4.0 * (1 - d / 220))));
        }
        m.broadcast(level, new SimplePackets.Announce("THE COMMS TOWER HAS FALLEN", "Radar uplink lost for both sides", 0));
        if (cause instanceof ServerPlayer sp) Scoring.award(sp, 500, "LEVOLUTION: TOWER DEMOLISHED");
        Warfront.LOG.info("Comms tower collapsed into {} falling blocks", count);
    }

    private TowerCollapse() {}
}
