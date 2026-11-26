package com.ditsjohann.warfront.client;

import com.ditsjohann.warfront.client.render.Tracers;
import com.ditsjohann.warfront.client.screen.DeployScreen;
import com.ditsjohann.warfront.client.screen.WarTableScreen;
import com.ditsjohann.warfront.match.MatchSnapshot;
import com.ditsjohann.warfront.network.packets.SimplePackets;
import com.ditsjohann.warfront.network.packets.TracerPacket;
import com.ditsjohann.warfront.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;

/** Client-side packet handlers (called on the client main thread). */
public final class ClientPacketHandler {
    public static void tracer(TracerPacket p) {
        Tracers.add(p.origin(), p.velocity(), p.gravity(), p.color());
        if (!p.suppressed() && p.shooterId() >= 0) ClientState.REVEALED.put(p.shooterId(), ClientState.now());
    }

    public static void hitMarker(SimplePackets.HitMarker m) {
        ClientState.hitMarkerTicks = 8;
        ClientState.hitHead = (m.flags() & 2) != 0;
        ClientState.hitKill = (m.flags() & 4) != 0;
        ClientState.hitVehicle = (m.flags() & 8) != 0;
        Minecraft mc = Minecraft.getInstance();
        String sound = ClientState.hitKill ? "kill_confirm" : ClientState.hitHead ? "headshot" : "hitmarker";
        mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.get(sound), 1f, 0.7f));
    }

    public static void suppressed(SimplePackets.Suppressed s) {
        ClientState.suppression = Math.min(1f, ClientState.suppression + s.amount());
        ClientState.lastSuppressFrom = s.from();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            mc.level.playLocalSound(s.from().x, s.from().y, s.from().z, ModSounds.get("bullet_crack"), net.minecraft.sounds.SoundSource.PLAYERS,
                    0.9f, 0.85f + mc.level.random.nextFloat() * 0.3f, false);
        }
    }

    public static void score(SimplePackets.Score s) {
        long t = ClientState.now();
        ClientState.SCORES.addLast(new ClientState.ScorePopup(s.points(), s.label(), t));
        while (ClientState.SCORES.size() > 5) ClientState.SCORES.pollFirst();
        if (s.points() > 0) {
            ClientState.roundScore += s.points();
            ClientState.roundScoreTime = t;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.get("score_ping"), 1.2f, 0.35f));
        }
    }

    public static void killFeed(SimplePackets.KillFeed f) {
        ClientState.FEED.addLast(new ClientState.FeedLine(f, ClientState.now()));
        while (ClientState.FEED.size() > 6) ClientState.FEED.pollFirst();
    }

    public static void openDeploy(SimplePackets.OpenDeploy o) {
        ClientState.deployCountdown = o.delayTicks();
        Minecraft.getInstance().setScreen(new DeployScreen());
    }

    public static void openWarTable(SimplePackets.OpenWarTable o) {
        Minecraft.getInstance().setScreen(new WarTableScreen(o.pos()));
    }

    public static void progress(SimplePackets.Progress p) {
        ClientState.progress = p;
    }

    public static void shake(SimplePackets.Shake s) {
        ClientState.shake = Math.min(4f, ClientState.shake + s.intensity());
    }

    public static void downed(SimplePackets.Downed d) {
        ClientState.downed = d.downed();
        ClientState.downedTicks = d.ticksLeft();
    }

    public static void announce(SimplePackets.Announce a) {
        ClientState.announcement = new ClientState.Announcement(a.title(), a.subtitle(), a.style(), ClientState.now());
    }

    public static void match(MatchSnapshot s) {
        ClientState.match = s;
    }

    private ClientPacketHandler() {}
}
