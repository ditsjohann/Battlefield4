package com.ditsjohann.warfront.match;

import com.ditsjohann.warfront.block.CapturePointBlockEntity;
import com.ditsjohann.warfront.block.HqBeaconBlockEntity;
import com.ditsjohann.warfront.combat.Faction;
import com.ditsjohann.warfront.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a Conquest battlefield onto the existing terrain in front of a War Table: two
 * headquarters at either end, a line of zig-zagging capture points with sandbag fortifications,
 * and the Comms Tower set-piece beside the centre flag.
 */
public final class BattlefieldGenerator {
    public static final int FLAG_RADIUS = 9;
    private static final int SPACING = 42;
    private static final int[] FLAGS_BY_SIZE = {3, 5, 7};

    public static ConquestMatch generate(ServerLevel level, BlockPos table, Direction facing, int size, Faction near, int soldiers, int tickets) {
        RandomSource rnd = level.random;
        int n = FLAGS_BY_SIZE[Math.max(0, Math.min(2, size))];
        int ax = facing.getStepX(), az = facing.getStepZ();
        int px = -az, pz = ax;
        ConquestMatch m = new ConquestMatch();
        m.maxTickets = tickets;
        m.tickets[0] = tickets;
        m.tickets[1] = tickets;
        m.soldiersPerTeam = soldiers;

        int startOff = 30;
        BlockPos hqNear = surface(level, table.getX() + ax * startOff, table.getZ() + az * startOff);
        int farOff = startOff + 38 + (n - 1) * SPACING + 38;
        BlockPos hqFar = surface(level, table.getX() + ax * farOff, table.getZ() + az * farOff);
        m.hq[near.ordinal()] = buildHq(level, hqNear, near, facing, m);
        m.hq[near.enemy().ordinal()] = buildHq(level, hqFar, near.enemy(), facing.getOpposite(), m);

        List<BlockPos> sites = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int along = startOff + 38 + i * SPACING;
            int lateral = (i % 2 == 0 ? 1 : -1) * (8 + rnd.nextInt(6));
            if (i == n / 2) lateral = 0;
            int x = table.getX() + ax * along + px * lateral;
            int z = table.getZ() + az * along + pz * lateral;
            sites.add(surface(level, x, z));
        }
        int mid = n / 2;
        for (int i = 0; i < n; i++) {
            BlockPos center = sites.get(i);
            BlockPos flagPos = buildFlag(level, center, rnd);
            Objective o = new Objective((char) ('A' + i), flagPos, FLAG_RADIUS);
            if (i == 0) {
                o.owner = near;
                o.control = near == Faction.COALITION ? 1 : -1;
            } else if (i == n - 1) {
                o.owner = near.enemy();
                o.control = near.enemy() == Faction.COALITION ? 1 : -1;
            }
            if (level.getBlockEntity(flagPos) instanceof CapturePointBlockEntity be) be.setLetter(o.letter);
            if (i == mid) {
                o.hasTower = true;
                m.towerFlag = i;
                BlockPos towerAt = surface(level, center.getX() + px * 12 - 2, center.getZ() + pz * 12 - 2);
                m.towerBase = buildTower(level, towerAt);
            }
            m.flags.add(o);
        }
        // bounds with margin
        int minX = Math.min(m.hq[0].getX(), m.hq[1].getX()), maxX = Math.max(m.hq[0].getX(), m.hq[1].getX());
        int minZ = Math.min(m.hq[0].getZ(), m.hq[1].getZ()), maxZ = Math.max(m.hq[0].getZ(), m.hq[1].getZ());
        for (Objective o : m.flags) {
            minX = Math.min(minX, o.pos.getX());
            maxX = Math.max(maxX, o.pos.getX());
            minZ = Math.min(minZ, o.pos.getZ());
            maxZ = Math.max(maxZ, o.pos.getZ());
        }
        m.minX = minX - 40;
        m.maxX = maxX + 40;
        m.minZ = minZ - 40;
        m.maxZ = maxZ + 40;
        return m;
    }

    /** Surface position (first air block above ground, ignoring leaves). Loads the chunk if needed. */
    static BlockPos surface(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        // don't build on water: walk down through fluid
        BlockPos p = new BlockPos(x, y, z);
        while (!level.getFluidState(p.below()).isEmpty() && p.getY() > level.getMinBuildHeight() + 1) p = p.below();
        return p;
    }

    /** Levels and clears a circular pad; returns the pad height (top block y + 1). */
    private static int preparePad(ServerLevel level, BlockPos center, int radius, BlockState topA, BlockState topB, RandomSource rnd) {
        int[] heights = new int[(radius * 2 + 1) * (radius * 2 + 1)];
        int k = 0;
        for (int dx = -radius; dx <= radius; dx++)
            for (int dz = -radius; dz <= radius; dz++)
                heights[k++] = surface(level, center.getX() + dx, center.getZ() + dz).getY();
        java.util.Arrays.sort(heights);
        int y = heights[heights.length / 2];
        for (int dx = -radius - 2; dx <= radius + 2; dx++) {
            for (int dz = -radius - 2; dz <= radius + 2; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > radius + 2) continue;
                int x = center.getX() + dx, z = center.getZ() + dz;
                boolean inner = d <= radius;
                // clear vegetation and trees above the pad
                for (int h = 0; h < 14; h++) {
                    BlockPos q = new BlockPos(x, y + h, z);
                    BlockState s = level.getBlockState(q);
                    if (s.isAir()) continue;
                    if (s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS) || s.canBeReplaced() || s.is(BlockTags.FLOWERS) || s.getBlock() instanceof BushBlock
                            || s.getBlock() instanceof SnowLayerBlock || s.getBlock() instanceof VineBlock || inner && h < 5) {
                        level.setBlock(q, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
                if (!inner) continue;
                // fill below to the pad height
                for (int h = 1; h <= 4; h++) {
                    BlockPos q = new BlockPos(x, y - h, z);
                    if (level.getBlockState(q).canBeReplaced()) level.setBlock(q, Blocks.DIRT.defaultBlockState(), 2);
                }
                level.setBlock(new BlockPos(x, y - 1, z), rnd.nextFloat() < 0.65f ? topA : topB, 2);
            }
        }
        return y;
    }

    private static BlockPos buildFlag(ServerLevel level, BlockPos center, RandomSource rnd) {
        int y = preparePad(level, center, 7, Blocks.COARSE_DIRT.defaultBlockState(), Blocks.GRAVEL.defaultBlockState(), rnd);
        BlockPos flag = new BlockPos(center.getX(), y, center.getZ());
        level.setBlock(flag.below(), Blocks.POLISHED_ANDESITE.defaultBlockState(), 2);
        level.setBlock(flag, ModBlocks.CAPTURE_POINT.get().defaultBlockState(), 3);
        // sandbag arcs with gaps
        BlockState bag = ModBlocks.SANDBAG.get().defaultBlockState();
        double start = rnd.nextDouble() * Math.PI * 2;
        for (int arc = 0; arc < 4; arc++) {
            double a0 = start + arc * Math.PI / 2;
            double r = 5 + rnd.nextInt(2);
            int len = 3 + rnd.nextInt(3);
            for (int s = 0; s < len; s++) {
                double a = a0 + s * (1.0 / r);
                int x = (int) Math.round(center.getX() + Math.cos(a) * r);
                int z = (int) Math.round(center.getZ() + Math.sin(a) * r);
                level.setBlock(new BlockPos(x, y, z), bag, 2);
                if (rnd.nextFloat() < 0.6f) level.setBlock(new BlockPos(x, y + 1, z), bag, 2);
            }
        }
        // crates for cover and a resupply cabinet for whoever holds the flag
        for (int i = 0; i < 3; i++) {
            int x = center.getX() + rnd.nextInt(7) - 3, z = center.getZ() + rnd.nextInt(7) - 3;
            if (Math.abs(x - center.getX()) + Math.abs(z - center.getZ()) < 2) continue;
            BlockPos q = new BlockPos(x, y, z);
            if (level.getBlockState(q).isAir()) level.setBlock(q, rnd.nextBoolean() ? Blocks.BARREL.defaultBlockState() : Blocks.HAY_BLOCK.defaultBlockState(), 2);
        }
        BlockPos crate = new BlockPos(center.getX() + 2, y, center.getZ() - 2);
        level.setBlock(crate, ModBlocks.RESUPPLY_CRATE.get().defaultBlockState(), 3);
        return flag;
    }

    private static BlockPos buildHq(ServerLevel level, BlockPos center, Faction f, Direction toBattle, ConquestMatch m) {
        RandomSource rnd = level.random;
        int y = preparePad(level, center, 9, Blocks.POLISHED_ANDESITE.defaultBlockState(), Blocks.STONE_BRICKS.defaultBlockState(), rnd);
        BlockPos hq = new BlockPos(center.getX(), y, center.getZ());
        level.setBlock(hq, ModBlocks.HQ_BEACON.get().defaultBlockState(), 3);
        if (level.getBlockEntity(hq) instanceof HqBeaconBlockEntity be) be.setFaction(f);
        // perimeter: stone brick walls with sandbag-topped gaps facing the battle
        for (int dx = -9; dx <= 9; dx++) {
            for (int dz = -9; dz <= 9; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != 9) continue;
                boolean gap = Math.abs(dx) <= 2 || Math.abs(dz) <= 2;
                if (gap) continue;
                BlockPos q = new BlockPos(center.getX() + dx, y, center.getZ() + dz);
                level.setBlock(q, Blocks.STONE_BRICK_WALL.defaultBlockState(), 3);
                if (rnd.nextFloat() < 0.5f) level.setBlock(q.above(), ModBlocks.SANDBAG.get().defaultBlockState(), 2);
            }
        }
        // resupply cabinets
        level.setBlock(hq.offset(3, 0, 3), ModBlocks.RESUPPLY_CRATE.get().defaultBlockState(), 3);
        level.setBlock(hq.offset(-3, 0, -3), ModBlocks.RESUPPLY_CRATE.get().defaultBlockState(), 3);
        // vehicle pad toward the battle, with hazard markings
        BlockPos pad = hq.relative(toBattle, 5).relative(toBattle.getClockWise(), 4);
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                level.setBlock(pad.offset(dx, -1, dz), Blocks.YELLOW_CONCRETE.defaultBlockState(), 2);
        level.setBlock(pad.below(), ModBlocks.VEHICLE_PAD.get().defaultBlockState(), 3);
        for (int h = 0; h < 4; h++)
            for (int dx = -2; dx <= 2; dx++)
                for (int dz = -2; dz <= 2; dz++)
                    level.setBlock(pad.offset(dx, h, dz), Blocks.AIR.defaultBlockState(), 2);
        m.pads.add(new ConquestMatch.VehiclePad(pad, f));
        return hq.above();
    }

    /** The Comms Tower: concrete pillars, three floors, an internal ladder and an antenna mast. */
    private static BlockPos buildTower(ServerLevel level, BlockPos base) {
        RandomSource rnd = level.random;
        preparePad(level, base.offset(2, 0, 2), 4, Blocks.POLISHED_ANDESITE.defaultBlockState(), Blocks.GRAVEL.defaultBlockState(), rnd);
        int y0 = surface(level, base.getX() + 2, base.getZ() + 2).getY();
        BlockPos b = new BlockPos(base.getX(), y0, base.getZ());
        BlockState concrete = ModBlocks.REINFORCED_CONCRETE.get().defaultBlockState();
        int height = 21;
        for (int h = 0; h <= height; h++) {
            for (int dx = 0; dx <= 4; dx++) {
                for (int dz = 0; dz <= 4; dz++) {
                    BlockPos q = b.offset(dx, h, dz);
                    boolean corner = (dx == 0 || dx == 4) && (dz == 0 || dz == 4);
                    boolean floor = h > 0 && h % 7 == 0;
                    boolean core = dx == 2 && dz == 2;
                    boolean ladder = dx == 2 && dz == 1;
                    BlockState s = Blocks.AIR.defaultBlockState();
                    if (corner || core) s = concrete;
                    else if (floor && !ladder) s = concrete;
                    else if (ladder) s = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH);
                    else if (h > 0 && (h - 1) % 7 == 0 && h > 1 && (dx == 0 || dx == 4 || dz == 0 || dz == 4)) s = Blocks.IRON_BARS.defaultBlockState();
                    else if (h == 3 && (dx == 0 || dx == 4 || dz == 0 || dz == 4) && rnd.nextFloat() < 0.3f) s = Blocks.IRON_BARS.defaultBlockState();
                    level.setBlock(q, s, 2);
                }
            }
        }
        // railings on the floors
        for (int f = 1; f <= 3; f++) {
            int h = f * 7 + 1;
            for (int dx = 0; dx <= 4; dx++)
                for (int dz = 0; dz <= 4; dz++)
                    if ((dx == 0 || dx == 4 || dz == 0 || dz == 4) && !((dx == 0 || dx == 4) && (dz == 0 || dz == 4)))
                        level.setBlock(b.offset(dx, h, dz), Blocks.IRON_BARS.defaultBlockState(), 2);
        }
        // antenna + warning light
        for (int h = 1; h <= 5; h++) level.setBlock(b.offset(2, height + h, 2), Blocks.IRON_BARS.defaultBlockState(), 2);
        level.setBlock(b.offset(2, height + 6, 2), Blocks.REDSTONE_BLOCK.defaultBlockState(), 2);
        level.setBlock(b.offset(2, height + 7, 2), Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, true), 2);
        level.setBlock(b.offset(2, height + 8, 2), Blocks.LIGHTNING_ROD.defaultBlockState(), 2);
        level.setBlock(b.offset(2, 0, 2), ModBlocks.TOWER_CORE.get().defaultBlockState(), 3);
        return b;
    }

    private BattlefieldGenerator() {}
}
