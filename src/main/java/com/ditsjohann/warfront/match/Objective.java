package com.ditsjohann.warfront.match;

import com.ditsjohann.warfront.combat.Faction;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import org.jetbrains.annotations.Nullable;

/**
 * A capture point. {@code control} runs from -1 (fully Ember Legion) to +1 (fully Iron Coalition).
 * A flag is neutralised when control crosses 0 and captured when it reaches either end.
 */
public final class Objective {
    public final char letter;
    public final BlockPos pos;
    public final int radius;
    public float control;
    @Nullable public Faction owner;
    public int countA, countB;
    public boolean hasTower;

    public Objective(char letter, BlockPos pos, int radius) {
        this.letter = letter;
        this.pos = pos;
        this.radius = radius;
    }

    public boolean contested() { return countA > 0 && countB > 0; }

    public int enemiesOf(Faction f) { return f == Faction.COALITION ? countB : countA; }

    public boolean safeSpawnFor(Faction f) {
        return owner == f && enemiesOf(f) == 0 && Math.abs(control) > 0.5f;
    }

    public boolean contains(double x, double y, double z) {
        double dx = x - (pos.getX() + 0.5), dz = z - (pos.getZ() + 0.5);
        return dx * dx + dz * dz <= radius * radius && Math.abs(y - pos.getY()) < 8;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("letter", String.valueOf(letter));
        t.put("pos", NbtUtils.writeBlockPos(pos));
        t.putInt("radius", radius);
        t.putFloat("control", control);
        t.putInt("owner", owner == null ? -1 : owner.ordinal());
        t.putBoolean("tower", hasTower);
        return t;
    }

    public static Objective load(CompoundTag t) {
        Objective o = new Objective(t.getString("letter").charAt(0), NbtUtils.readBlockPos(t, "pos").orElse(BlockPos.ZERO), t.getInt("radius"));
        o.control = t.getFloat("control");
        int ow = t.getInt("owner");
        o.owner = ow < 0 ? null : Faction.byId(ow);
        o.hasTower = t.getBoolean("tower");
        return o;
    }
}
