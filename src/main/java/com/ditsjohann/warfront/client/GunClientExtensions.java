package com.ditsjohann.warfront.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/** Third-person pose for players holding a gun: both hands on the weapon, shouldered, like soldiers. */
public final class GunClientExtensions implements IClientItemExtensions {
    public static final GunClientExtensions INSTANCE = new GunClientExtensions();

    @Override
    public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
        return hand == InteractionHand.MAIN_HAND ? HumanoidModel.ArmPose.CROSSBOW_HOLD : HumanoidModel.ArmPose.EMPTY;
    }

    private GunClientExtensions() {}
}
