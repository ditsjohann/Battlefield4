package com.ditsjohann.warfront.combat;

import com.ditsjohann.warfront.network.WarfrontNet;
import com.ditsjohann.warfront.network.packets.SimplePackets;
import com.ditsjohann.warfront.weapon.GunItem;
import com.ditsjohann.warfront.weapon.GunLogic;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Scope glint: a player aiming through a 4x or stronger optic flashes a glint visible to anyone
 * the scope points at. It gives away snipers, so countersniping is possible.
 */
public final class ScopeGlint {
    public static boolean hasHighZoom(ItemStack s) {
        return s.getItem() instanceof GunItem g && GunLogic.loadout(s, g.type).optic().zoom >= 4f;
    }

    public static void aim(ServerPlayer p, boolean aiming) {
        boolean glint = aiming && hasHighZoom(p.getMainHandItem());
        SimplePackets.Glint pkt = new SimplePackets.Glint(p.getId(), glint);
        for (ServerPlayer other : p.serverLevel().players()) {
            if (other != p && other.distanceToSqr(p) < 320 * 320) WarfrontNet.toPlayer(other, pkt);
        }
    }

    private ScopeGlint() {}
}
