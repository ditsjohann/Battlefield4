package com.ditsjohann.warfront.client.screen;

import com.ditsjohann.warfront.client.ClientState;
import com.ditsjohann.warfront.combat.Faction;
import com.ditsjohann.warfront.match.Deployment;
import com.ditsjohann.warfront.match.Kit;
import com.ditsjohann.warfront.match.MatchSnapshot;
import com.ditsjohann.warfront.network.WarfrontNet;
import com.ditsjohann.warfront.network.packets.SimplePackets;
import com.ditsjohann.warfront.progression.Ranks;
import com.ditsjohann.warfront.registry.ModSounds;
import com.ditsjohann.warfront.weapon.Attachment;
import com.ditsjohann.warfront.weapon.GunType;
import com.ditsjohann.warfront.weapon.Loadout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The deploy screen: choose a kit, a primary weapon and its attachments, then pick a spawn point
 * on the tactical map (HQ, a held flag, a spawn beacon or your squad) and deploy.
 */
public class DeployScreen extends Screen {
    private static Kit kit = Kit.ASSAULT;
    private static final Map<Kit, GunType> GUN = new EnumMap<>(Kit.class);
    private static final Map<GunType, Loadout> LOADOUT = new EnumMap<>(GunType.class);
    private static int spawnType = Deployment.SPAWN_HQ, spawnIndex;

    private record Spawn(int type, int index, BlockPos pos, String label) {}

    /** The screen lays out on a virtual canvas of at least 520x300 and is scaled down to fit small GUIs. */
    private float f = 1f;
    private int vw, vh;

    public DeployScreen() {
        super(Component.literal("Deploy"));
    }

    private void computeScale() {
        f = Math.min(1f, Math.min(width / 520f, height / 300f));
        vw = Math.round(width / f);
        vh = Math.round(height / f);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xB0080A0C);
    }

    private GunType gun() {
        GunType g = GUN.getOrDefault(kit, kit.primaries.get(0));
        return kit.primaries.contains(g) ? g : kit.primaries.get(0);
    }

    private Loadout loadout(GunType g) {
        return LOADOUT.getOrDefault(g, g.defaultLoadout());
    }

    private int rank() {
        return ClientState.progress.rank();
    }

    private boolean creative() {
        return Minecraft.getInstance().player != null && Minecraft.getInstance().player.getAbilities().instabuild;
    }

    private boolean unlocked(int req) {
        return creative() || rank() >= req;
    }

    private List<Spawn> spawns() {
        List<Spawn> out = new ArrayList<>();
        MatchSnapshot m = ClientState.match;
        Faction f = ClientState.myFaction();
        if (!m.active() || f == null) return out;
        out.add(new Spawn(Deployment.SPAWN_HQ, 0, f == Faction.COALITION ? m.hqA() : m.hqB(), "HQ"));
        for (int i = 0; i < m.flags().size(); i++) {
            MatchSnapshot.Flag fl = m.flags().get(i);
            int enemies = f == Faction.COALITION ? fl.countB() : fl.countA();
            if (fl.owner() == f.ordinal() && enemies == 0 && Math.abs(fl.control()) > 0.5f) out.add(new Spawn(Deployment.SPAWN_FLAG, i, fl.pos(), "FLAG " + fl.letter()));
        }
        for (int i = 0; i < m.beacons().size(); i++) {
            MatchSnapshot.Beacon b = m.beacons().get(i);
            if (b.faction() == f.ordinal()) out.add(new Spawn(Deployment.SPAWN_BEACON, i, b.pos(), "BEACON (" + b.owner() + ")"));
        }
        out.add(new Spawn(Deployment.SPAWN_SQUAD, 0, null, "SQUAD"));
        return out;
    }

    // ------------------------------------------------------------------ layout
    private static final int LW = 116, RW = 156;

    private int lx() { return 8; }

    private int mapX() { return lx() + LW + 8; }

    private int mapW() { return Math.max(80, rx() - 8 - mapX()); }

    private int rx() { return vw - RW - 8; }

    @Override
    public void render(GuiGraphics g, int rawMx, int rawMy, float pt) {
        super.render(g, rawMx, rawMy, pt);
        computeScale();
        int mx = Math.round(rawMx / f), my = Math.round(rawMy / f);
        g.pose().pushPose();
        g.pose().scale(f, f, 1);
        renderScaled(g, mx, my);
        g.pose().popPose();
    }

    private void renderScaled(GuiGraphics g, int mx, int my) {
        MatchSnapshot m = ClientState.match;
        Faction mine = ClientState.myFaction();
        // ---- header
        g.fill(0, 0, vw, 26, 0xE0101418);
        g.fill(0, 26, vw, 27, UiKit.ACCENT);
        g.pose().pushPose();
        g.pose().translate(12, 7, 0);
        g.pose().scale(1.6f, 1.6f, 1);
        g.drawString(font, "DEPLOY", 0, 0, UiKit.TEXT, false);
        g.pose().popPose();
        if (mine != null) {
            String army = mine.displayName.toUpperCase();
            g.drawString(font, army, 80, 10, mine == Faction.COALITION ? 0xFF6FA4FF : 0xFFFF6A5A, false);
            String t = "TICKETS  " + (mine == Faction.COALITION ? m.ticketsA() : m.ticketsB()) + " : " + (mine == Faction.COALITION ? m.ticketsB() : m.ticketsA());
            g.drawString(font, t, vw / 2 - font.width(t) / 2, 10, UiKit.TEXT, false);
        }
        String rk = Ranks.name(rank()).toUpperCase() + "  " + ClientState.progress.xp() + " XP";
        g.drawString(font, rk, vw - 12 - font.width(rk), 10, UiKit.ACCENT, false);

        // ---- kits
        int y = 40;
        UiKit.header(g, font, "KIT", lx(), y);
        y += 16;
        for (Kit k : Kit.values()) {
            UiKit.button(g, font, k.label.toUpperCase(), lx(), y, LW, 20, mx, my, k == kit, true, 0xFF000000 | k.color);
            y += 23;
        }
        // kit description, wrapped
        y += 4;
        for (var line : font.split(Component.literal(kit.description), LW)) {
            g.drawString(font, line, lx(), y, UiKit.DIM, false);
            y += 10;
        }
        y += 6;
        UiKit.header(g, font, "GADGETS", lx(), y);
        y += 14;
        for (String s : gadgets(kit)) {
            g.drawString(font, "- " + s, lx(), y, UiKit.TEXT, false);
            y += 10;
        }

        // ---- tactical map
        drawMap(g, mx, my);

        // ---- weapon
        int x = rx();
        y = 40;
        UiKit.header(g, font, "PRIMARY", x, y);
        y += 16;
        for (GunType gt : kit.primaries) {
            boolean ok = unlocked(gt.rankRequired);
            String label = ok ? gt.displayName : gt.displayName + "  [" + Ranks.shortName(gt.rankRequired) + "]";
            UiKit.button(g, font, label, x, y, RW, 18, mx, my, gt == gun(), ok, 0);
            y += 21;
        }
        GunType gt = gun();
        Loadout l = loadout(gt);
        y += 4;
        UiKit.header(g, font, "ATTACHMENTS", x, y);
        y += 16;
        for (Attachment.Slot slot : Attachment.Slot.values()) {
            Attachment a = l.get(slot);
            boolean hover = UiKit.button(g, font, a.label, x, y, RW, 18, mx, my, false, true, UiKit.DIM);
            if (hover) g.renderTooltip(font, font.split(Component.literal(slot.name() + ": " + a.describe() + "  (click to cycle)"), 160), mx, my);
            y += 21;
        }
        int wk = ClientState.progress.weaponKills(gt);
        Attachment next = null;
        for (Attachment a : Attachment.values()) {
            if (gt.allows(a) && !a.unlockedFor(gt, wk) && (next == null || a.killsRequired < next.killsRequired)) next = a;
        }
        String mastery = next == null ? wk + " kills - all unlocked" : "Next: " + next.label + " (" + wk + "/" + next.killsRequired + ")";
        g.drawString(font, mastery, x, y, next == null ? 0xFF7FFF7F : UiKit.DIM, false);
        y += 12;
        var st = gt.stats;
        UiKit.bar(g, font, "Damage", st.damageMax * st.pellets / 19f, x, y, RW);
        y += 11;
        UiKit.bar(g, font, "Fire rate", st.rpm / 900f, x, y, RW);
        y += 11;
        UiKit.bar(g, font, "Accuracy", 1f - st.hipSpread / 6.5f, x, y, RW);
        y += 11;
        UiKit.bar(g, font, "Range", st.falloffEnd / 130f, x, y, RW);
        y += 11;
        UiKit.bar(g, font, "Velocity", st.velocity / 30f, x, y, RW);
        y += 11;
        UiKit.bar(g, font, "Magazine", st.magSize / 100f, x, y, RW);

        // ---- deploy button
        int countdown = Math.max(0, (ClientState.deployCountdown + 19) / 20);
        boolean ready = ClientState.deployCountdown <= 0 && m.active() && m.state() != 2;
        String label = ready ? "DEPLOY  >>" : "DEPLOY IN " + countdown;
        int bw = Math.min(200, mapW()), bx = mapX() + mapW() / 2 - bw / 2, by = vh - 40;
        g.fill(bx, by, bx + bw, by + 28, ready ? 0xF0F2A33A : 0xA0404850);
        g.drawString(font, label, bx + bw / 2 - font.width(label) / 2, by + 10, ready ? 0xFF101010 : UiKit.TEXT, false);
        Spawn sel = selectedSpawn();
        String sp = "SPAWN: " + (sel == null ? "HQ" : sel.label());
        g.drawString(font, sp, bx + bw / 2 - font.width(sp) / 2, by - 12, UiKit.TEXT, false);
        String hint = "ESC close | J reopen | ENTER deploy";
        g.drawString(font, hint, mapX(), vh - 10, UiKit.DIM, false);
    }

    private static List<String> gadgets(Kit k) {
        return switch (k) {
            case ASSAULT -> List.of("Medic Bag x2", "Defibrillator", "Frag Grenade x2", "P-12 Hornet", "Sandbags x12");
            case ENGINEER -> List.of("RL-4 Breacher x5", "Repair Tool", "Frag Grenade", "P-12 Hornet", "Sandbags x24");
            case SUPPORT -> List.of("Ammo Bag x2", "C4 x3", "Frag Grenade x2", "P-12 Hornet", "Sandbags x12");
            case RECON -> List.of("Spawn Beacon", "Field Binoculars", "Smoke Grenade x2", "P-12 Hornet", "Sandbags x12");
        };
    }

    private Spawn selectedSpawn() {
        for (Spawn s : spawns()) if (s.type() == spawnType && s.index() == spawnIndex) return s;
        return null;
    }

    /** Map box in screen space: [x, y, w, h], plus world->screen transform. */
    private int[] mapRect() {
        return new int[]{mapX(), 40, mapW(), vh - 40 - 58};
    }

    /** Projects a world position onto the tactical map, rotating so the battlefield's long axis fits the panel. */
    private int[] toScreen(BlockPos p) {
        MatchSnapshot m = ClientState.match;
        int[] r = mapRect();
        float ex = Math.max(1, m.maxX() - m.minX()), ez = Math.max(1, m.maxZ() - m.minZ());
        boolean rotate = (r[3] > r[2]) != (ez > ex);
        float cx = (m.minX() + m.maxX()) / 2f, cz = (m.minZ() + m.maxZ()) / 2f;
        float dx = p.getX() - cx, dz = p.getZ() - cz;
        float u = rotate ? -dz : dx, v = rotate ? dx : dz;
        float eu = rotate ? ez : ex, ev = rotate ? ex : ez;
        float s = Math.min((r[2] - 24) / eu, (r[3] - 24) / ev);
        return new int[]{Math.round(r[0] + r[2] / 2f + u * s), Math.round(r[1] + r[3] / 2f + v * s)};
    }

    private int flagSize() {
        int[] r = mapRect();
        return Math.max(5, Math.min(10, Math.min(r[2], r[3]) / 16));
    }

    private void drawMap(GuiGraphics g, int mx, int my) {
        int[] r = mapRect();
        g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], 0xD0182018);
        // grid
        for (int gx = r[0]; gx < r[0] + r[2]; gx += 24) g.fill(gx, r[1], gx + 1, r[1] + r[3], 0x18FFFFFF);
        for (int gy = r[1]; gy < r[1] + r[3]; gy += 24) g.fill(r[0], gy, r[0] + r[2], gy + 1, 0x18FFFFFF);
        g.drawString(font, "TACTICAL MAP", r[0] + 4, r[1] + 4, UiKit.DIM, false);
        MatchSnapshot m = ClientState.match;
        if (!m.active()) return;
        Faction mine = ClientState.myFaction();
        int my0 = mine == null ? 0 : mine.ordinal();
        // front line: connect flags
        int[] prev = null;
        for (MatchSnapshot.Flag f : m.flags()) {
            int[] s = toScreen(f.pos());
            if (prev != null) line(g, prev[0], prev[1], s[0], s[1], 0x30FFFFFF);
            prev = s;
        }
        if (m.towerPos() != null && !m.towerPos().equals(BlockPos.ZERO)) {
            int[] s = toScreen(m.towerPos());
            g.fill(s[0] - 3, s[1] - 3, s[0] + 3, s[1] + 3, m.towerCollapsed() ? 0xFF505050 : 0xFFE0A020);
            g.drawString(font, m.towerCollapsed() ? "RUINS" : "TOWER", s[0] + 5, s[1] - 4, UiKit.DIM, false);
        }
        if (!m.depotPos().equals(BlockPos.ZERO)) {
            int[] s = toScreen(m.depotPos());
            g.fill(s[0] - 3, s[1] - 3, s[0] + 3, s[1] + 3, m.depotDestroyed() ? 0xFF505050 : 0xFFD83A2E);
            g.drawString(font, m.depotDestroyed() ? "SMOKE" : "FUEL", s[0] + 5, s[1] - 4, UiKit.DIM, false);
        }
        List<Spawn> spawns = spawns();
        for (MatchSnapshot.Flag f : m.flags()) {
            int[] s = toScreen(f.pos());
            int col = f.owner() < 0 ? 0xFF9A9A9A : f.owner() == my0 ? UiKit.BLUE : UiKit.RED;
            boolean spawnable = spawns.stream().anyMatch(sp -> sp.type() == Deployment.SPAWN_FLAG && sp.pos().equals(f.pos()));
            boolean sel = spawnType == Deployment.SPAWN_FLAG && spawnIndex == m.flags().indexOf(f);
            int rr = flagSize() + (sel ? 2 : 0);
            g.fill(s[0] - rr - 1, s[1] - rr - 1, s[0] + rr + 1, s[1] + rr + 1, sel ? UiKit.ACCENT : 0xFF0A0A0A);
            g.fill(s[0] - rr, s[1] - rr, s[0] + rr, s[1] + rr, col);
            String l = String.valueOf(f.letter());
            g.drawString(font, l, s[0] - font.width(l) / 2 + 1, s[1] - 4, 0xFFFFFFFF, true);
            if (f.contested()) g.drawString(font, "CONTESTED", s[0] - font.width("CONTESTED") / 2, s[1] + rr + 3, 0xFFFFD060, false);
            else if (spawnable) g.drawString(font, "SPAWN", s[0] - font.width("SPAWN") / 2, s[1] + rr + 3, 0xFF9FD0FF, false);
        }
        for (int i = 0; i < 2; i++) {
            BlockPos hq = i == 0 ? m.hqA() : m.hqB();
            int[] s = toScreen(hq);
            int col = i == my0 ? UiKit.BLUE : UiKit.RED;
            boolean sel = i == my0 && spawnType == Deployment.SPAWN_HQ;
            g.fill(s[0] - 9, s[1] - 7, s[0] + 9, s[1] + 7, sel ? UiKit.ACCENT : 0xFF0A0A0A);
            g.fill(s[0] - 8, s[1] - 6, s[0] + 8, s[1] + 6, col);
            g.drawString(font, "HQ", s[0] - font.width("HQ") / 2 + 1, s[1] - 4, 0xFFFFFFFF, true);
        }
        for (Spawn sp : spawns) {
            if (sp.type() != Deployment.SPAWN_BEACON) continue;
            int[] s = toScreen(sp.pos());
            boolean sel = spawnType == sp.type() && spawnIndex == sp.index();
            g.fill(s[0] - 4, s[1] - 4, s[0] + 4, s[1] + 4, sel ? UiKit.ACCENT : 0xFF7AFF7A);
        }
        // squad spawn button
        boolean squadSel = spawnType == Deployment.SPAWN_SQUAD;
        UiKit.button(g, font, "SQUAD", r[0] + r[2] - 50, r[1] + r[3] - 22, 46, 18, mx, my, squadSel, true, 0xFF7AFF7A);
        // player marker
        var p = Minecraft.getInstance().player;
        if (p != null) {
            int[] s = toScreen(p.blockPosition());
            g.fill(s[0] - 2, s[1] - 2, s[0] + 2, s[1] + 2, 0xFFFFFFFF);
        }
    }

    private static void line(GuiGraphics g, int x0, int y0, int x1, int y1, int col) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 0; i <= steps; i += 2) {
            int x = x0 + (x1 - x0) * i / Math.max(1, steps), y = y0 + (y1 - y0) * i / Math.max(1, steps);
            g.fill(x, y, x + 1, y + 1, col);
        }
    }

    @Override
    public boolean mouseClicked(double rawMx, double rawMy, int button) {
        computeScale();
        double mx = rawMx / f, my = rawMy / f;
        // kits
        int y = 56;
        for (Kit k : Kit.values()) {
            if (UiKit.inside(mx, my, lx(), y, LW, 20)) {
                kit = k;
                click();
                return true;
            }
            y += 23;
        }
        // primaries
        int x = rx();
        y = 56;
        for (GunType gt : kit.primaries) {
            if (UiKit.inside(mx, my, x, y, RW, 18) && unlocked(gt.rankRequired)) {
                GUN.put(kit, gt);
                click();
                return true;
            }
            y += 21;
        }
        y += 20;
        GunType gt = gun();
        for (Attachment.Slot slot : Attachment.Slot.values()) {
            if (UiKit.inside(mx, my, x, y, RW, 18)) {
                cycleAttachment(gt, slot, button == 1 ? -1 : 1);
                click();
                return true;
            }
            y += 21;
        }
        // map spawn points
        MatchSnapshot m = ClientState.match;
        int[] r = mapRect();
        if (UiKit.inside(mx, my, r[0] + r[2] - 50, r[1] + r[3] - 22, 46, 18)) {
            spawnType = Deployment.SPAWN_SQUAD;
            spawnIndex = 0;
            click();
            return true;
        }
        for (Spawn sp : spawns()) {
            if (sp.pos() == null) continue;
            int[] s = toScreen(sp.pos());
            if (Math.abs(mx - s[0]) <= flagSize() + 2 && Math.abs(my - s[1]) <= flagSize() + 2) {
                spawnType = sp.type();
                spawnIndex = sp.index();
                click();
                return true;
            }
        }
        // deploy
        int bw = Math.min(200, mapW()), bx = mapX() + mapW() / 2 - bw / 2, by = vh - 40;
        if (UiKit.inside(mx, my, bx, by, bw, 28) && ClientState.deployCountdown <= 0 && m.active()) {
            deploy();
            return true;
        }
        return super.mouseClicked(rawMx, rawMy, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if ((key == 257 || key == 32) && ClientState.deployCountdown <= 0) { // enter / space
            deploy();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    private void cycleAttachment(GunType gt, Attachment.Slot slot, int dir) {
        List<Attachment> opts = gt.options(slot);
        Loadout l = loadout(gt);
        int i = opts.indexOf(l.get(slot));
        for (int step = 1; step <= opts.size(); step++) {
            Attachment next = opts.get(Math.floorMod(i + dir * step, opts.size()));
            if (creative() || next.unlockedFor(gt, ClientState.progress.weaponKills(gt))) {
                LOADOUT.put(gt, l.with(next));
                return;
            }
        }
    }

    private void deploy() {
        Spawn sel = selectedSpawn();
        int type = sel == null ? Deployment.SPAWN_HQ : sel.type();
        int idx = sel == null ? 0 : sel.index();
        GunType gt = gun();
        WarfrontNet.toServer(new SimplePackets.Deploy(type, idx, kit.ordinal(), gt.id, loadout(gt)));
        onClose();
    }

    private void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.get("fire_mode"), 1.2f, 0.4f));
    }

    @Override
    public void tick() {
        if (!ClientState.match.active()) onClose();
    }
}
