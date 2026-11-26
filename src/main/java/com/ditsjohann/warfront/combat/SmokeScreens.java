package com.ditsjohann.warfront.combat;

import com.ditsjohann.warfront.Warfront;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Smoke screens (smoke grenades, burning fuel depots). Smoke is a gameplay object, not just
 * particles: it blocks AI line of sight, spotting and vehicle targeting, as smoke blocked 3D
 * spotting in BF4.
 */
@Mod.EventBusSubscriber(modid = Warfront.MODID)
public final class SmokeScreens {
    public record Smoke(Vec3 center, float radius, long until) {}

    private static final Map<Level, List<Smoke>> ACTIVE = new WeakHashMap<>();

    public static void add(ServerLevel level, Vec3 center, float radius, int ticks) {
        ACTIVE.computeIfAbsent(level, l -> new ArrayList<>()).add(new Smoke(center, radius, level.getGameTime() + ticks));
    }

    public static List<Smoke> active(Level level) {
        List<Smoke> l = ACTIVE.get(level);
        return l == null ? List.of() : l;
    }

    /** True if the sight line a->b passes through any smoke cloud. */
    public static boolean blocks(Level level, Vec3 a, Vec3 b) {
        List<Smoke> l = ACTIVE.get(level);
        if (l == null || l.isEmpty()) return false;
        Vec3 seg = b.subtract(a);
        double len2 = seg.lengthSqr();
        for (Smoke s : l) {
            double r = s.radius * 0.85;
            double t = len2 < 1e-6 ? 0 : Math.max(0, Math.min(1, s.center.subtract(a).dot(seg) / len2));
            if (a.add(seg.scale(t)).distanceToSqr(s.center) < r * r) return true;
        }
        return false;
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent.Post event) {
        if (!(event.level instanceof ServerLevel level)) return;
        List<Smoke> l = ACTIVE.get(level);
        if (l == null || l.isEmpty()) return;
        long now = level.getGameTime();
        l.removeIf(s -> now > s.until);
        if (now % 4 != 0) return;
        for (Smoke s : l) {
            float fade = Math.min(1f, (s.until - now) / 80f);
            int count = Math.max(2, Math.round(s.radius * 1.4f * fade));
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(s.center) > 180 * 180) continue;
                level.sendParticles(p, ParticleTypes.CAMPFIRE_COSY_SMOKE, true, s.center.x, s.center.y + 1.2, s.center.z,
                        count, s.radius * 0.45, 1.0, s.radius * 0.45, 0.004);
                level.sendParticles(p, ParticleTypes.LARGE_SMOKE, true, s.center.x, s.center.y + 0.6, s.center.z,
                        count / 2 + 1, s.radius * 0.5, 0.6, s.radius * 0.5, 0.01);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ACTIVE.clear();
    }

    private SmokeScreens() {}
}
