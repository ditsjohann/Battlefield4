package com.ditsjohann.warfront.entity.ai;

import com.ditsjohann.warfront.entity.SoldierEntity;
import com.ditsjohann.warfront.match.ConquestMatch;
import com.ditsjohann.warfront.match.MatchManager;
import com.ditsjohann.warfront.match.Objective;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Moves the soldier toward its squad's destination: the Commander-assigned flag, a flag ordered
 * by the player squad leader, or the leader themself. Long routes are walked in 24-block
 * waypoints so pathfinding stays cheap. Inside a flag the squad spreads out and holds it.
 */
public class ObjectiveGoal extends Goal {
    private final SoldierEntity s;
    private int repath;
    private Vec3 loiter;
    private int loiterTimer;

    public ObjectiveGoal(SoldierEntity s) {
        this.s = s;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (s.isDowned() || s.getTarget() != null || s.getMatchId() == null) return false;
        return destination() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    private Vec3 destination() {
        if (!(s.level() instanceof ServerLevel level)) return null;
        ConquestMatch m = MatchManager.active(level);
        if (m == null || !s.getMatchId().equals(m.id)) return null;
        return m.squadDestination(level, s.getSquadId());
    }

    @Override
    public void start() {
        repath = 0;
    }

    @Override
    public void tick() {
        if (--repath > 0) return;
        repath = 20 + s.getRandom().nextInt(10);
        ServerLevel level = (ServerLevel) s.level();
        ConquestMatch m = MatchManager.active(level);
        Vec3 dest = destination();
        if (dest == null || m == null) return;
        // noises draw attention
        Vec3 noise = s.recentNoise();
        if (noise != null) s.getLookControl().setLookAt(noise.x, noise.y, noise.z);
        boolean following = m.squadFollowsPlayer(s.getSquadId());
        double dist = s.position().distanceTo(dest);
        if (following) {
            if (dist > 5) moveToward(level, dest, dist > 16 ? 1.2 : 1.0);
            else s.getNavigation().stop();
            return;
        }
        Objective flag = null;
        for (Objective o : m.flags) if (Vec3.atBottomCenterOf(o.pos).distanceToSqr(dest) < 1) flag = o;
        if (flag != null && flag.contains(s.getX(), s.getY(), s.getZ())) {
            // hold the flag: spread out inside the radius, crouch behind cover sometimes
            if (loiter == null || --loiterTimer <= 0 || s.getNavigation().isDone() && s.getRandom().nextInt(3) == 0) {
                double a = s.getRandom().nextDouble() * Math.PI * 2;
                double r = 2 + s.getRandom().nextDouble() * (flag.radius - 3);
                loiter = Vec3.atBottomCenterOf(flag.pos).add(Math.cos(a) * r, 0, Math.sin(a) * r);
                loiterTimer = 4 + s.getRandom().nextInt(5);
                s.setPose(Pose.STANDING);
                s.getNavigation().moveTo(loiter.x, loiter.y, loiter.z, 0.75);
            } else if (s.getNavigation().isDone() && s.getRandom().nextFloat() < 0.3f) {
                s.setPose(Pose.CROUCHING);
            }
            return;
        }
        loiter = null;
        if (s.getPose() == Pose.CROUCHING) s.setPose(Pose.STANDING);
        moveToward(level, dest, dist > 40 ? 1.15 : 1.0);
    }

    private void moveToward(ServerLevel level, Vec3 dest, double speed) {
        Vec3 goal = dest;
        double dist = s.position().distanceTo(dest);
        if (dist > 28) {
            Vec3 dir = dest.subtract(s.position()).multiply(1, 0, 1).normalize();
            // slight lateral jitter so squads don't walk in a single file
            Vec3 side = new Vec3(-dir.z, 0, dir.x).scale((s.getRandom().nextDouble() - 0.5) * 8);
            Vec3 wp = s.position().add(dir.scale(24)).add(side);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(wp.x), (int) Math.floor(wp.z));
            goal = new Vec3(wp.x, y, wp.z);
        }
        if (!s.getNavigation().moveTo(goal.x, goal.y, goal.z, speed)) {
            // unreachable waypoint: try a direct shorter hop
            Vec3 dir = dest.subtract(s.position()).normalize();
            Vec3 hop = s.position().add(dir.scale(8));
            s.getNavigation().moveTo(hop.x, hop.y, hop.z, speed);
        }
    }
}
