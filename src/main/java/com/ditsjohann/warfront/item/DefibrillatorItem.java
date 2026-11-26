package com.ditsjohann.warfront.item;

import com.ditsjohann.warfront.combat.CombatEvents;
import com.ditsjohann.warfront.combat.Teams;
import com.ditsjohann.warfront.registry.ModSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/** Revives downed teammates. Also delivers a lethal shock to an unsuspecting enemy at arm's length. */
public class DefibrillatorItem extends GadgetItem {
    public DefibrillatorItem() {
        super(0, "Right-click a downed teammate to revive them (refunds a ticket).");
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player p, LivingEntity target, InteractionHand hand) {
        if (!(p.level() instanceof ServerLevel sl) || p.getCooldowns().isOnCooldown(this)) return InteractionResult.sidedSuccess(p.level().isClientSide);
        if (CombatEvents.isDowned(target) && Teams.allies(p, target)) {
            CombatEvents.revive(sl, target, p);
            p.getCooldowns().addCooldown(this, 30);
            return InteractionResult.SUCCESS;
        }
        if (!CombatEvents.isDowned(target) && Teams.enemies(p, target)) {
            CombatEvents.recordWeapon(target, "Defibrillator");
            target.hurt(sl.damageSources().playerAttack(p), 40f);
            sl.playSound(null, target.getX(), target.getY(), target.getZ(), ModSounds.get("defib"), SoundSource.PLAYERS, 1f, 1.3f);
            p.getCooldowns().addCooldown(this, 40);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    /** Right-clicking near a crawling teammate also works (downed players have a small hitbox). */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack s = p.getItemInHand(hand);
        if (level instanceof ServerLevel sl && !p.getCooldowns().isOnCooldown(this)) {
            AABB box = p.getBoundingBox().inflate(2.0);
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, box, e -> e != p && CombatEvents.isDowned(e) && Teams.allies(p, e))) {
                CombatEvents.revive(sl, e, p);
                p.getCooldowns().addCooldown(this, 30);
                return InteractionResultHolder.success(s);
            }
        }
        return InteractionResultHolder.pass(s);
    }
}
