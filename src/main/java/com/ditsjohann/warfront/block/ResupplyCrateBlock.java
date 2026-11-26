package com.ditsjohann.warfront.block;

import com.ditsjohann.warfront.item.GadgetItem;
import com.ditsjohann.warfront.network.WarfrontNet;
import com.ditsjohann.warfront.network.packets.SimplePackets;
import com.ditsjohann.warfront.registry.ModComponents;
import com.ditsjohann.warfront.registry.ModSounds;
import com.ditsjohann.warfront.weapon.GunItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Resupply cabinet: restocks spare ammo and gadget charges, with a cooldown per soldier. */
public class ResupplyCrateBlock extends Block {
    private static final Map<UUID, Long> COOLDOWN = new ConcurrentHashMap<>();
    private static final int COOLDOWN_TICKS = 400;

    public ResupplyCrateBlock(BlockBehaviour.Properties props) {
        super(props);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        long now = level.getGameTime();
        long ready = COOLDOWN.getOrDefault(sp.getUUID(), 0L);
        if (ready > now) {
            WarfrontNet.toPlayer(sp, new SimplePackets.Score(0, "RESUPPLY IN " + ((ready - now) / 20 + 1) + "s"));
            return InteractionResult.CONSUME;
        }
        int restocked = 0;
        for (int i = 0; i < sp.getInventory().getContainerSize(); i++) {
            ItemStack s = sp.getInventory().getItem(i);
            if (s.getItem() instanceof GunItem g) {
                s.set(ModComponents.RESERVE.get(), g.type.stats.reserve);
                restocked++;
            } else if (s.getItem() instanceof GadgetItem gi && gi.maxCharges() > 0) {
                s.set(ModComponents.CHARGES.get(), gi.maxCharges());
                restocked++;
            }
        }
        COOLDOWN.put(sp.getUUID(), now + COOLDOWN_TICKS);
        level.playSound(null, pos, ModSounds.get("resupply"), SoundSource.BLOCKS, 1f, 1f);
        WarfrontNet.toPlayer(sp, new SimplePackets.Score(0, restocked > 0 ? "RESUPPLIED" : "NOTHING TO RESUPPLY"));
        return InteractionResult.CONSUME;
    }
}
