package com.ditsjohann.warfront.item;

import com.ditsjohann.warfront.entity.FragGrenadeEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class FragGrenadeItem extends GadgetItem {
    public FragGrenadeItem() {
        super(2, "Right-click to throw. 3 second fuse. Bounces and rolls.");
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack s = p.getItemInHand(hand);
        if (charges(s) <= 0 && !p.getAbilities().instabuild) return InteractionResultHolder.fail(s);
        if (level instanceof ServerLevel sl && consume(p, s)) {
            Vec3 v = p.getLookAngle().scale(1.15).add(p.getDeltaMovement().multiply(1, 0, 1));
            FragGrenadeEntity.throwFrom(sl, p, p.getEyePosition(), v.add(0, 0.12, 0), 60);
            p.getCooldowns().addCooldown(this, 20);
        }
        p.swing(hand, true);
        return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
    }
}
