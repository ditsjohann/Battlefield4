package com.ditsjohann.warfront.combat;

import com.ditsjohann.warfront.Warfront;
import com.ditsjohann.warfront.WarfrontConfig;
import com.ditsjohann.warfront.match.ConquestMatch;
import com.ditsjohann.warfront.match.MatchManager;
import com.ditsjohann.warfront.network.WarfrontNet;
import com.ditsjohann.warfront.network.packets.SimplePackets;
import com.ditsjohann.warfront.registry.ModBlocks;
import com.ditsjohann.warfront.weapon.GunLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * All WARFRONT explosions go through here: kill-feed attribution, friendly-fire filtering,
 * destruction rules, damage to the Levolution tower, and camera shake for nearby players.
 */
@Mod.EventBusSubscriber(modid = Warfront.MODID)
public final class Explosions {
    private record Ctx(String weapon, @Nullable Entity owner, int towerDamage) {}

    private static final ThreadLocal<Ctx> CURRENT = new ThreadLocal<>();

    public static void blast(ServerLevel level, @Nullable Entity direct, @Nullable Entity owner, Vec3 pos, float power, String weapon, int towerDamage) {
        boolean destroy = switch (WarfrontConfig.DESTRUCTION.get()) {
            case ALL -> true;
            case NONE -> false;
            case BATTLEFIELD_ONLY -> {
                ConquestMatch m = MatchManager.active(level);
                yield m != null && m.inBounds(BlockPos.containing(pos));
            }
        };
        CURRENT.set(new Ctx(weapon, owner, towerDamage));
        try {
            level.explode(direct, level.damageSources().explosion(direct, owner), null, pos.x, pos.y, pos.z, power, false,
                    destroy ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
        } finally {
            CURRENT.remove();
        }
        level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.x, pos.y + 0.5, pos.z, (int) (power * 6), power * 0.4, power * 0.3, power * 0.4, 0.02);
        level.sendParticles(ParticleTypes.LAVA, pos.x, pos.y + 0.3, pos.z, (int) (power * 3), 0.3, 0.3, 0.3, 0.1);
        GunLogic.playLayered(level, pos, power >= 3.5f ? "explosion" : "grenade_explode", "explosion_far", null, 2.5f, 0.9f + level.random.nextFloat() * 0.2f, 40);
        for (ServerPlayer p : level.players()) {
            double d = p.position().distanceTo(pos);
            if (d < power * 12) WarfrontNet.toPlayer(p, new SimplePackets.Shake((float) (power * 0.5 * (1 - d / (power * 12)))));
        }
    }

    private static final Set<Block> PROTECTED = new java.util.HashSet<>();

    private static boolean isProtected(Block b) {
        if (PROTECTED.isEmpty()) {
            PROTECTED.add(ModBlocks.CAPTURE_POINT.get());
            PROTECTED.add(ModBlocks.HQ_BEACON.get());
            PROTECTED.add(ModBlocks.VEHICLE_PAD.get());
            PROTECTED.add(ModBlocks.WAR_TABLE.get());
            PROTECTED.add(ModBlocks.TOWER_CORE.get());
        }
        return PROTECTED.contains(b);
    }

    @SubscribeEvent
    public static void onDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        // objective hardware is indestructible no matter who blows it up
        event.getAffectedBlocks().removeIf(p -> isProtected(level.getBlockState(p).getBlock()));
        Ctx ctx = CURRENT.get();
        if (ctx == null) return;
        if (!WarfrontConfig.FRIENDLY_FIRE.get() && ctx.owner != null) {
            event.getAffectedEntities().removeIf(e -> e != ctx.owner && Teams.allies(ctx.owner, e));
        }
        for (Entity e : event.getAffectedEntities()) {
            if (e instanceof LivingEntity) CombatEvents.recordWeapon(e, ctx.weapon);
        }
        ConquestMatch m = MatchManager.active(level);
        if (m != null && m.towerBase != null && !m.towerCollapsed && ctx.towerDamage > 0) {
            Vec3 c = event.getExplosion().center();
            BlockPos tb = m.towerBase;
            double dx = Math.max(Math.abs(c.x - (tb.getX() + 2.5)) - 2.5, 0);
            double dz = Math.max(Math.abs(c.z - (tb.getZ() + 2.5)) - 2.5, 0);
            double dy = c.y - tb.getY();
            if (dx < 3 && dz < 3 && dy > -3 && dy < 10) m.damageTower(level, ctx.towerDamage, ctx.owner);
        }
        if (m != null && m.depotPos != null && !m.depotDestroyed && ctx.towerDamage > 0) {
            Vec3 c = event.getExplosion().center();
            if (c.distanceTo(Vec3.atCenterOf(m.depotPos)) < 7.5) m.damageDepot(level, ctx.towerDamage + 2, ctx.owner);
        }
    }

    private Explosions() {}
}
