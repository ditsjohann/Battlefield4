package com.ditsjohann.warfront.combat;

import com.ditsjohann.warfront.Warfront;
import com.ditsjohann.warfront.network.WarfrontNet;
import com.ditsjohann.warfront.network.packets.SimplePackets;
import com.ditsjohann.warfront.registry.ModDamageTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Players in a match go down instead of dying outright. While downed they crawl, can't fight, and
 * bleed out after 20 seconds unless a teammate revives them (defibrillator or AI medic).
 */
@Mod.EventBusSubscriber(modid = Warfront.MODID)
public final class DownedState {
    public static final int BLEED_TICKS = 400;
    private static final Map<UUID, Integer> DOWNED = new ConcurrentHashMap<>();

    public static boolean isDowned(Player p) {
        return DOWNED.containsKey(p.getUUID());
    }

    public static void down(ServerPlayer p) {
        DOWNED.put(p.getUUID(), BLEED_TICKS);
        p.setForcedPose(Pose.SWIMMING);
        p.stopUsingItem();
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, BLEED_TICKS, 3, false, false));
        WarfrontNet.toPlayer(p, new SimplePackets.Downed(true, BLEED_TICKS));
    }

    public static void clear(ServerPlayer p, boolean revived) {
        if (DOWNED.remove(p.getUUID()) == null) return;
        p.setForcedPose(null);
        p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        if (revived) p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 4, false, false));
        WarfrontNet.toPlayer(p, new SimplePackets.Downed(false, 0));
    }

    /** Give up and redeploy (or the timer ran out). */
    public static void bleedOut(ServerPlayer p) {
        if (!isDowned(p)) return;
        DOWNED.remove(p.getUUID());
        p.setForcedPose(null);
        p.hurt(ModDamageTypes.source(p.level(), ModDamageTypes.BLEED_OUT, null, null), Float.MAX_VALUE);
        WarfrontNet.toPlayer(p, new SimplePackets.Downed(false, 0));
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent.Post event) {
        if (!(event.player instanceof ServerPlayer p)) return;
        Integer left = DOWNED.get(p.getUUID());
        if (left == null) return;
        if (left <= 0 || !p.isAlive()) {
            bleedOut(p);
            return;
        }
        DOWNED.put(p.getUUID(), left - 1);
        if (p.getForcedPose() != Pose.SWIMMING) p.setForcedPose(Pose.SWIMMING);
        if (left % 20 == 0) WarfrontNet.toPlayer(p, new SimplePackets.Downed(true, left));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && isDowned(p)) bleedOut(p);
    }

    private DownedState() {}
}
