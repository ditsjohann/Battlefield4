package com.ditsjohann.warfront.item;

import com.ditsjohann.warfront.combat.Faction;
import com.ditsjohann.warfront.combat.Teams;
import com.ditsjohann.warfront.entity.TankEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/** Sandbox item: airdrops a vehicle (MBT-9 tank or FAV-3 buggy) for your side. */
public class TankDeployerItem extends Item {
    private final boolean buggy;

    public TankDeployerItem(boolean buggy) {
        super(new Item.Properties().stacksTo(1));
        this.buggy = buggy;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getLevel() instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
        Player p = ctx.getPlayer();
        Faction f = Teams.of(p);
        com.ditsjohann.warfront.entity.VehicleEntity t = buggy
                ? com.ditsjohann.warfront.entity.BuggyEntity.spawn(sl, ctx.getClickedPos().above(), f == null ? Faction.COALITION : f, null)
                : TankEntity.spawn(sl, ctx.getClickedPos().above(), f == null ? Faction.COALITION : f, null);
        if (t != null && p != null) {
            t.setYRot(p.getYRot());
            if (!p.getAbilities().instabuild) ctx.getItemInHand().shrink(1);
        }
        return InteractionResult.CONSUME;
    }
}
