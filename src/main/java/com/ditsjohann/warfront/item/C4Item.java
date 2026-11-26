package com.ditsjohann.warfront.item;

import com.ditsjohann.warfront.entity.C4Entity;
import com.ditsjohann.warfront.entity.VehicleEntity;
import com.ditsjohann.warfront.registry.ModSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Place charges on surfaces or vehicles; right-click the air (or sneak + right-click) to detonate them all. */
public class C4Item extends GadgetItem {
    public C4Item() {
        super(3, "Right-click a surface to plant a charge. Right-click the air to detonate every charge you planted.");
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player p = ctx.getPlayer();
        if (p == null || p.isSecondaryUseActive()) return InteractionResult.PASS;
        ItemStack s = ctx.getItemInHand();
        if (charges(s) <= 0 && !p.getAbilities().instabuild) return InteractionResult.FAIL;
        if (ctx.getLevel() instanceof ServerLevel sl && consume(p, s)) {
            Vec3 at = ctx.getClickLocation().add(Vec3.atLowerCornerOf(ctx.getClickedFace().getNormal()).scale(0.06));
            C4Entity.place(sl, p, at, ctx.getClickedFace(), null);
        }
        return InteractionResult.sidedSuccess(ctx.getLevel().isClientSide);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack s, Player p, LivingEntity target, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack s = p.getItemInHand(hand);
        if (level instanceof ServerLevel sl) {
            // planting on a vehicle we are looking at
            Vec3 eye = p.getEyePosition(), end = eye.add(p.getLookAngle().scale(3.5));
            for (VehicleEntity t : sl.getEntitiesOfClass(VehicleEntity.class, p.getBoundingBox().inflate(4))) {
                var hit = t.getBoundingBox().clip(eye, end);
                if (hit.isPresent() && !p.isSecondaryUseActive() && (charges(s) > 0 || p.getAbilities().instabuild) && consume(p, s)) {
                    C4Entity.place(sl, p, hit.get(), net.minecraft.core.Direction.UP, t);
                    return InteractionResultHolder.success(s);
                }
            }
            int n = C4Entity.detonateAll(sl, p);
            if (n == 0) sl.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.get("dry_fire"), SoundSource.PLAYERS, 0.6f, 1.2f);
        }
        return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
    }
}
