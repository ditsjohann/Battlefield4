package com.ditsjohann.warfront.client.screen;

import com.ditsjohann.warfront.client.ClientState;
import com.ditsjohann.warfront.client.KeyBinds;
import com.ditsjohann.warfront.combat.Faction;
import com.ditsjohann.warfront.match.MatchSnapshot;
import com.ditsjohann.warfront.network.WarfrontNet;
import com.ditsjohann.warfront.network.packets.SimplePackets;
import com.ditsjohann.warfront.registry.ModSounds;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * The commo rose: hold the orders key, point at a wedge, release. Squad orders for every flag
 * (attack or defend depending on who holds it), follow me, hold here, and calls for a medic or
 * ammo that bring the nearest friendly AI medic / support gunner running.
 */
public class CommoRoseScreen extends Screen {
    private record Option(String label, String sub, int command, int arg, int color) {}

    private final List<Option> options = new ArrayList<>();
    private int hovered = -1;
    private boolean sent;
    private final boolean hold;

    public CommoRoseScreen() {
        this(true);
    }

    /** @param hold true when opened by holding the orders key (release selects); false = click to select. */
    public CommoRoseScreen(boolean hold) {
        super(Component.literal("Orders"));
        this.hold = hold;
        MatchSnapshot m = ClientState.match;
        Faction mine = ClientState.myFaction();
        for (int i = 0; i < m.flags().size(); i++) {
            MatchSnapshot.Flag f = m.flags().get(i);
            boolean ours = mine != null && f.owner() == mine.ordinal();
            options.add(new Option((ours ? "DEFEND " : "ATTACK ") + f.letter(), ours ? "hold the flag" : f.owner() < 0 ? "neutral flag" : "enemy flag",
                    SimplePackets.SquadCommand.FLAG, i, ours ? 0xFF4F8FFF : 0xFFFF6A5A));
        }
        options.add(new Option("FOLLOW ME", "squad regroups", SimplePackets.SquadCommand.FOLLOW, 0, 0xFF7AFF7A));
        options.add(new Option("HOLD HERE", "dig in at your position", SimplePackets.SquadCommand.HOLD, 0, 0xFFF2A33A));
        options.add(new Option("NEED MEDIC", "nearest medic comes to you", SimplePackets.SquadCommand.MEDIC, 0, 0xFFE8E8E8));
        options.add(new Option("NEED AMMO", "nearest support drops ammo", SimplePackets.SquadCommand.AMMO, 0, 0xFF5FBF3A));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0x50000000);
    }

    @Override
    public void render(GuiGraphics g, int rawMx, int rawMy, float pt) {
        super.render(g, rawMx, rawMy, pt);
        // lay out on a virtual canvas at least 320 tall so wedges never overlap on small GUIs
        float f = Math.min(1f, height / 320f);
        int vw = Math.round(width / f), vh = Math.round(height / f);
        int mx = Math.round(rawMx / f), my = Math.round(rawMy / f);
        g.pose().pushPose();
        g.pose().scale(f, f, 1);
        int cx = vw / 2, cy = vh / 2;
        int n = options.size();
        int ry = vh / 2 - 34;
        int rx = Math.min(vw / 2 - 80, Math.round(ry * 1.7f));
        double dx = (mx - cx) / (double) rx, dy = (my - cy) / (double) ry;
        hovered = -1;
        if (dx * dx + dy * dy > 0.02) {
            double a = Math.atan2(dx, -dy); // 0 at top, clockwise
            if (a < 0) a += Math.PI * 2;
            hovered = (int) Math.floor((a / (Math.PI * 2)) * n + 0.5) % n;
        }
        // spokes
        for (int i = 0; i < n; i++) {
            double a = (i + 0.5) / n * Math.PI * 2;
            for (double t = 0.15; t < 1.08; t += 0.025) {
                int x = cx + (int) (Math.sin(a) * rx * t), y = cy - (int) (Math.cos(a) * ry * t);
                g.fill(x, y, x + 1, y + 1, 0x40FFFFFF);
            }
        }
        g.fill(cx - 2, cy - 2, cx + 3, cy + 3, 0xFFF2A33A);
        for (int i = 0; i < n; i++) {
            Option o = options.get(i);
            double a = (double) i / n * Math.PI * 2;
            int x = cx + (int) (Math.sin(a) * rx), y = cy - (int) (Math.cos(a) * ry);
            int w = Math.max(font.width(o.label), font.width(o.sub)) + 12;
            boolean hov = i == hovered;
            g.fill(x - w / 2, y - 12, x + w / 2, y + 13, hov ? 0xF0283848 : 0xD0101418);
            g.fill(x - w / 2, y - 12, x - w / 2 + 2, y + 13, o.color);
            if (hov) g.fill(x - w / 2, y + 12, x + w / 2, y + 13, 0xFFF2A33A);
            g.drawString(font, o.label, x - font.width(o.label) / 2, y - 9, 0xFFFFFFFF, false);
            g.drawString(font, o.sub, x - font.width(o.sub) / 2, y + 2, 0xFF8C98A4, false);
        }
        String hint = hold ? "Point and release " + KeyBinds.ORDERS.getTranslatedKeyMessage().getString() : "Click an order";
        g.drawString(font, hint, cx - font.width(hint) / 2, cy + 10, 0xFF8C98A4, false);
        g.pose().popPose();
    }

    @Override
    public void tick() {
        if (!hold) return;
        // releasing the hold key confirms the hovered wedge
        long win = Minecraft.getInstance().getWindow().getWindow();
        InputConstants.Key key = KeyBinds.ORDERS.getKey();
        boolean down = key.getType() == InputConstants.Type.KEYSYM && InputConstants.isKeyDown(win, key.getValue());
        if (!down) select();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        select();
        return true;
    }

    private void select() {
        if (sent) return;
        sent = true;
        if (hovered >= 0 && hovered < options.size()) {
            Option o = options.get(hovered);
            WarfrontNet.toServer(new SimplePackets.SquadCommand(o.command, o.arg));
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.get("spot"), 1.1f, 0.6f));
        }
        onClose();
    }

    static int clamp(int v) {
        return Mth.clamp(v, 0, 255);
    }
}
