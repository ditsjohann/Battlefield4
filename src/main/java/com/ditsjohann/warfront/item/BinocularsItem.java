package com.ditsjohann.warfront.item;

import com.ditsjohann.warfront.combat.Spotting;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/** Recon field binoculars: hold right-click to zoom; enemies at the centre of view are auto-spotted. */
public class BinocularsItem extends GadgetItem {
    public BinocularsItem() {
        super(0, "Hold right-click to scan. Enemies in the centre of your view are spotted for your team.");
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        p.startUsingItem(hand);
        return InteractionResultHolder.consume(p.getItemInHand(hand));
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPYGLASS;
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
        if (!level.isClientSide && remaining % 10 == 0) Spotting.spot(user, 220, 2.5);
    }
}
