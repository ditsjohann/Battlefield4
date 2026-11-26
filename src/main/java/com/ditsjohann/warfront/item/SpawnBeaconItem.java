package com.ditsjohann.warfront.item;

import com.ditsjohann.warfront.combat.Faction;
import com.ditsjohann.warfront.combat.Teams;
import com.ditsjohann.warfront.entity.SpawnBeaconEntity;
import com.ditsjohann.warfront.network.WarfrontNet;
import com.ditsjohann.warfront.network.packets.SimplePackets;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

/** Recon radio beacon: place on the ground to create a forward spawn point for your team. */
public class SpawnBeaconItem extends GadgetItem {
    public SpawnBeaconItem() {
        super(1, "Place on the ground: your team can deploy on it until it is destroyed.");
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (ctx.getClickedFace() != Direction.UP || !(ctx.getPlayer() instanceof ServerPlayer p)) return ctx.getClickedFace() == Direction.UP ? InteractionResult.SUCCESS : InteractionResult.PASS;
        Faction f = Teams.of(p);
        if (f == null) {
            WarfrontNet.toPlayer(p, new SimplePackets.Score(0, "JOIN A CONQUEST MATCH TO USE BEACONS"));
            return InteractionResult.FAIL;
        }
        if (consume(p, ctx.getItemInHand())) {
            Vec3 at = ctx.getClickLocation();
            SpawnBeaconEntity.place((ServerLevel) ctx.getLevel(), p, at, f);
            WarfrontNet.toPlayer(p, new SimplePackets.Score(0, "SPAWN BEACON DEPLOYED"));
        }
        return InteractionResult.SUCCESS;
    }
}
