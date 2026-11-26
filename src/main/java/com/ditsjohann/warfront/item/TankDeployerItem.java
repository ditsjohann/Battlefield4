package com.ditsjohann.warfront.item;

import com.ditsjohann.warfront.combat.Faction;
import com.ditsjohann.warfront.combat.Teams;
import com.ditsjohann.warfront.entity.TankEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/** Sandbox item: airdrops an MBT-9 tank for your side. */
public class TankDeployerItem extends Item {
    public TankDeployerItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getLevel() instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
        Player p = ctx.getPlayer();
        Faction f = Teams.of(p);
        TankEntity t = TankEntity.spawn(sl, ctx.getClickedPos().above(), f == null ? Faction.COALITION : f, null);
        if (t != null && p != null) {
            t.setYRot(p.getYRot());
            if (!p.getAbilities().instabuild) ctx.getItemInHand().shrink(1);
        }
        return InteractionResult.CONSUME;
    }
}
