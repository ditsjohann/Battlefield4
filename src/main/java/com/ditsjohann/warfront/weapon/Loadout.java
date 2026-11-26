package com.ditsjohann.warfront.weapon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** The attachments mounted on a single gun (stored as a data component). */
public record Loadout(Attachment optic, Attachment muzzle, Attachment underbarrel) {
    public static final Codec<Attachment> ATTACHMENT_CODEC = Codec.INT.xmap(Attachment::byOrdinal, Attachment::ordinal);
    public static final Codec<Loadout> CODEC = RecordCodecBuilder.create(i -> i.group(
            ATTACHMENT_CODEC.fieldOf("optic").forGetter(Loadout::optic),
            ATTACHMENT_CODEC.fieldOf("muzzle").forGetter(Loadout::muzzle),
            ATTACHMENT_CODEC.fieldOf("underbarrel").forGetter(Loadout::underbarrel)
    ).apply(i, Loadout::new));
    private static final StreamCodec<ByteBuf, Attachment> ATT_STREAM = ByteBufCodecs.VAR_INT.map(Attachment::byOrdinal, Attachment::ordinal);
    public static final StreamCodec<ByteBuf, Loadout> STREAM_CODEC = StreamCodec.composite(
            ATT_STREAM, Loadout::optic, ATT_STREAM, Loadout::muzzle, ATT_STREAM, Loadout::underbarrel, Loadout::new);

    public Loadout with(Attachment a) {
        return switch (a.slot) {
            case OPTIC -> new Loadout(a, muzzle, underbarrel);
            case MUZZLE -> new Loadout(optic, a, underbarrel);
            case UNDERBARREL -> new Loadout(optic, muzzle, a);
        };
    }

    public Attachment get(Attachment.Slot slot) {
        return switch (slot) {
            case OPTIC -> optic;
            case MUZZLE -> muzzle;
            case UNDERBARREL -> underbarrel;
        };
    }

    public boolean suppressed() { return muzzle == Attachment.SUPPRESSOR; }
}
