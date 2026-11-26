package com.ditsjohann.warfront.progression;

import com.ditsjohann.warfront.Warfront;
import com.ditsjohann.warfront.network.WarfrontNet;
import com.ditsjohann.warfront.network.packets.SimplePackets;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Career stats stored in the player's persistent data (survives death via {@link PlayerEvent.Clone}).
 */
@Mod.EventBusSubscriber(modid = Warfront.MODID)
public final class PlayerProgress {
    private static final String KEY = "warfront_progress";

    public static CompoundTag data(Player p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(KEY)) root.put(KEY, new CompoundTag());
        return root.getCompound(KEY);
    }

    public static int xp(Player p) { return data(p).getInt("xp"); }
    public static int rank(Player p) { return Ranks.rankFor(xp(p)); }

    public static int weaponKills(Player p, com.ditsjohann.warfront.weapon.GunType gun) {
        return data(p).getInt("wk_" + gun.id);
    }

    public static java.util.Map<String, Integer> allWeaponKills(Player p) {
        java.util.Map<String, Integer> out = new java.util.HashMap<>();
        for (com.ditsjohann.warfront.weapon.GunType g : com.ditsjohann.warfront.weapon.GunType.values()) {
            int k = weaponKills(p, g);
            if (k > 0) out.put(g.id, k);
        }
        return out;
    }

    public static void add(Player p, String stat, int amount) {
        CompoundTag d = data(p);
        d.putInt(stat, d.getInt(stat) + amount);
    }

    public static void sync(ServerPlayer p) {
        CompoundTag d = data(p);
        WarfrontNet.toPlayer(p, new SimplePackets.Progress(d.getInt("xp"), rank(p), d.getInt("kills"), d.getInt("deaths"), d.getInt("wins"), d.getInt("matches"), allWeaponKills(p)));
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        CompoundTag old = event.getOriginal().getPersistentData();
        if (old.contains(KEY)) event.getEntity().getPersistentData().put(KEY, old.getCompound(KEY).copy());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) sync(sp);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) sync(sp);
    }

    private PlayerProgress() {}
}
