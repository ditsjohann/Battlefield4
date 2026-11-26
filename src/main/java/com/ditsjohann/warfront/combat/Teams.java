package com.ditsjohann.warfront.combat;

import com.ditsjohann.warfront.WarfrontConfig;
import com.ditsjohann.warfront.match.MatchManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/** Resolves which side an entity fights for, on either logical side. */
public final class Teams {
    /** Installed by the client so client code can resolve player factions from synced match state. */
    public static Function<Player, Faction> clientPlayerLookup = p -> null;

    @Nullable
    public static Faction of(@Nullable Entity e) {
        if (e == null) return null;
        if (e instanceof FactionMember m) return m.getFaction();
        if (e instanceof Player p) {
            if (p.level() instanceof ServerLevel sl) return MatchManager.factionOf(sl, p);
            return clientPlayerLookup.apply(p);
        }
        Entity v = e.getVehicle();
        if (v instanceof FactionMember m) return m.getFaction();
        return null;
    }

    public static boolean allies(@Nullable Entity a, @Nullable Entity b) {
        if (a == null || b == null) return false;
        if (a == b) return true;
        Faction fa = of(a), fb = of(b);
        return fa != null && fa == fb;
    }

    public static boolean enemies(@Nullable Entity a, @Nullable Entity b) {
        if (a == null || b == null || a == b) return false;
        Faction fa = of(a), fb = of(b);
        return fa != null && fb != null && fa != fb;
    }

    /** Whether {@code attacker} may damage {@code target} under the friendly-fire rule. */
    public static boolean canHurt(@Nullable Entity attacker, Entity target) {
        if (attacker == null || attacker == target) return attacker == null;
        if (!allies(attacker, target)) return true;
        return !(target.level() instanceof ServerLevel) || WarfrontConfig.FRIENDLY_FIRE.get();
    }

    private Teams() {}
}
