package com.ditsjohann.warfront.item;

import com.ditsjohann.warfront.registry.ModComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Base for limited-use gadgets. Charges live in a data component; creative mode never runs out. */
public abstract class GadgetItem extends Item {
    private final int defaultCharges;
    private final String description;

    protected GadgetItem(int defaultCharges, String description) {
        super(new Item.Properties().stacksTo(1));
        this.defaultCharges = defaultCharges;
        this.description = description;
    }

    private DataComponentMap defaults;

    /** Lazy defaults: data component types register after items. */
    @Override
    public DataComponentMap components() {
        if (defaults == null) {
            if (!ModComponents.CHARGES.isPresent()) return super.components();
            defaults = defaultCharges <= 0 ? super.components() : DataComponentMap.composite(super.components(),
                    DataComponentMap.builder().set(ModComponents.CHARGES.get(), defaultCharges).build());
        }
        return defaults;
    }

    public static int charges(ItemStack s) {
        return s.getOrDefault(ModComponents.CHARGES.get(), 0);
    }

    public int maxCharges() { return defaultCharges; }

    /** Consumes one charge. Returns false if empty. */
    protected boolean consume(Player p, ItemStack s) {
        if (p.getAbilities().instabuild) return true;
        int c = charges(s);
        if (c <= 0) return false;
        s.set(ModComponents.CHARGES.get(), c - 1);
        return true;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return defaultCharges > 1;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13f * Math.min(1f, (float) charges(stack) / defaultCharges));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x6FD3FF;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal(description).withStyle(ChatFormatting.GRAY));
        if (defaultCharges > 0) tip.add(Component.literal("Charges: " + charges(stack)).withStyle(ChatFormatting.DARK_AQUA));
    }
}
