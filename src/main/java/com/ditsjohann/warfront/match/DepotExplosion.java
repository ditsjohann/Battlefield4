package com.ditsjohann.warfront.match;

import com.ditsjohann.warfront.Warfront;
import com.ditsjohann.warfront.combat.Explosions;
import com.ditsjohann.warfront.combat.SmokeScreens;
import com.ditsjohann.warfront.network.packets.SimplePackets;
import com.ditsjohann.warfront.progression.Scoring;
import com.ditsjohann.warfront.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Second Levolution set-piece: once the fuel depot's integrity runs out, the tanks go up one after
 * another, gouging a crater, scattering fires and blanketing flag B in a smoke screen that blinds
 * AI and spotting for 45 seconds — a window to storm (or lose) the flag.
 */
@Mod.EventBusSubscriber(modid = Warfront.MODID)
public final class DepotExplosion {
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
        m.depotDestroyed = true;
        PENDING.add(new Pending(level, m, cause));
        m.broadcast(level, new SimplePackets.Announce("FUEL DEPOT IGNITED", "Get clear!", 2));
        if (cause instanceof ServerPlayer sp) Scoring.award(sp, 300, "LEVOLUTION: FUEL DEPOT");
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) return;
        PENDING.removeIf(p -> {
            p.t++;
            List<BlockPos> tanks = p.match.fuelTanks;
            if (p.t == 1 || p.t == 22) {
                int idx = p.t == 1 ? 0 : 1;
                if (idx < tanks.size()) detonate(p, Vec3.atCenterOf(tanks.get(idx)));
            }
            if (p.t == 40 && p.match.depotPos != null) {
                Vec3 c = Vec3.atCenterOf(p.match.depotPos);
                detonate(p, c);
                ignite(p.level, p.match.depotPos, 9);
                SmokeScreens.add(p.level, c.add(0, 0.5, 0), 14f, 900);
                p.match.broadcast(p.level, new SimplePackets.Announce("SMOKE COVERS THE DEPOT", "Sight lines around flag B are blocked", 0));
                return true;
            }
            return false;
        });
    }

    private static void detonate(Pending p, Vec3 at) {
        Explosions.blast(p.level, null, p.cause, at, 5.5f, "Fuel Depot", 0);
        p.level.playSound(null, at.x, at.y, at.z, ModSounds.get("explosion"), SoundSource.BLOCKS, 6f, 0.7f);
    }

    private static void ignite(ServerLevel level, BlockPos center, int radius) {
        for (int i = 0; i < 26; i++) {
            int x = center.getX() + level.random.nextInt(radius * 2 + 1) - radius;
            int z = center.getZ() + level.random.nextInt(radius * 2 + 1) - radius;
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
            if (level.getBlockState(top).isAir() && BaseFireBlock.canBePlacedAt(level, top, net.minecraft.core.Direction.UP)) {
                level.setBlock(top, BaseFireBlock.getState(level, top), 3);
            }
        }
    }

    private DepotExplosion() {}
}
