package com.ditsjohann.warfront.network;

import com.ditsjohann.warfront.WarfrontConfig;
import com.ditsjohann.warfront.combat.DownedState;
import com.ditsjohann.warfront.combat.Faction;
import com.ditsjohann.warfront.combat.Spotting;
import com.ditsjohann.warfront.entity.VehicleEntity;
import com.ditsjohann.warfront.match.BattlefieldGenerator;
import com.ditsjohann.warfront.match.ConquestMatch;
import com.ditsjohann.warfront.match.Deployment;
import com.ditsjohann.warfront.match.MatchManager;
import com.ditsjohann.warfront.network.packets.SimplePackets;
import com.ditsjohann.warfront.registry.ModBlocks;
import com.ditsjohann.warfront.registry.ModComponents;
import com.ditsjohann.warfront.registry.ModSounds;
import com.ditsjohann.warfront.weapon.GunItem;
import com.ditsjohann.warfront.weapon.GunLogic;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Validates and applies client requests on the server thread. */
public final class ServerPacketHandler {
    static void shoot(SimplePackets.Shoot m, ServerPlayer p) {
        if (!Float.isFinite(m.yaw()) || !Float.isFinite(m.pitch())) return;
        GunLogic.playerShoot(p, m.yaw(), m.pitch());
    }

    static void action(SimplePackets.Action m, ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        switch (m.action()) {
            case SimplePackets.Action.RELOAD -> {
                ItemStack s = p.getMainHandItem();
                if (s.getItem() instanceof GunItem g && !DownedState.isDowned(p)) GunLogic.startReload(p, s, g.type, level.getGameTime());
            }
            case SimplePackets.Action.FIRE_MODE -> {
                ItemStack s = p.getMainHandItem();
                if (s.getItem() instanceof GunItem g && g.type.stats.modeOrder.length > 1) {
                    s.set(ModComponents.FIRE_MODE.get(), (s.getOrDefault(ModComponents.FIRE_MODE.get(), 0) + 1) % g.type.stats.modeOrder.length);
                    level.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.get("fire_mode"), SoundSource.PLAYERS, 0.5f, 1f);
                }
            }
            case SimplePackets.Action.SPOT -> {
                Entity spotted = Spotting.spot(p, 180, 5);
                ConquestMatch match = MatchManager.active(level);
                if (spotted == null && match != null) match.orderSquad(level, p);
            }
            case SimplePackets.Action.BLEED_OUT -> DownedState.bleedOut(p);
            case SimplePackets.Action.OPEN_DEPLOY -> {
                ConquestMatch match = MatchManager.active(level);
                if (match != null && match.participant(p) != null) {
                    ConquestMatch.Participant pt = match.participant(p);
                    // re-deploying while already deployed is only allowed at HQ (loadout change)
                    if (!pt.deployed || Deployment.nearHq(match, p, pt.faction)) WarfrontNet.toPlayer(p, new SimplePackets.OpenDeploy(0));
                    else WarfrontNet.toPlayer(p, new SimplePackets.Score(0, "RETURN TO HQ TO CHANGE LOADOUT"));
                }
            }
            case SimplePackets.Action.AIM_ON -> com.ditsjohann.warfront.combat.ScopeGlint.aim(p, true);
            case SimplePackets.Action.AIM_OFF -> com.ditsjohann.warfront.combat.ScopeGlint.aim(p, false);
            default -> {}
        }
    }

    static void squadCommand(SimplePackets.SquadCommand m, ServerPlayer p) {
        ConquestMatch match = MatchManager.active(p.serverLevel());
        if (match != null) match.command(p.serverLevel(), p, m.command(), m.arg());
    }

    static void deploy(SimplePackets.Deploy m, ServerPlayer p) {
        Deployment.deploy(p, m);
    }

    static void warTable(SimplePackets.WarTable m, ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        if (p.distanceToSqr(m.pos().getCenter()) > 64) return;
        BlockState state = level.getBlockState(m.pos());
        if (!state.is(ModBlocks.WAR_TABLE.get())) return;
        MatchManager mm = MatchManager.get(level);
        ConquestMatch current = MatchManager.active(level);
        Faction f = Faction.byId(m.faction());
        switch (m.command()) {
            case SimplePackets.WarTable.START -> {
                if (current != null && current.state != ConquestMatch.ENDED) {
                    WarfrontNet.toPlayer(p, new SimplePackets.Score(0, "A BATTLE IS ALREADY UNDERWAY"));
                    return;
                }
                Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
                int soldiers = Math.max(0, Math.min(WarfrontConfig.SOLDIERS_PER_TEAM.get(), m.soldiers()));
                int tickets = Math.max(20, Math.min(2000, m.tickets()));
                ConquestMatch match = BattlefieldGenerator.generate(level, m.pos(), facing, m.size(), f, soldiers, tickets);
                mm.set(level, match);
                match.join(p, f);
                match.broadcast(level, new SimplePackets.Announce("BATTLEFIELD DEPLOYED", f.displayName + " vs " + f.enemy().displayName, 0));
                WarfrontNet.toPlayer(p, new SimplePackets.OpenDeploy(0));
            }
            case SimplePackets.WarTable.JOIN -> {
                if (current == null || current.state == ConquestMatch.ENDED) return;
                current.join(p, f);
                WarfrontNet.toPlayer(p, new SimplePackets.OpenDeploy(0));
                MatchManager.get(level).setDirty();
            }
            case SimplePackets.WarTable.END -> {
                if (current == null) return;
                if (current.state == ConquestMatch.ENDED) mm.clear(level);
                else current.end(level, null);
            }
            case SimplePackets.WarTable.LEAVE -> {
                if (current != null) current.leave(p);
            }
            default -> {}
        }
    }

    static void vehicle(SimplePackets.VehicleInput m, ServerPlayer p) {
        if (p.getVehicle() instanceof VehicleEntity v) v.handleInput(p, m.action());
    }

    private ServerPacketHandler() {}
}
