package com.ditsjohann.warfront.client.screen;

import com.ditsjohann.warfront.client.ClientState;
import com.ditsjohann.warfront.combat.Faction;
import com.ditsjohann.warfront.match.ConquestMatch;
import com.ditsjohann.warfront.match.MatchSnapshot;
import com.ditsjohann.warfront.network.WarfrontNet;
import com.ditsjohann.warfront.network.packets.SimplePackets;
import com.ditsjohann.warfront.progression.Ranks;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** Start a Conquest battle on the terrain ahead, or join / leave / end the current one. */
public class WarTableScreen extends Screen {
    private static final String[] SIZES = {"SKIRMISH  (3 flags)", "CONQUEST  (5 flags)", "CONQUEST LARGE  (7 flags)"};
    private static final int[] SOLDIERS = {4, 8, 12, 16, 20, 28};
    private static final int[] TICKETS = {75, 150, 300, 500};
    private static int size = 1, faction = 0, soldiers = 2, tickets = 1;
    private final BlockPos pos;

    public WarTableScreen(BlockPos pos) {
        super(Component.literal("War Table"));
        this.pos = pos;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int px() { return width / 2 - 170; }

    private int py() { return height / 2 - 110; }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        renderTransparentBackground(g);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        int x = px(), y = py();
        g.fill(x, y, x + 340, y + 220, UiKit.BG);
        g.fill(x, y, x + 340, y + 2, UiKit.ACCENT);
        g.pose().pushPose();
        g.pose().translate(x + 12, y + 10, 0);
        g.pose().scale(2f, 2f, 1);
        g.drawString(font, "WAR TABLE", 0, 0, UiKit.TEXT, false);
        g.pose().popPose();
        var pr = ClientState.progress;
        String rank = Ranks.name(pr.rank()).toUpperCase() + "  " + pr.xp() + " XP   K/D " + pr.kills() + "/" + pr.deaths() + "   W " + pr.wins() + "/" + pr.matches();
        g.drawString(font, rank, x + 328 - font.width(rank), y + 14, UiKit.DIM, false);
        MatchSnapshot m = ClientState.match;
        boolean active = m.active() && m.state() != ConquestMatch.ENDED;
        if (!active) {
            UiKit.header(g, font, "DEPLOY A BATTLEFIELD", x + 12, y + 38);
            g.drawString(font, "Builds a Conquest battle on the terrain the table faces:", x + 12, y + 54, UiKit.DIM, false);
            g.drawString(font, "two HQs, fortified flags and a destructible Comms Tower.", x + 12, y + 64, UiKit.DIM, false);
            UiKit.button(g, font, "MODE: " + SIZES[size], x + 12, y + 82, 316, 18, mx, my, false, true, UiKit.ACCENT);
            Faction f = Faction.byId(faction);
            UiKit.button(g, font, "YOUR ARMY: " + f.displayName.toUpperCase(), x + 12, y + 104, 316, 18, mx, my, false, true, faction == 0 ? UiKit.BLUE : UiKit.RED);
            UiKit.button(g, font, "AI SOLDIERS PER SIDE: " + SOLDIERS[soldiers], x + 12, y + 126, 155, 18, mx, my, false, true, UiKit.DIM);
            UiKit.button(g, font, "TICKETS: " + TICKETS[tickets], x + 173, y + 126, 155, 18, mx, my, false, true, UiKit.DIM);
            UiKit.button(g, font, "DEPLOY BATTLEFIELD", x + 12, y + 182, 316, 26, mx, my, true, true, 0);
            g.drawString(font, "Warning: terraforms and builds in front of this table.", x + 12, y + 160, 0xFFFFB060, false);
        } else {
            UiKit.header(g, font, "BATTLE IN PROGRESS", x + 12, y + 38);
            String t = Faction.COALITION.displayName + "  " + m.ticketsA() + "   vs   " + m.ticketsB() + "  " + Faction.LEGION.displayName;
            g.drawString(font, t, x + 170 - font.width(t) / 2, y + 56, UiKit.TEXT, false);
            StringBuilder flags = new StringBuilder();
            for (MatchSnapshot.Flag fl : m.flags()) flags.append(fl.letter()).append(fl.owner() == 0 ? "[IRC] " : fl.owner() == 1 ? "[EML] " : "[---] ");
            g.drawString(font, flags.toString(), x + 170 - font.width(flags.toString()) / 2, y + 70, UiKit.DIM, false);
            Faction mine = ClientState.myFaction();
            UiKit.button(g, font, "JOIN " + Faction.COALITION.displayName.toUpperCase(), x + 12, y + 92, 155, 22, mx, my, mine == Faction.COALITION, true, UiKit.BLUE);
            UiKit.button(g, font, "JOIN " + Faction.LEGION.displayName.toUpperCase(), x + 173, y + 92, 155, 22, mx, my, mine == Faction.LEGION, true, UiKit.RED);
            UiKit.button(g, font, "LEAVE BATTLE", x + 12, y + 120, 155, 20, mx, my, false, mine != null, 0);
            UiKit.button(g, font, "END BATTLE", x + 173, y + 120, 155, 20, mx, my, false, true, 0);
            g.drawString(font, "Controls: LMB fire | RMB aim | R reload | B fire mode", x + 12, y + 152, UiKit.DIM, false);
            g.drawString(font, "Z spot enemy / order squad to flag | J deploy | TAB scores", x + 12, y + 164, UiKit.DIM, false);
            g.drawString(font, "Hold SPACE while downed to redeploy.", x + 12, y + 176, UiKit.DIM, false);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x = px(), y = py();
        MatchSnapshot m = ClientState.match;
        boolean active = m.active() && m.state() != ConquestMatch.ENDED;
        if (!active) {
            if (UiKit.inside(mx, my, x + 12, y + 82, 316, 18)) size = (size + 1) % SIZES.length;
            else if (UiKit.inside(mx, my, x + 12, y + 104, 316, 18)) faction = 1 - faction;
            else if (UiKit.inside(mx, my, x + 12, y + 126, 155, 18)) soldiers = (soldiers + 1) % SOLDIERS.length;
            else if (UiKit.inside(mx, my, x + 173, y + 126, 155, 18)) tickets = (tickets + 1) % TICKETS.length;
            else if (UiKit.inside(mx, my, x + 12, y + 182, 316, 26)) {
                send(SimplePackets.WarTable.START, faction);
                onClose();
            } else return super.mouseClicked(mx, my, button);
            return true;
        }
        if (UiKit.inside(mx, my, x + 12, y + 92, 155, 22)) send(SimplePackets.WarTable.JOIN, 0);
        else if (UiKit.inside(mx, my, x + 173, y + 92, 155, 22)) send(SimplePackets.WarTable.JOIN, 1);
        else if (UiKit.inside(mx, my, x + 12, y + 120, 155, 20)) {
            send(SimplePackets.WarTable.LEAVE, 0);
            onClose();
        } else if (UiKit.inside(mx, my, x + 173, y + 120, 155, 20)) {
            send(SimplePackets.WarTable.END, 0);
            onClose();
        } else return super.mouseClicked(mx, my, button);
        return true;
    }

    private void send(int cmd, int f) {
        WarfrontNet.toServer(new SimplePackets.WarTable(pos, cmd, size, f, SOLDIERS[soldiers], TICKETS[tickets]));
    }
}
