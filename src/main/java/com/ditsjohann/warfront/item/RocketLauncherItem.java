package com.ditsjohann.warfront.item;

import com.ditsjohann.warfront.entity.RocketEntity;
import com.ditsjohann.warfront.registry.ModSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** RL-4 Breacher: unguided anti-armour rocket. Devastating against tanks and walls alike. */
public class RocketLauncherItem extends GadgetItem {
    public static final int RELOAD = 70;

    public RocketLauncherItem() {
        super(5, "Right-click to fire a rocket. Direct hits cripple armour; blasts breach walls.");
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack s = p.getItemInHand(hand);
        if (charges(s) <= 0 && !p.getAbilities().instabuild) {
            level.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.get("dry_fire"), SoundSource.PLAYERS, 0.6f, 0.8f);
            return InteractionResultHolder.fail(s);
        }
        if (level instanceof ServerLevel sl && consume(p, s)) {
            Vec3 eye = p.getEyePosition();
            Vec3 dir = p.getLookAngle();
            RocketEntity.launch(sl, p, eye.add(dir.scale(0.8)).add(0, -0.1, 0), dir, RocketEntity.Kind.ROCKET);
            p.getCooldowns().addCooldown(this, RELOAD);
        }
        return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
    }
}
