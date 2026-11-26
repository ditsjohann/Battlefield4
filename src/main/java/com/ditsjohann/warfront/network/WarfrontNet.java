package com.ditsjohann.warfront.network;

import com.ditsjohann.warfront.Warfront;
import com.ditsjohann.warfront.match.MatchSnapshot;
import com.ditsjohann.warfront.network.packets.SimplePackets.*;
import com.ditsjohann.warfront.network.packets.TracerPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;

import java.util.function.BiConsumer;

/**
 * The single WARFRONT play channel. Client-bound handlers are lambdas that call into
 * {@code client.ClientPacketHandler}, so no client class is loaded on a dedicated server.
 */
public final class WarfrontNet {
    public static final int PROTOCOL = 1;
    public static final SimpleChannel CHANNEL = ChannelBuilder.named(Warfront.id("main"))
            .networkProtocolVersion(PROTOCOL)
            .simpleChannel();

    private static boolean registered;

    public static void register() {
        if (registered) return;
        registered = true;
        // ---- client -> server
        toServer(Shoot.class, Shoot.CODEC, (m, c) -> ServerPacketHandler.shoot(m, c.getSender()));
        toServer(Action.class, Action.CODEC, (m, c) -> ServerPacketHandler.action(m, c.getSender()));
        toServer(Deploy.class, Deploy.CODEC, (m, c) -> ServerPacketHandler.deploy(m, c.getSender()));
        toServer(WarTable.class, WarTable.CODEC, (m, c) -> ServerPacketHandler.warTable(m, c.getSender()));
        toServer(VehicleInput.class, VehicleInput.CODEC, (m, c) -> ServerPacketHandler.vehicle(m, c.getSender()));
        // ---- server -> client
        toClient(TracerPacket.class, TracerPacket.CODEC, (m, c) -> com.ditsjohann.warfront.client.ClientPacketHandler.tracer(m));
        toClient(HitMarker.class, HitMarker.CODEC, (m, c) -> com.ditsjohann.warfront.client.ClientPacketHandler.hitMarker(m));
        toClient(Suppressed.class, Suppressed.CODEC, (m, c) -> com.ditsjohann.warfront.client.ClientPacketHandler.suppressed(m));
        toClient(Score.class, Score.CODEC, (m, c) -> com.ditsjohann.warfront.client.ClientPacketHandler.score(m));
        toClient(KillFeed.class, KillFeed.CODEC, (m, c) -> com.ditsjohann.warfront.client.ClientPacketHandler.killFeed(m));
        toClient(OpenDeploy.class, OpenDeploy.CODEC, (m, c) -> com.ditsjohann.warfront.client.ClientPacketHandler.openDeploy(m));
        toClient(OpenWarTable.class, OpenWarTable.CODEC, (m, c) -> com.ditsjohann.warfront.client.ClientPacketHandler.openWarTable(m));
        toClient(Progress.class, Progress.CODEC, (m, c) -> com.ditsjohann.warfront.client.ClientPacketHandler.progress(m));
        toClient(Shake.class, Shake.CODEC, (m, c) -> com.ditsjohann.warfront.client.ClientPacketHandler.shake(m));
        toClient(Downed.class, Downed.CODEC, (m, c) -> com.ditsjohann.warfront.client.ClientPacketHandler.downed(m));
        toClient(Announce.class, Announce.CODEC, (m, c) -> com.ditsjohann.warfront.client.ClientPacketHandler.announce(m));
        toClient(MatchSnapshot.class, MatchSnapshot.CODEC, (m, c) -> com.ditsjohann.warfront.client.ClientPacketHandler.match(m));
        CHANNEL.build();
    }

    private static <M> void toServer(Class<M> type, StreamCodec<FriendlyByteBuf, M> codec, BiConsumer<M, CustomPayloadEvent.Context> handler) {
        CHANNEL.messageBuilder(type, NetworkDirection.PLAY_TO_SERVER).codec(adapt(codec)).consumerMainThread((m, c) -> {
            if (c.getSender() != null) handler.accept(m, c);
        }).add();
    }

    private static <M> void toClient(Class<M> type, StreamCodec<FriendlyByteBuf, M> codec, BiConsumer<M, CustomPayloadEvent.Context> handler) {
        CHANNEL.messageBuilder(type, NetworkDirection.PLAY_TO_CLIENT).codec(adapt(codec)).consumerMainThread(handler).add();
    }

    private static <M> StreamCodec<RegistryFriendlyByteBuf, M> adapt(StreamCodec<FriendlyByteBuf, M> c) {
        return StreamCodec.of((buf, m) -> c.encode(buf, m), c::decode);
    }

    public static void toPlayer(ServerPlayer player, Object msg) {
        CHANNEL.send(msg, PacketDistributor.PLAYER.with(player));
    }

    public static void toServer(Object msg) {
        CHANNEL.send(msg, PacketDistributor.SERVER.noArg());
    }

    private WarfrontNet() {}
}
