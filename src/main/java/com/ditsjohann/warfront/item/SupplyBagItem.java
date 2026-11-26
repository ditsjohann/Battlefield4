package com.ditsjohann.warfront.item;

import com.ditsjohann.warfront.entity.SupplyBagEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Medic bag or ammo bag: throw it down and teammates around it heal or restock. */
public class SupplyBagItem extends GadgetItem {
    private final boolean ammo;

    public SupplyBagItem(boolean ammo) {
        super(2, ammo ? "Toss down to resupply ammunition and grenades for nearby teammates." : "Toss down to heal nearby teammates over time.");
        this.ammo = ammo;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack s = p.getItemInHand(hand);
        if (charges(s) <= 0 && !p.getAbilities().instabuild) return InteractionResultHolder.fail(s);
        if (level instanceof ServerLevel sl && consume(p, s)) {
            SupplyBagEntity.toss(sl, p, ammo);
            p.getCooldowns().addCooldown(this, 100);
        }
        p.swing(hand, true);
        return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
    }
}
