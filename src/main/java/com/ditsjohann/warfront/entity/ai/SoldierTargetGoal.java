package com.ditsjohann.warfront.entity.ai;

import com.ditsjohann.warfront.combat.CombatEvents;
import com.ditsjohann.warfront.combat.Spotting;
import com.ditsjohann.warfront.combat.Teams;
import com.ditsjohann.warfront.entity.SoldierEntity;
import com.ditsjohann.warfront.entity.SoldierRole;
import com.ditsjohann.warfront.entity.TankEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.EnumSet;

/**
 * Picks the most threatening visible enemy: closer is worse, spotted enemies count as closer,
 * whoever is shooting at us counts most. Engineers with rockets go after enemy armour.
 */
public class SoldierTargetGoal extends TargetGoal {
    private final SoldierEntity soldier;
    private LivingEntity candidate;
    private int cooldown;

    public SoldierTargetGoal(SoldierEntity soldier) {
        super(soldier, false, false);
        this.soldier = soldier;
        this.setFlags(EnumSet.of(Flag.TARGET));
    }

    public static boolean validTarget(SoldierEntity s, Entity e) {
        if (!e.isAlive() || e == s) return false;
        if (e instanceof TankEntity t) return Teams.enemies(s, t) && t.hasCrew();
        if (!(e instanceof LivingEntity le)) return false;
        if (le instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
        if (CombatEvents.isDowned(le)) return false;
        if (le.getVehicle() instanceof TankEntity) return false; // shoot the tank, not its crew
        if (Teams.enemies(s, le)) return true;
        return le instanceof Enemy && s.distanceToSqr(le) < 28 * 28;
    }

    @Override
    public boolean canUse() {
        if (soldier.isDowned()) return false;
        if (--cooldown > 0) return false;
        cooldown = 8 + soldier.getRandom().nextInt(6);
        candidate = find();
        return candidate != null;
    }

    private LivingEntity find() {
        double range = soldier.getRole() == SoldierRole.RECON ? 110 : 72;
        AABB box = soldier.getBoundingBox().inflate(range, 24, range);
        LivingEntity attacker = soldier.recentAttacker();
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        boolean antiArmour = soldier.getRole() == SoldierRole.ENGINEER && soldier.rockets > 0;
        for (Entity e : soldier.level().getEntities(soldier, box, e -> validTarget(soldier, e))) {
            if (e instanceof TankEntity t) {
                if (!antiArmour) continue;
                double d = soldier.distanceTo(t);
                if (d < 80 && soldier.getSensing().hasLineOfSight(t)) {
                    double score = d * 0.4; // armour is the engineer's priority
                    if (score < bestScore && t.getControllingPassenger() instanceof LivingEntity crew) {
                        // target the vehicle by proxy: the combat goal reads the vehicle from the crew
                        bestScore = score;
                        best = crew;
                    }
                }
                continue;
            }
            LivingEntity le = (LivingEntity) e;
            double d = soldier.distanceTo(le);
            if (d > range) continue;
            if (!soldier.getSensing().hasLineOfSight(le)) continue;
            double score = d;
            if (Spotting.isSpotted(le)) score *= 0.7;
            if (le == attacker) score *= 0.4;
            if (le instanceof Player) score *= 0.9;
            if (score < bestScore) {
                bestScore = score;
                best = le;
            }
        }
        return best;
    }

    @Override
    public void start() {
        soldier.setTarget(candidate);
        // recon soldiers call out what they see
        if (soldier.getRole() == SoldierRole.RECON && soldier.level() instanceof ServerLevel sl && soldier.getRandom().nextFloat() < 0.4f) {
            Spotting.mark(sl, candidate.getVehicle() != null ? candidate.getVehicle() : candidate, null);
        }
        super.start();
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity t = soldier.getTarget();
        if (t == null || !t.isAlive() || soldier.isDowned()) return false;
        if (CombatEvents.isDowned(t)) return false;
        if (t instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
        if (soldier.distanceToSqr(t) > 120 * 120) return false;
        return super.canContinueToUse();
    }

    @Override
    protected double getFollowDistance() {
        return 110;
    }
}
