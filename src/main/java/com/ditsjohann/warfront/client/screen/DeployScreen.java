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

    public DeployScreen() {
        super(Component.literal("Deploy"));
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
    private int lx() { return 12; }

    private int mapX() { return 160; }

    private int mapW() { return Math.max(160, width - 160 - 200); }

    private int rx() { return width - 190; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        MatchSnapshot m = ClientState.match;
        Faction mine = ClientState.myFaction();
        // ---- header
        g.fill(0, 0, width, 26, 0xE0101418);
        g.fill(0, 26, width, 27, UiKit.ACCENT);
        g.pose().pushPose();
        g.pose().translate(12, 7, 0);
        g.pose().scale(1.6f, 1.6f, 1);
        g.drawString(font, "DEPLOY", 0, 0, UiKit.TEXT, false);
        g.pose().popPose();
        if (mine != null) {
            String army = mine.displayName.toUpperCase();
            g.drawString(font, army, 80, 10, mine == Faction.COALITION ? 0xFF6FA4FF : 0xFFFF6A5A, false);
            String t = "TICKETS  " + (mine == Faction.COALITION ? m.ticketsA() : m.ticketsB()) + " : " + (mine == Faction.COALITION ? m.ticketsB() : m.ticketsA());
            g.drawString(font, t, width / 2 - font.width(t) / 2, 10, UiKit.TEXT, false);
        }
        String rk = Ranks.name(rank()).toUpperCase() + "  " + ClientState.progress.xp() + " XP";
        g.drawString(font, rk, width - 12 - font.width(rk), 10, UiKit.ACCENT, false);

        // ---- kits
        int y = 40;
        UiKit.header(g, font, "KIT", lx(), y);
        y += 16;
        for (Kit k : Kit.values()) {
            UiKit.button(g, font, k.label.toUpperCase(), lx(), y, 136, 20, mx, my, k == kit, true, 0xFF000000 | k.color);
            y += 23;
        }
        // kit description, wrapped
        y += 4;
        for (var line : font.split(Component.literal(kit.description), 136)) {
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
            UiKit.button(g, font, label, x, y, 178, 18, mx, my, gt == gun(), ok, 0);
            y += 21;
        }
        GunType gt = gun();
        Loadout l = loadout(gt);
        y += 4;
        UiKit.header(g, font, "ATTACHMENTS", x, y);
        y += 16;
        for (Attachment.Slot slot : Attachment.Slot.values()) {
            Attachment a = l.get(slot);
            UiKit.button(g, font, slot.name().charAt(0) + slot.name().substring(1).toLowerCase() + ": " + a.label, x, y, 178, 18, mx, my, false, true, UiKit.DIM);
            y += 21;
        }
        g.drawString(font, l.optic().describe(), x, y, UiKit.DIM, false);
        y += 10;
        g.drawString(font, l.muzzle().describe(), x, y, UiKit.DIM, false);
        y += 10;
        g.drawString(font, l.underbarrel().describe(), x, y, UiKit.DIM, false);
        y += 16;
        var st = gt.stats;
        UiKit.bar(g, font, "Damage", st.damageMax * st.pellets / 19f, x, y, 178);
        y += 11;
        UiKit.bar(g, font, "Fire rate", st.rpm / 900f, x, y, 178);
        y += 11;
        UiKit.bar(g, font, "Accuracy", 1f - st.hipSpread / 6.5f, x, y, 178);
        y += 11;
        UiKit.bar(g, font, "Range", st.falloffEnd / 130f, x, y, 178);
        y += 11;
        UiKit.bar(g, font, "Velocity", st.velocity / 30f, x, y, 178);
        y += 11;
        UiKit.bar(g, font, "Magazine", st.magSize / 100f, x, y, 178);

        // ---- deploy button
        int countdown = Math.max(0, (ClientState.deployCountdown + 19) / 20);
        boolean ready = ClientState.deployCountdown <= 0 && m.active() && m.state() != 2;
        String label = ready ? "DEPLOY  >>" : "DEPLOY IN " + countdown;
        int bw = 220, bx = width / 2 - bw / 2 + 80 - 80, by = height - 40;
        bx = mapX() + mapW() / 2 - bw / 2;
        g.fill(bx, by, bx + bw, by + 28, ready ? 0xF0F2A33A : 0xA0404850);
        g.drawString(font, label, bx + bw / 2 - font.width(label) / 2, by + 10, ready ? 0xFF101010 : UiKit.TEXT, false);
        Spawn sel = selectedSpawn();
        String sp = "SPAWN: " + (sel == null ? "HQ" : sel.label());
        g.drawString(font, sp, bx + bw / 2 - font.width(sp) / 2, by - 12, UiKit.TEXT, false);
        String hint = "ESC to close  |  J to reopen";
        g.drawString(font, hint, width - 12 - font.width(hint), height - 14, UiKit.DIM, false);
    }

    private static List<String> gadgets(Kit k) {
        return switch (k) {
            case ASSAULT -> List.of("Medic Bag x2", "Defibrillator", "Frag Grenade x2", "P-12 Hornet", "Sandbags x12");
            case ENGINEER -> List.of("RL-4 Breacher x5", "Repair Tool", "Frag Grenade", "P-12 Hornet", "Sandbags x24");
            case SUPPORT -> List.of("Ammo Bag x2", "C4 x3", "Frag Grenade x2", "P-12 Hornet", "Sandbags x12");
            case RECON -> List.of("Spawn Beacon", "Field Binoculars", "Frag Grenade", "P-12 Hornet", "Sandbags x12");
        };
    }

    private Spawn selectedSpawn() {
        for (Spawn s : spawns()) if (s.type() == spawnType && s.index() == spawnIndex) return s;
        return null;
    }

    /** Map box in screen space: [x, y, w, h], plus world->screen transform. */
    private int[] mapRect() {
        return new int[]{mapX(), 40, mapW(), height - 40 - 62};
    }

    private int[] toScreen(BlockPos p) {
        MatchSnapshot m = ClientState.match;
        int[] r = mapRect();
        float sx = (float) (r[2] - 20) / Math.max(1, m.maxX() - m.minX());
        float sz = (float) (r[3] - 20) / Math.max(1, m.maxZ() - m.minZ());
        float s = Math.min(sx, sz);
        float ox = r[0] + r[2] / 2f - (m.minX() + m.maxX()) / 2f * s;
        float oz = r[1] + r[3] / 2f - (m.minZ() + m.maxZ()) / 2f * s;
        return new int[]{Math.round(ox + p.getX() * s), Math.round(oz + p.getZ() * s)};
    }

    private void drawMap(GuiGraphics g, int mx, int my) {
        int[] r = mapRect();
        g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], 0xD0182018);
        // grid
        for (int gx = r[0]; gx < r[0] + r[2]; gx += 24) g.fill(gx, r[1], gx + 1, r[1] + r[3], 0x18FFFFFF);
        for (int gy = r[1]; gy < r[1] + r[3]; gy += 24) g.fill(r[0], gy, r[0] + r[2], gy + 1, 0x18FFFFFF);
        g.drawString(font, "TACTICAL MAP - click a spawn point", r[0] + 6, r[1] + 5, UiKit.DIM, false);
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
        List<Spawn> spawns = spawns();
        for (MatchSnapshot.Flag f : m.flags()) {
            int[] s = toScreen(f.pos());
            int col = f.owner() < 0 ? 0xFF9A9A9A : f.owner() == my0 ? UiKit.BLUE : UiKit.RED;
            boolean spawnable = spawns.stream().anyMatch(sp -> sp.type() == Deployment.SPAWN_FLAG && sp.pos().equals(f.pos()));
            boolean sel = spawnType == Deployment.SPAWN_FLAG && spawnIndex == m.flags().indexOf(f);
            int rr = sel ? 12 : 10;
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
        UiKit.button(g, font, "SPAWN ON SQUAD", r[0] + r[2] - 112, r[1] + r[3] - 24, 106, 18, mx, my, squadSel, true, 0xFF7AFF7A);
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
    public boolean mouseClicked(double mx, double my, int button) {
        // kits
        int y = 56;
        for (Kit k : Kit.values()) {
            if (UiKit.inside(mx, my, lx(), y, 136, 20)) {
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
            if (UiKit.inside(mx, my, x, y, 178, 18) && unlocked(gt.rankRequired)) {
                GUN.put(kit, gt);
                click();
                return true;
            }
            y += 21;
        }
        y += 20;
        GunType gt = gun();
        for (Attachment.Slot slot : Attachment.Slot.values()) {
            if (UiKit.inside(mx, my, x, y, 178, 18)) {
                cycleAttachment(gt, slot, button == 1 ? -1 : 1);
                click();
                return true;
            }
            y += 21;
        }
        // map spawn points
        MatchSnapshot m = ClientState.match;
        int[] r = mapRect();
        if (UiKit.inside(mx, my, r[0] + r[2] - 112, r[1] + r[3] - 24, 106, 18)) {
            spawnType = Deployment.SPAWN_SQUAD;
            spawnIndex = 0;
            click();
            return true;
        }
        for (Spawn sp : spawns()) {
            if (sp.pos() == null) continue;
            int[] s = toScreen(sp.pos());
            if (Math.abs(mx - s[0]) <= 12 && Math.abs(my - s[1]) <= 12) {
                spawnType = sp.type();
                spawnIndex = sp.index();
                click();
                return true;
            }
        }
        // deploy
        int bw = 220, bx = mapX() + mapW() / 2 - bw / 2, by = height - 40;
        if (UiKit.inside(mx, my, bx, by, bw, 28) && ClientState.deployCountdown <= 0 && m.active()) {
            deploy();
            return true;
        }
        return super.mouseClicked(mx, my, button);
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
            if (unlocked(next.rankRequired)) {
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
