package com.ditsjohann.warfront.entity.ai;

import com.ditsjohann.warfront.combat.CombatEvents;
import com.ditsjohann.warfront.combat.Teams;
import com.ditsjohann.warfront.entity.SoldierEntity;
import com.ditsjohann.warfront.entity.SoldierRole;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/** Assault-class medics run to downed teammates (soldiers and players) and revive them. */
public class ReviveAllyGoal extends Goal {
    private final SoldierEntity s;
    private LivingEntity patient;
    private int work;
    private int scan;

    public ReviveAllyGoal(SoldierEntity s) {
        this.s = s;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (s.getRole() != SoldierRole.ASSAULT || s.isDowned()) return false;
        if (--scan > 0) return false;
        scan = 20;
        if (s.getTarget() != null && s.getSensing().hasLineOfSight(s.getTarget()) && s.distanceTo(s.getTarget()) < 20) return false;
        LivingEntity best = null;
        double bd = 22 * 22;
        for (LivingEntity e : s.level().getEntitiesOfClass(LivingEntity.class, s.getBoundingBox().inflate(22),
                e -> e != s && CombatEvents.isDowned(e) && Teams.allies(s, e))) {
            double d = s.distanceToSqr(e);
            if (e instanceof Player) d *= 0.5; // players first
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        patient = best;
        return patient != null;
    }

    @Override
    public boolean canContinueToUse() {
        return patient != null && patient.isAlive() && CombatEvents.isDowned(patient) && !s.isDowned();
    }

    @Override
    public void start() {
        work = 0;
        s.getNavigation().moveTo(patient, 1.25);
    }

    @Override
    public void tick() {
        s.getLookControl().setLookAt(patient);
        if (s.distanceTo(patient) > 1.8) {
            if (s.getNavigation().isDone()) s.getNavigation().moveTo(patient, 1.25);
            work = 0;
            return;
        }
        s.getNavigation().stop();
        s.setPose(net.minecraft.world.entity.Pose.CROUCHING);
        if (++work >= 30) {
            CombatEvents.revive((ServerLevel) s.level(), patient, s);
            patient = null;
            s.setPose(net.minecraft.world.entity.Pose.STANDING);
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}
