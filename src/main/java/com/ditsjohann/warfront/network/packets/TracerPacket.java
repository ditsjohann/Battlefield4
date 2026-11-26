package com.ditsjohann.warfront.network.packets;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;

/** S2C: a round was fired; the client simulates its tracer. */
public record TracerPacket(int shooterId, Vec3 origin, Vec3 velocity, float gravity, int color, boolean suppressed) {
    public static final StreamCodec<FriendlyByteBuf, TracerPacket> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.shooterId);
                buf.writeVec3(p.origin);
                buf.writeVec3(p.velocity);
                buf.writeFloat(p.gravity);
                buf.writeInt(p.color);
                buf.writeBoolean(p.suppressed);
            },
            buf -> new TracerPacket(buf.readVarInt(), buf.readVec3(), buf.readVec3(), buf.readFloat(), buf.readInt(), buf.readBoolean()));
}
