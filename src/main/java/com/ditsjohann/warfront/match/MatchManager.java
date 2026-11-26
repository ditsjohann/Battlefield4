package com.ditsjohann.warfront.match;

import com.ditsjohann.warfront.Warfront;
import com.ditsjohann.warfront.combat.Faction;
import com.ditsjohann.warfront.network.WarfrontNet;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/** Persists the (single) Conquest match of a level and drives its tick. */
@Mod.EventBusSubscriber(modid = Warfront.MODID)
public final class MatchManager extends SavedData {
    private static final String NAME = "warfront_conquest";
    @Nullable private ConquestMatch match;

    public static MatchManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(MatchManager::new, MatchManager::load, null), NAME);
    }

    @Nullable
    public static ConquestMatch active(ServerLevel level) {
        return get(level).match;
    }

    @Nullable
    public static Faction factionOf(ServerLevel level, Player p) {
        ConquestMatch m = active(level);
        if (m == null) return null;
        ConquestMatch.Participant pt = m.participant(p);
        return pt == null ? null : pt.faction;
    }

    public void set(ServerLevel level, ConquestMatch m) {
        if (match != null) match.cleanup(level);
        match = m;
        setDirty();
        sync(level, m);
    }

    public void clear(ServerLevel level) {
        if (match != null) match.cleanup(level);
        match = null;
        setDirty();
        for (ServerPlayer p : level.players()) WarfrontNet.toPlayer(p, MatchSnapshot.NONE);
    }

    static void sync(ServerLevel level, ConquestMatch m) {
        MatchSnapshot snap = m.snapshot();
        for (ServerPlayer p : level.players()) WarfrontNet.toPlayer(p, snap);
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent.Post event) {
        if (!(event.level instanceof ServerLevel level)) return;
        MatchManager mm = level.getDataStorage().get(new SavedData.Factory<>(MatchManager::new, MatchManager::load, null), NAME);
        if (mm == null || mm.match == null) return;
        mm.match.tick(level);
        if (level.getGameTime() % 100 == 0) mm.setDirty();
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            ConquestMatch m = active(p.serverLevel());
            WarfrontNet.toPlayer(p, m == null ? MatchSnapshot.NONE : m.snapshot());
        }
    }

    @SubscribeEvent
    public static void onChangeDim(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            ConquestMatch m = active(p.serverLevel());
            WarfrontNet.toPlayer(p, m == null ? MatchSnapshot.NONE : m.snapshot());
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (match != null) tag.put("match", match.save());
        return tag;
    }

    private static MatchManager load(CompoundTag tag, HolderLookup.Provider registries) {
        MatchManager mm = new MatchManager();
        if (tag.contains("match")) {
            try {
                mm.match = ConquestMatch.load(tag.getCompound("match"));
            } catch (Exception e) {
                Warfront.LOG.error("Could not load Conquest match, discarding it", e);
            }
        }
        return mm;
    }
}
