package com.ditsjohann.warfront.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Finds tactical cover against a threat: a reachable, standable spot where the threat's line of
 * fire to a crouching soldier is blocked, preferring half cover (blocked when crouched, clear
 * when standing) so the soldier can peek and return fire. Sandbags are ideal half cover.
 */
public final class CoverFinder {
    @Nullable
    public static BlockPos find(PathfinderMob mob, Vec3 threatEye, int radius) {
        Level level = mob.level();
        BlockPos origin = mob.blockPosition();
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (int i = 0; i < 28; i++) {
            int dx = mob.getRandom().nextInt(radius * 2 + 1) - radius;
            int dz = mob.getRandom().nextInt(radius * 2 + 1) - radius;
            BlockPos column = origin.offset(dx, 0, dz);
            BlockPos p = ground(level, column);
            if (p == null) continue;
            Vec3 crouchEye = Vec3.atBottomCenterOf(p).add(0, 1.15, 0);
            Vec3 standEye = Vec3.atBottomCenterOf(p).add(0, 1.62, 0);
            boolean crouchBlocked = blocked(level, threatEye, crouchEye, mob);
            if (!crouchBlocked) continue;
            boolean standBlocked = blocked(level, threatEye, standEye, mob);
            double score = Math.sqrt(p.distSqr(origin));
            if (!standBlocked) score -= 3; // half cover: can peek
            // don't run toward the threat
            if (Vec3.atCenterOf(p).distanceTo(threatEye) < mob.position().distanceTo(threatEye) - 3) score += 6;
            if (score >= bestScore) continue;
            Path path = mob.getNavigation().createPath(p, 0);
            if (path == null || !path.canReach()) continue;
            bestScore = score;
            best = p;
        }
        return best;
    }

    private static boolean blocked(Level level, Vec3 from, Vec3 to, PathfinderMob mob) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob)).getType() == HitResult.Type.BLOCK;
    }

    @Nullable
    private static BlockPos ground(Level level, BlockPos column) {
        for (int dy = 3; dy >= -4; dy--) {
            BlockPos p = column.above(dy);
            if (level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)
                    && level.getBlockState(p).getCollisionShape(level, p).isEmpty()
                    && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()
                    && level.getFluidState(p).isEmpty()) {
                return p;
            }
        }
        return null;
    }

    private CoverFinder() {}
}
