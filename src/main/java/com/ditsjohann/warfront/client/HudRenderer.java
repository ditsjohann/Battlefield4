package com.ditsjohann.warfront.client;

import com.ditsjohann.warfront.client.render.FirstPersonGun;
import com.ditsjohann.warfront.combat.Faction;
import com.ditsjohann.warfront.entity.SoldierEntity;
import com.ditsjohann.warfront.entity.TankEntity;
import com.ditsjohann.warfront.item.GadgetItem;
import com.ditsjohann.warfront.match.ConquestMatch;
import com.ditsjohann.warfront.match.MatchSnapshot;
import com.ditsjohann.warfront.progression.Ranks;
import com.ditsjohann.warfront.registry.ModComponents;
import com.ditsjohann.warfront.weapon.*;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** The WARFRONT HUD, drawn as one Forge GUI layer above vanilla. */
public final class HudRenderer {
    private static final int PANEL = 0x99101418;
    private static final int PANEL_LIGHT = 0x66202830;
    private static final int ACCENT = 0xFFF2A33A;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int DIM = 0xFFB8C0C8;
    private static final int NEUTRAL = 0xFFB0B0B0;

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.options.hideGui) return;
        float pt = delta.getGameTimeDeltaPartialTick(false);
        int w = g.guiWidth(), h = g.guiHeight();
        RenderSystem.enableBlend();

        if (FirstPersonGun.scoped()) drawScope(g, w, h);
        drawVignettes(g, p, w, h);
        boolean match = ClientState.match.active();
        if (match) {
            drawConquestBar(g, mc.font, w);
            drawCaptureStatus(g, mc.font, p, w);
            drawMinimap(g, mc, p, w, h, pt);
        }
        if (GunController.drivingTank()) drawTankHud(g, mc.font, p, w, h);
        else drawWeaponHud(g, mc.font, p, w, h);
        drawKillFeed(g, mc.font, w);
        drawScores(g, mc.font, w, h);
        drawAnnouncement(g, mc.font, w, h);
        if (ClientState.downed) drawDowned(g, mc.font, w, h);
        if (match && ClientState.inMatch() && KeyBinds.SCOREBOARD.isDown()) drawScoreboard(g, mc.font, w, h);
        if (match && !ClientState.inMatch() && ClientState.match.state() != ConquestMatch.ENDED) {
            String s = "A battle is raging nearby. Use a War Table to enlist.";
            g.drawString(mc.font, s, w / 2 - mc.font.width(s) / 2, h - 70, 0xFFE0E0E0, true);
        }
        RenderSystem.disableBlend();
    }

    // ------------------------------------------------------------------ weapon + crosshair
    private static void drawWeaponHud(GuiGraphics g, Font font, LocalPlayer p, int w, int h) {
        ItemStack stack = p.getMainHandItem();
        int cx = w / 2, cy = h / 2;
        boolean gun = stack.getItem() instanceof GunItem;
        // panel bottom-right
        int pw = 132, ph = 46, px = w - pw - 8, py = h - ph - 8;
        if (gun) {
            GunItem gi = (GunItem) stack.getItem();
            GunType type = gi.type;
            EffectiveStats eff = EffectiveStats.of(type, GunLogic.loadout(stack, type));
            panel(g, px, py, pw, ph);
            g.drawString(font, type.displayName.toUpperCase(), px + 6, py + 5, DIM, false);
            int ammo = GunController.displayAmmo(stack);
            int reserve = GunLogic.reserve(stack);
            boolean low = ammo <= type.stats.magSize / 4;
            String mag = String.valueOf(ammo);
            g.pose().pushPose();
            g.pose().translate(px + 6, py + 16, 0);
            g.pose().scale(2.2f, 2.2f, 1);
            g.drawString(font, mag, 0, 0, low ? 0xFFFF5040 : WHITE, true);
            g.pose().popPose();
            String res = p.getAbilities().instabuild ? "/ ∞" : "/ " + reserve;
            g.drawString(font, res, px + 8 + Math.round(font.width(mag) * 2.2f), py + 28, DIM, true);
            FireMode mode = GunLogic.fireMode(stack, type);
            g.drawString(font, mode.label, px + pw - 6 - font.width(mode.label), py + 5, ACCENT, false);
            // ammo pips
            int pips = Math.min(type.stats.magSize, 30);
            int filled = Math.round((float) ammo / type.stats.magSize * pips);
            for (int i = 0; i < pips; i++) {
                int x = px + pw - 6 - (pips - i) * 3;
                g.fill(x, py + 18, x + 2, py + 26, i < filled ? 0xFFE8E8E8 : 0x44FFFFFF);
            }
            Long end = stack.get(ModComponents.RELOAD_END.get());
            if (end != null) {
                int len = stack.getOrDefault(ModComponents.RELOAD_LENGTH.get(), 40);
                float t = Mth.clamp(1f - (end - p.level().getGameTime()) / (float) len, 0, 1);
                g.fill(px + 6, py + ph - 7, px + pw - 6, py + ph - 4, 0x55FFFFFF);
                g.fill(px + 6, py + ph - 7, px + 6 + Math.round((pw - 12) * t), py + ph - 4, ACCENT);
                String r = "RELOADING";
                g.drawString(font, r, cx - font.width(r) / 2, cy + 22, ACCENT, true);
            } else if (ammo == 0) {
                String r = reserve > 0 || p.getAbilities().instabuild ? "RELOAD [" + KeyBinds.RELOAD.getTranslatedKeyMessage().getString() + "]" : "NO AMMO";
                g.drawString(font, r, cx - font.width(r) / 2, cy + 22, 0xFFFF5040, true);
            }
            String att = eff.loadout().optic().label + (eff.suppressed() ? " | SUPPRESSED" : "");
            g.drawString(font, att, px + 6, py + ph - 16, 0xFF7FA8C0, false);
            if (!FirstPersonGun.scoped()) drawCrosshair(g, p, eff, cx, cy);
        } else if (stack.getItem() instanceof GadgetItem gadget) {
            panel(g, px, py + 18, pw, ph - 18);
            g.drawString(font, stack.getHoverName().getString().toUpperCase(), px + 6, py + 23, DIM, false);
            if (gadget.maxCharges() > 0) {
                String c = "x" + GadgetItem.charges(stack);
                g.drawString(font, c, px + pw - 6 - font.width(c), py + 23, WHITE, true);
            }
            int heat = stack.getOrDefault(ModComponents.HEAT.get(), -1);
            if (heat >= 0) {
                g.fill(px + 6, py + 36, px + pw - 6, py + 39, 0x55FFFFFF);
                g.fill(px + 6, py + 36, px + 6 + Math.round((pw - 12) * heat / 100f), py + 39, heat > 80 ? 0xFFFF4020 : 0xFFFFA040);
            }
        }
        // health
        if (ClientState.inMatch() || gun) {
            int hx = w - 140 - 8, hy = h - 8 - 46 - 14;
            float hp = p.getHealth() / p.getMaxHealth();
            g.fill(hx + 8, hy, hx + 140, hy + 9, PANEL);
            int col = hp > 0.5f ? 0xFFE8E8E8 : hp > 0.25f ? 0xFFF2C23A : 0xFFFF4A3A;
            g.fill(hx + 9, hy + 1, hx + 9 + Math.round(130 * hp), hy + 8, col);
            String hs = String.valueOf(Math.round(hp * 100));
            g.drawString(font, hs, hx - font.width(hs) + 4, hy + 1, col, true);
        }
        drawHitMarker(g, cx, cy);
    }

    private static void drawCrosshair(GuiGraphics g, LocalPlayer p, EffectiveStats eff, int cx, int cy) {
        float ads = GunController.ease(GunController.ads);
        if (ads > 0.6f) return;
        float spread = GunController.currentSpread(eff, p);
        Minecraft mc = Minecraft.getInstance();
        double fov = mc.options.fov().get();
        float halfH = g.guiHeight() / 2f;
        int gap = Math.max(2, Math.round((float) (Math.tan(Math.toRadians(spread)) / Math.tan(Math.toRadians(fov / 2)) * halfH)));
        int a = Math.round(255 * (1 - ads / 0.6f));
        int col = (a << 24) | 0xFFFFFF;
        int len = 5;
        g.fill(cx - gap - len, cy, cx - gap, cy + 1, col);
        g.fill(cx + gap + 1, cy, cx + gap + 1 + len, cy + 1, col);
        g.fill(cx, cy - gap - len, cx + 1, cy - gap, col);
        g.fill(cx, cy + gap + 1, cx + 1, cy + gap + 1 + len, col);
        g.fill(cx, cy, cx + 1, cy + 1, col);
    }

    private static void drawHitMarker(GuiGraphics g, int cx, int cy) {
        if (ClientState.hitMarkerTicks <= 0) return;
        float t = ClientState.hitMarkerTicks / 8f;
        int col = ClientState.hitKill ? 0xFFFF3A2A : ClientState.hitVehicle ? 0xFFFFC040 : WHITE;
        int a = Math.round(255 * Math.min(1, t * 1.5f));
        col = (a << 24) | (col & 0xFFFFFF);
        int r = ClientState.hitHead || ClientState.hitKill ? 8 : 6;
        int inner = 3;
        for (int i = inner; i < r; i++) {
            g.fill(cx - i, cy - i, cx - i + 1, cy - i + 1, col);
            g.fill(cx + i, cy - i, cx + i + 1, cy - i + 1, col);
            g.fill(cx - i, cy + i, cx - i + 1, cy + i + 1, col);
            g.fill(cx + i, cy + i, cx + i + 1, cy + i + 1, col);
        }
    }

    private static void drawScope(GuiGraphics g, int w, int h) {
        int cx = w / 2, cy = h / 2;
        int r = Math.min(w, h) / 2 - 6;
        // black outside the circle, row by row
        for (int y = 0; y < h; y++) {
            int dy = y - cy;
            if (Math.abs(dy) >= r) {
                g.fill(0, y, w, y + 1, 0xFF000000);
                continue;
            }
            int dx = (int) Math.sqrt((double) r * r - (double) dy * dy);
            g.fill(0, y, cx - dx, y + 1, 0xFF000000);
            g.fill(cx + dx, y, w, y + 1, 0xFF000000);
        }
        int line = 0xFF101010;
        g.fill(cx - r, cy, cx - 12, cy + 1, line);
        g.fill(cx + 12, cy, cx + r, cy + 1, line);
        g.fill(cx, cy - r, cx + 1, cy - 12, line);
        g.fill(cx, cy + 12, cx + 1, cy + r, line);
        g.fill(cx - r, cy - 1, cx - r / 2, cy + 2, line);
        g.fill(cx + r / 2, cy - 1, cx + r, cy + 2, line);
        g.fill(cx - 1, cy + r / 2, cx + 2, cy + r, line);
        for (int i = 1; i <= 4; i++) {
            int d = i * r / 10;
            g.fill(cx - 3, cy + d, cx + 4, cy + d + 1, line);
            g.fill(cx - d, cy - 2, cx - d + 1, cy + 3, line);
            g.fill(cx + d, cy - 2, cx + d + 1, cy + 3, line);
        }
        g.fill(cx, cy, cx + 1, cy + 1, 0xFFFF2020);
        // lens tint ring
        for (int i = 0; i < 8; i++) {
            int rr = r - i;
            int a = (8 - i) * 14;
            ring(g, cx, cy, rr, (a << 24));
        }
    }

    private static void ring(GuiGraphics g, int cx, int cy, int r, int col) {
        for (int deg = 0; deg < 360; deg += 2) {
            double a = Math.toRadians(deg);
            int x = cx + (int) (Math.cos(a) * r), y = cy + (int) (Math.sin(a) * r);
            g.fill(x, y, x + 1, y + 1, col);
        }
    }

    private static void drawVignettes(GuiGraphics g, LocalPlayer p, int w, int h) {
        float sup = ClientState.suppression;
        float hp = p.getHealth() / p.getMaxHealth();
        float damage = hp < 0.5f ? (0.5f - hp) * 1.6f : 0;
        if (sup > 0.02f) edge(g, w, h, Math.round(170 * Math.min(1, sup)), 0x000000);
        if (damage > 0.02f) edge(g, w, h, Math.round(140 * Math.min(1, damage)), 0x8A0000);
        if (p.hasEffect(MobEffects.GLOWING) && ClientState.inMatch()) {
            String s = "YOU HAVE BEEN SPOTTED";
            Font font = Minecraft.getInstance().font;
            g.drawString(font, s, w / 2 - font.width(s) / 2, h - 82, 0xFFFF6A4A, true);
        }
    }

    private static void edge(GuiGraphics g, int w, int h, int alpha, int rgb) {
        int steps = 24;
        for (int i = 0; i < steps; i++) {
            int a = alpha * (steps - i) / steps / 3;
            int col = (a << 24) | rgb;
            int t = i * 3;
            g.fill(0, t, w, t + 3, col);
            g.fill(0, h - t - 3, w, h - t, col);
            g.fill(t, 0, t + 3, h, col);
            g.fill(w - t - 3, 0, w - t, h, col);
        }
    }

    // ------------------------------------------------------------------ conquest
    private static int factionColor(int f) {
        return f == 0 ? 0xFF2F6FD8 : f == 1 ? 0xFFD83A2E : NEUTRAL;
    }

    private static void drawConquestBar(GuiGraphics g, Font font, int w) {
        MatchSnapshot m = ClientState.match;
        Faction mine = ClientState.myFaction();
        int my = mine == null ? 0 : mine.ordinal();
        int en = 1 - my;
        int n = m.flags().size();
        int box = 16, gap = 4;
        int total = n * box + (n - 1) * gap;
        int x0 = w / 2 - total / 2, y0 = 6;
        // tickets
        int myT = my == 0 ? m.ticketsA() : m.ticketsB(), enT = en == 0 ? m.ticketsA() : m.ticketsB();
        int bw = 90;
        int lx = x0 - 10 - bw, rx = x0 + total + 10;
        panel(g, lx - 4, y0 - 3, bw + 8, box + 6);
        panel(g, rx - 4, y0 - 3, bw + 8, box + 6);
        float fm = m.maxTickets() == 0 ? 0 : (float) myT / m.maxTickets(), fe = m.maxTickets() == 0 ? 0 : (float) enT / m.maxTickets();
        g.fill(lx, y0 + box - 4, lx + bw, y0 + box - 1, 0x44FFFFFF);
        g.fill(lx + bw - Math.round(bw * fm), y0 + box - 4, lx + bw, y0 + box - 1, factionColor(my));
        g.fill(rx, y0 + box - 4, rx + bw, y0 + box - 1, 0x44FFFFFF);
        g.fill(rx, y0 + box - 4, rx + Math.round(bw * fe), y0 + box - 1, factionColor(en));
        String ms = String.valueOf(myT), es = String.valueOf(enT);
        g.drawString(font, ms, lx + bw - font.width(ms), y0, WHITE, true);
        g.drawString(font, es, rx, y0, WHITE, true);
        String myTag = Faction.byId(my).tag, enTag = Faction.byId(en).tag;
        g.drawString(font, myTag, lx, y0, factionColor(my), true);
        g.drawString(font, enTag, rx + bw - font.width(enTag), y0, factionColor(en), true);
        // flags
        for (int i = 0; i < n; i++) {
            MatchSnapshot.Flag f = m.flags().get(i);
            int x = x0 + i * (box + gap);
            drawFlagIcon(g, font, x, y0, box, f, my);
        }
        // state line
        String status = null;
        if (m.state() == ConquestMatch.WARMUP) status = "BATTLE BEGINS IN " + (m.timer() / 20 + 1);
        else if (m.state() == ConquestMatch.ENDED) status = m.winner() < 0 ? "MATCH OVER" : Faction.byId(m.winner()).displayName.toUpperCase() + " WINS";
        else if (m.uplinkFaction() >= 0) status = m.uplinkFaction() == my ? "RADAR UPLINK: ONLINE" : "ENEMY HAS RADAR UPLINK";
        else if (m.towerCollapsed()) status = "COMMS TOWER DESTROYED";
        if (status != null) g.drawString(font, status, w / 2 - font.width(status) / 2, y0 + box + 5,
                m.uplinkFaction() == my ? 0xFF7FD0FF : m.uplinkFaction() >= 0 ? 0xFFFF8A6A : DIM, true);
    }

    private static void drawFlagIcon(GuiGraphics g, Font font, int x, int y, int box, MatchSnapshot.Flag f, int my) {
        int owner = f.owner();
        // relative colours: friendly blue, enemy red
        int base = owner < 0 ? 0xFF5A5A5A : owner == my ? 0xFF2F6FD8 : 0xFFD83A2E;
        g.fill(x - 1, y - 1, x + box + 1, y + box + 1, 0xFF0A0A0A);
        g.fill(x, y, x + box, y + box, base);
        // control fill from bottom toward the side currently leading
        float c = f.control();
        float myControl = my == 0 ? c : -c;
        int fill = Math.round(Math.abs(myControl) * box);
        int fc = myControl > 0 ? 0xAA4F8FFF : 0xAAFF5A4E;
        if (owner < 0 || (owner == my) != (myControl > 0)) g.fill(x, y + box - fill, x + box, y + box, fc);
        if (f.contested()) {
            long t = ClientState.now();
            if (t / 6 % 2 == 0) g.fill(x, y, x + box, y + 2, 0xFFFFFFFF);
        }
        String l = String.valueOf(f.letter());
        g.drawString(font, l, x + box / 2 - font.width(l) / 2 + 1, y + 4, WHITE, true);
    }

    private static void drawCaptureStatus(GuiGraphics g, Font font, LocalPlayer p, int w) {
        MatchSnapshot m = ClientState.match;
        Faction mine = ClientState.myFaction();
        if (mine == null) return;
        for (MatchSnapshot.Flag f : m.flags()) {
            double dx = p.getX() - (f.pos().getX() + 0.5), dz = p.getZ() - (f.pos().getZ() + 0.5);
            if (dx * dx + dz * dz > f.radius() * f.radius() || Math.abs(p.getY() - f.pos().getY()) > 8) continue;
            int my = mine.ordinal();
            float mc = my == 0 ? f.control() : -f.control();
            int friends = my == 0 ? f.countA() : f.countB(), foes = my == 0 ? f.countB() : f.countA();
            String label;
            if (friends > foes && mc < 1) label = mc < 0 ? "NEUTRALIZING " + f.letter() : "CAPTURING " + f.letter();
            else if (foes > friends) label = "LOSING " + f.letter();
            else if (friends > 0 && foes > 0) label = "CONTESTED";
            else label = "FLAG " + f.letter() + (f.owner() == my ? " SECURED" : "");
            int bw = 160, x = w / 2 - bw / 2, y = 44;
            panel(g, x, y, bw, 22);
            g.drawString(font, label, w / 2 - font.width(label) / 2, y + 3, WHITE, true);
            g.fill(x + 6, y + 14, x + bw - 6, y + 18, 0x55FFFFFF);
            int mid = w / 2;
            int half = (bw - 12) / 2;
            int fillPx = Math.round(Math.abs(mc) * half);
            if (mc > 0) g.fill(mid, y + 14, mid + fillPx, y + 18, 0xFF4F8FFF);
            else g.fill(mid - fillPx, y + 14, mid, y + 18, 0xFFFF5A4E);
            String counts = friends + " vs " + foes;
            g.drawString(font, counts, x + bw + 4, y + 7, DIM, true);
            return;
        }
    }

    private static void drawMinimap(GuiGraphics g, Minecraft mc, LocalPlayer p, int w, int h, float pt) {
        int r = 46;
        int cx = 8 + r + 4, cy = h - 8 - r - 4;
        double scale = 1.4; // blocks per pixel
        // backdrop disc
        for (int y = -r; y <= r; y++) {
            int dx = (int) Math.sqrt((double) r * r - (double) y * y);
            g.fill(cx - dx, cy + y, cx + dx, cy + y + 1, 0xA0101418);
        }
        ring(g, cx, cy, r, 0xFF5A6A78);
        ring(g, cx, cy, r / 2, 0x335A6A78);
        float yaw = p.getViewYRot(pt);
        double rad = Math.toRadians(yaw);
        double cos = Math.cos(rad), sin = Math.sin(rad);
        Vec3 me = p.getPosition(pt);
        Faction mine = ClientState.myFaction();
        int my = mine == null ? 0 : mine.ordinal();
        MatchSnapshot m = ClientState.match;
        // flags
        for (MatchSnapshot.Flag f : m.flags()) {
            int[] s = project(f.pos().getX() + 0.5 - me.x, f.pos().getZ() + 0.5 - me.z, cos, sin, scale, r - 6);
            int col = f.owner() < 0 ? NEUTRAL : f.owner() == my ? 0xFF4F8FFF : 0xFFFF5A4E;
            g.fill(cx + s[0] - 5, cy + s[1] - 5, cx + s[0] + 5, cy + s[1] + 5, 0xFF0A0A0A);
            g.fill(cx + s[0] - 4, cy + s[1] - 4, cx + s[0] + 4, cy + s[1] + 4, col);
            String l = String.valueOf(f.letter());
            g.drawString(mc.font, l, cx + s[0] - mc.font.width(l) / 2 + 1, cy + s[1] - 3, WHITE, false);
        }
        for (int i = 0; i < 2; i++) {
            var hq = i == 0 ? m.hqA() : m.hqB();
            int[] s = project(hq.getX() + 0.5 - me.x, hq.getZ() + 0.5 - me.z, cos, sin, scale, r - 4);
            int col = i == my ? 0xFF4F8FFF : 0xFFFF5A4E;
            g.fill(cx + s[0] - 3, cy + s[1] - 3, cx + s[0] + 3, cy + s[1] + 3, col);
            g.fill(cx + s[0] - 1, cy + s[1] - 1, cx + s[0] + 1, cy + s[1] + 1, 0xFF0A0A0A);
        }
        // units
        if (mc.level != null) {
            long now = ClientState.now();
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e == p || !e.isAlive()) continue;
                Faction f = com.ditsjohann.warfront.combat.Teams.of(e);
                boolean vehicle = e instanceof TankEntity;
                if (f == null || !(e instanceof SoldierEntity || e instanceof Player || vehicle)) continue;
                if (e.getVehicle() instanceof TankEntity) continue;
                boolean friendly = mine != null && f == mine;
                boolean visible = friendly || e.isCurrentlyGlowing() || ClientState.REVEALED.containsKey(e.getId());
                if (!visible) continue;
                double dx = e.getX() - me.x, dz = e.getZ() - me.z;
                if (dx * dx + dz * dz > (r * scale) * (r * scale)) continue;
                int[] s = project(dx, dz, cos, sin, scale, r - 2);
                int col = friendly ? 0xFF6FB0FF : 0xFFFF4A3A;
                if (friendly && e instanceof Player) col = 0xFF7AFF7A;
                int sz = vehicle ? 3 : 1;
                g.fill(cx + s[0] - sz, cy + s[1] - sz, cx + s[0] + sz + 1, cy + s[1] + sz + 1, col);
            }
            mc.level.getGameTime();
        }
        // me: arrow pointing up
        g.fill(cx - 1, cy - 4, cx + 2, cy + 3, WHITE);
        g.fill(cx - 3, cy + 1, cx + 4, cy + 3, WHITE);
        // compass letters
        String[] dirs = {"S", "W", "N", "E"};
        for (int i = 0; i < 4; i++) {
            double a = Math.toRadians(i * 90);
            int[] s = project(Math.sin(-a) * 1000, Math.cos(a) * 1000, cos, sin, 1, r + 6);
            g.drawString(mc.font, dirs[i], cx + s[0] - 2, cy + s[1] - 4, i == 2 ? ACCENT : DIM, true);
        }
    }

    /** World offset -> minimap pixels, rotated so the player's facing is up, clamped to the rim. */
    private static int[] project(double dx, double dz, double cos, double sin, double scale, int clamp) {
        double rx = -(dx * cos + dz * sin);
        double ry = -(-dx * sin + dz * cos);
        rx /= scale;
        ry /= scale;
        double len = Math.sqrt(rx * rx + ry * ry);
        if (len > clamp) {
            rx = rx / len * clamp;
            ry = ry / len * clamp;
        }
        return new int[]{(int) Math.round(-rx), (int) Math.round(ry)};
    }

    // ------------------------------------------------------------------ feed, scores, announcements
    private static void drawKillFeed(GuiGraphics g, Font font, int w) {
        int y = ClientState.match.active() ? 36 : 8;
        Faction mine = ClientState.myFaction();
        for (ClientState.FeedLine line : ClientState.FEED) {
            var f = line.feed();
            String weapon = "[" + f.weapon() + (f.headshot() ? " ✛" : "") + "]";
            int kw = font.width(f.killer()), ww = font.width(weapon), vw = font.width(f.victim());
            int total = (kw > 0 ? kw + 6 : 0) + ww + 6 + vw + 10;
            int x = w - total - 8;
            panel(g, x, y, total, 12);
            int tx = x + 5;
            if (kw > 0) {
                g.drawString(font, f.killer(), tx, y + 2, feedColor(f.killerFaction(), mine), true);
                tx += kw + 6;
            }
            g.drawString(font, weapon, tx, y + 2, f.headshot() ? 0xFFFFD060 : DIM, true);
            tx += ww + 6;
            g.drawString(font, f.victim(), tx, y + 2, feedColor(f.victimFaction(), mine), true);
            y += 14;
        }
    }

    private static int feedColor(int faction, Faction mine) {
        if (faction < 0) return 0xFFE0E0E0;
        if (mine == null) return faction == 0 ? 0xFF6FA4FF : 0xFFFF6A5A;
        return faction == mine.ordinal() ? 0xFF6FA4FF : 0xFFFF6A5A;
    }

    private static void drawScores(GuiGraphics g, Font font, int w, int h) {
        int y = h / 2 + 34;
        long now = ClientState.now();
        List<ClientState.ScorePopup> list = new ArrayList<>(ClientState.SCORES);
        for (int i = list.size() - 1; i >= 0; i--) {
            ClientState.ScorePopup s = list.get(i);
            float age = (now - s.time()) / 60f;
            int a = Math.round(255 * Mth.clamp(1.4f - age * 1.4f, 0, 1));
            if (a < 8) continue;
            String txt = s.points() > 0 ? s.label() + "  +" + s.points() : s.label();
            int col = (a << 24) | (s.points() > 0 ? 0xFFE6A0 : 0xD0E0F0);
            g.drawString(font, txt, w / 2 - font.width(txt) / 2, y, col, true);
            y += 11;
        }
        if (ClientState.roundScore > 0) {
            String total = "+" + ClientState.roundScore;
            g.pose().pushPose();
            g.pose().translate(w / 2f, h / 2f + 18, 0);
            g.pose().scale(1.6f, 1.6f, 1);
            g.drawString(font, total, -font.width(total) / 2, 0, 0xFFFFE070, true);
            g.pose().popPose();
        }
    }

    private static void drawAnnouncement(GuiGraphics g, Font font, int w, int h) {
        ClientState.Announcement a = ClientState.announcement;
        if (a == null) return;
        float age = (ClientState.now() - a.time()) / 90f;
        int alpha = Math.round(255 * Mth.clamp(Math.min(age * 8, (1 - age) * 3), 0, 1));
        if (alpha < 8) return;
        int col = switch (a.style()) {
            case 1, 3 -> 0x6FB0FF;
            case 2, 4 -> 0xFF6A5A;
            default -> 0xFFFFFF;
        };
        float scale = a.style() >= 3 ? 3.5f : 2f;
        int y = a.style() >= 3 ? h / 2 - 50 : h / 4;
        int bw = Math.round(font.width(a.title()) * scale) + 40;
        g.fill(w / 2 - bw / 2, y - 6, w / 2 + bw / 2, y + Math.round(9 * scale) + 16, (alpha * 6 / 10 << 24) | 0x0A0E12);
        g.fill(w / 2 - bw / 2, y - 6, w / 2 + bw / 2, y - 5, (alpha << 24) | col);
        g.pose().pushPose();
        g.pose().translate(w / 2f, y, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, a.title(), -font.width(a.title()) / 2, 0, (alpha << 24) | col, true);
        g.pose().popPose();
        if (!a.subtitle().isEmpty()) g.drawString(font, a.subtitle(), w / 2 - font.width(a.subtitle()) / 2, y + Math.round(9 * scale) + 4, (alpha << 24) | 0xE0E0E0, true);
    }

    private static void drawDowned(GuiGraphics g, Font font, int w, int h) {
        edge(g, w, h, 220, 0x300000);
        g.fill(0, 0, w, h, 0x40300000);
        String t = "YOU ARE DOWN";
        g.pose().pushPose();
        g.pose().translate(w / 2f, h / 2f - 40, 0);
        g.pose().scale(2.5f, 2.5f, 1);
        g.drawString(font, t, -font.width(t) / 2, 0, 0xFFFF5A4A, true);
        g.pose().popPose();
        int secs = (ClientState.downedTicks + 19) / 20;
        String s = "Waiting for a medic... bleeding out in " + secs + "s";
        g.drawString(font, s, w / 2 - font.width(s) / 2, h / 2 - 10, 0xFFE0E0E0, true);
        String give = "Hold [" + Minecraft.getInstance().options.keyJump.getTranslatedKeyMessage().getString() + "] to redeploy";
        g.drawString(font, give, w / 2 - font.width(give) / 2, h / 2 + 4, DIM, true);
        int bw = 120;
        g.fill(w / 2 - bw / 2, h / 2 + 16, w / 2 + bw / 2, h / 2 + 19, 0x55FFFFFF);
        g.fill(w / 2 - bw / 2, h / 2 + 16, w / 2 - bw / 2 + Math.round(bw * GunController.giveUpProgress()), h / 2 + 19, ACCENT);
    }

    private static void drawTankHud(GuiGraphics g, Font font, LocalPlayer p, int w, int h) {
        if (!(p.getVehicle() instanceof TankEntity t)) return;
        int cx = w / 2, cy = h / 2;
        // reticle
        int col = t.reloadTicks() > 0 ? 0xAAFF8040 : 0xEE7FFF7F;
        g.fill(cx - 14, cy, cx - 4, cy + 1, col);
        g.fill(cx + 5, cy, cx + 15, cy + 1, col);
        g.fill(cx, cy + 5, cx + 1, cy + 15, col);
        g.fill(cx - 6, cy + 10, cx + 7, cy + 11, col);
        drawHitMarker(g, cx, cy);
        int pw = 150, ph = 50, px = w - pw - 8, py = h - ph - 8;
        panel(g, px, py, pw, ph);
        g.drawString(font, "MBT-9 JUGGERNAUT", px + 6, py + 5, DIM, false);
        float hp = t.health() / TankEntity.MAX_HEALTH;
        String hs = Math.round(hp * 100) + "%";
        g.pose().pushPose();
        g.pose().translate(px + 6, py + 16, 0);
        g.pose().scale(2f, 2f, 1);
        g.drawString(font, hs, 0, 0, t.disabled() ? 0xFFFF4A3A : WHITE, true);
        g.pose().popPose();
        String state = t.disabled() ? "DISABLED" : t.reloadTicks() > 0 ? "LOADING" : "CANNON READY";
        g.drawString(font, state, px + pw - 6 - font.width(state), py + 18, t.disabled() ? 0xFFFF4A3A : t.reloadTicks() > 0 ? ACCENT : 0xFF7FFF7F, true);
        float rl = 1f - t.reloadTicks() / (float) TankEntity.CANNON_RELOAD;
        g.fill(px + 6, py + ph - 9, px + pw - 6, py + ph - 6, 0x55FFFFFF);
        g.fill(px + 6, py + ph - 9, px + 6 + Math.round((pw - 12) * rl), py + ph - 6, ACCENT);
        g.drawString(font, "LMB cannon  RMB coax", px + 6, py + 32, 0xFF7FA8C0, false);
        // hull vs turret indicator
        int ix = px - 34, iy = py + ph / 2;
        g.fill(ix - 10, iy - 14, ix + 10, iy + 14, PANEL);
        float rel = Mth.wrapDegrees(t.turretYaw() - t.getYRot());
        g.fill(ix - 6, iy - 10, ix + 6, iy + 10, 0xFF59633B);
        double a = Math.toRadians(rel);
        for (int i = 0; i < 12; i++) {
            int bx = ix + (int) Math.round(-Math.sin(a) * i), by = iy - (int) Math.round(Math.cos(a) * i);
            g.fill(bx, by, bx + 1, by + 1, WHITE);
        }
    }

    private static void drawScoreboard(GuiGraphics g, Font font, int w, int h) {
        MatchSnapshot m = ClientState.match;
        int bw = Math.min(w - 40, 420), bh = 26 + 12 * Math.max(4, m.players().size() + 3);
        int x = w / 2 - bw / 2, y = 40;
        panel(g, x, y, bw, bh);
        String title = "CONQUEST  -  " + Faction.COALITION.displayName.toUpperCase() + " " + m.ticketsA() + " : " + m.ticketsB() + " " + Faction.LEGION.displayName.toUpperCase();
        g.drawString(font, title, w / 2 - font.width(title) / 2, y + 6, WHITE, true);
        for (int side = 0; side < 2; side++) {
            int colX = x + 8 + side * (bw / 2);
            int ry = y + 22;
            g.drawString(font, Faction.byId(side).displayName, colX, ry, factionColor(side), true);
            g.drawString(font, "SCORE  K  D", colX + bw / 2 - 80, ry, DIM, false);
            ry += 12;
            for (MatchSnapshot.Entry e : m.players()) {
                if (e.faction() != side) continue;
                g.drawString(font, e.name(), colX, ry, WHITE, false);
                String st = String.format("%5d %2d %2d", e.score(), e.kills(), e.deaths());
                g.drawString(font, st, colX + bw / 2 - 80, ry, WHITE, false);
                ry += 12;
            }
            int bots = side == 0 ? m.aliveA() : m.aliveB();
            g.drawString(font, bots + " AI soldiers in the field", colX, ry + 2, DIM, false);
        }
        var pr = ClientState.progress;
        String rank = Ranks.name(pr.rank()) + "  |  " + pr.xp() + " XP  |  next rank at " + Ranks.xpFor(pr.rank() + 1);
        g.drawString(font, rank, w / 2 - font.width(rank) / 2, y + bh - 12, ACCENT, false);
    }

    private static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, PANEL);
        g.fill(x, y, x + w, y + 1, PANEL_LIGHT);
        g.fill(x, y, x + 2, y + h, 0x66F2A33A);
    }

    static boolean livingCheck(Entity e) {
        return e instanceof LivingEntity;
    }

    private HudRenderer() {}
}
