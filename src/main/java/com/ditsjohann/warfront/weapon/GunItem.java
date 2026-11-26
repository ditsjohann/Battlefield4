package com.ditsjohann.warfront.weapon;

import com.ditsjohann.warfront.registry.ModComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** A firearm. All per-gun state (ammo, reserve, reload, fire mode, attachments) lives in data components. */
public class GunItem extends Item {
    public final GunType type;

    public GunItem(GunType type) {
        super(new Item.Properties().stacksTo(1));
        this.type = type;
    }

    private DataComponentMap defaults;

    /**
     * Default components are attached lazily: vanilla registers data component types after items,
     * so they can't be referenced from the Item constructor.
     */
    @Override
    public DataComponentMap components() {
        if (defaults == null) {
            if (!ModComponents.AMMO.isPresent()) return super.components();
            defaults = DataComponentMap.composite(super.components(), DataComponentMap.builder()
                    .set(ModComponents.AMMO.get(), type.stats.magSize)
                    .set(ModComponents.RESERVE.get(), type.stats.reserve)
                    .set(ModComponents.FIRE_MODE.get(), 0)
                    .set(ModComponents.LOADOUT.get(), type.defaultLoadout())
                    .build());
        }
        return defaults;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide && entity instanceof LivingEntity le) {
            GunLogic.tickReload(le, stack, type, level.getGameTime(), selected && le.getMainHandItem() == stack);
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        // right click is aim-down-sights, handled entirely on the client
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    @Override
    public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(com.ditsjohann.warfront.client.GunClientExtensions.INSTANCE);
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return false;
    }

    @Override
    public boolean onEntitySwing(ItemStack stack, LivingEntity entity) {
        return true;
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return GunLogic.ammo(stack) < type.stats.magSize;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13f * GunLogic.ammo(stack) / type.stats.magSize);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        float f = (float) GunLogic.ammo(stack) / type.stats.magSize;
        return Mth.hsvToRgb(f / 3f, 0.8f, 1f);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.literal(type.displayName);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        GunType.Stats s = type.stats;
        tip.add(Component.literal(type.category.label).withStyle(ChatFormatting.GOLD));
        tip.add(Component.literal("Ammo " + GunLogic.ammo(stack) + " / " + GunLogic.reserve(stack)).withStyle(ChatFormatting.GRAY));
        tip.add(stat("Damage", String.format("%.1f - %.1f", s.damageMax * s.pellets, s.damageMin * s.pellets)));
        tip.add(stat("Rate of fire", s.rpm + " RPM"));
        tip.add(stat("Muzzle velocity", Math.round(s.velocity * 20) + " m/s"));
        Loadout l = GunLogic.loadout(stack, type);
        tip.add(Component.literal(l.optic().label + " | " + l.muzzle().label + " | " + l.underbarrel().label).withStyle(ChatFormatting.DARK_AQUA));
        StringBuilder modes = new StringBuilder();
        for (FireMode m : s.modeOrder) modes.append(m.label).append(' ');
        tip.add(Component.literal(modes.toString().trim()).withStyle(ChatFormatting.DARK_GRAY));
    }

    private static Component stat(String k, String v) {
        return Component.literal(k + ": ").withStyle(ChatFormatting.GRAY).append(Component.literal(v).withStyle(ChatFormatting.WHITE));
    }
}
