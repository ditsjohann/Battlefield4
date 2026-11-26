package com.ditsjohann.warfront.entity.ai;

import com.ditsjohann.warfront.entity.SoldierEntity;
import com.ditsjohann.warfront.entity.SupplyBagEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/** Answers a "need medic" / "need ammo" call: run to the player and drop a bag at their feet. */
public class AssistPlayerGoal extends Goal {
    private final SoldierEntity s;
    private Player target;
    private int repath;

    public AssistPlayerGoal(SoldierEntity s) {
        this.s = s;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (s.assistTarget == null || s.isDowned()) return false;
        if (s.level().getGameTime() > s.assistUntil) {
            s.assistTarget = null;
            return false;
        }
        target = s.level().getPlayerByUUID(s.assistTarget);
        return target != null && target.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void tick() {
        if (target == null) return;
        s.getLookControl().setLookAt(target);
        if (s.distanceTo(target) > 3.2) {
            if (--repath <= 0) {
                s.getNavigation().moveTo(target, 1.3);
                repath = 10;
            }
            return;
        }
        s.getNavigation().stop();
        SupplyBagEntity.toss((ServerLevel) s.level(), s, s.assistAmmo, target.position().subtract(s.position()));
        s.assistTarget = null;
        target = null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}
