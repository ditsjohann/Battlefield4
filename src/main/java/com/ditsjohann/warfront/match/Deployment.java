package com.ditsjohann.warfront.match;

import com.ditsjohann.warfront.Warfront;
import com.ditsjohann.warfront.combat.DownedState;
import com.ditsjohann.warfront.combat.Faction;
import com.ditsjohann.warfront.entity.SoldierEntity;
import com.ditsjohann.warfront.network.WarfrontNet;
import com.ditsjohann.warfront.network.packets.SimplePackets;
import com.ditsjohann.warfront.progression.PlayerProgress;
import com.ditsjohann.warfront.registry.ModComponents;
import com.ditsjohann.warfront.registry.ModItems;
import com.ditsjohann.warfront.registry.ModSounds;
import com.ditsjohann.warfront.weapon.Attachment;
import com.ditsjohann.warfront.weapon.GunType;
import com.ditsjohann.warfront.weapon.Loadout;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Unit;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/** Deploy screen requests, kit hand-out and respawn flow. */
@Mod.EventBusSubscriber(modid = Warfront.MODID)
public final class Deployment {
    public static final int SPAWN_HQ = 0, SPAWN_FLAG = 1, SPAWN_BEACON = 2, SPAWN_SQUAD = 3;

    public static void deploy(ServerPlayer p, SimplePackets.Deploy req) {
        ServerLevel level = p.serverLevel();
        ConquestMatch m = MatchManager.active(level);
        if (m == null || m.state == ConquestMatch.ENDED) return;
        ConquestMatch.Participant pt = m.participant(p);
        if (pt == null || !p.isAlive()) return;
        Faction f = pt.faction;
        Vec3 spawn = resolveSpawn(level, m, pt, req.spawnType(), req.spawnIndex());
        if (spawn == null) {
            WarfrontNet.toPlayer(p, new SimplePackets.Score(0, "SPAWN POINT UNAVAILABLE"));
            WarfrontNet.toPlayer(p, new SimplePackets.OpenDeploy(0));
            return;
        }
        Kit kit = Kit.byId(req.kit());
        int rank = PlayerProgress.rank(p);
        GunType gun = GunType.byId(req.gun());
        if (!kit.primaries.contains(gun) || (gun.rankRequired > rank && !p.isCreative())) gun = kit.primaries.get(0);
        Loadout l = sanitize(gun, req.loadout(), PlayerProgress.weaponKills(p, gun), p.isCreative());
        DownedState.clear(p, false);
        stripKit(p);
        giveKit(p, kit, gun, l);
        BlockPos at = safeNear(level, BlockPos.containing(spawn), req.spawnType() == SPAWN_SQUAD ? 2 : 4);
        p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        p.setHealth(p.getMaxHealth());
        p.getFoodData().eat(20, 1f);
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 4, false, false));
        p.fallDistance = 0;
        pt.deployed = true;
        pt.lastKit = kit.ordinal();
        pt.lastGun = gun.id;
        level.playSound(null, at, ModSounds.get("deploy"), SoundSource.PLAYERS, 0.8f, 1f);
        WarfrontNet.toPlayer(p, new SimplePackets.Announce("DEPLOYED", kit.label + " | " + gun.displayName, 0));
    }

    @Nullable
    private static Vec3 resolveSpawn(ServerLevel level, ConquestMatch m, ConquestMatch.Participant pt, int type, int index) {
        Faction f = pt.faction;
        switch (type) {
            case SPAWN_FLAG -> {
                if (index < 0 || index >= m.flags.size()) return null;
                Objective o = m.flags.get(index);
                return o.safeSpawnFor(f) ? Vec3.atBottomCenterOf(o.pos) : null;
            }
            case SPAWN_BEACON -> {
                if (index < 0 || index >= m.beacons.size()) return null;
                ConquestMatch.SpawnBeacon b = m.beacons.get(index);
                return b.faction() == f ? Vec3.atBottomCenterOf(b.pos()) : null;
            }
            case SPAWN_SQUAD -> {
                for (SoldierEntity s : level.getEntitiesOfClass(SoldierEntity.class,
                        new AABB(m.minX - 64, level.getMinBuildHeight(), m.minZ - 64, m.maxX + 64, level.getMaxBuildHeight(), m.maxZ + 64),
                        s -> s.getSquadId() == pt.squadId && s.isAlive() && !s.isDowned() && s.getSuppression() < 0.3f)) {
                    return s.position();
                }
                return null;
            }
            default -> {
                return Vec3.atBottomCenterOf(m.hq(f));
            }
        }
    }

    public static Loadout sanitize(GunType gun, Loadout l, int weaponKills, boolean creative) {
        Loadout out = gun.defaultLoadout();
        for (Attachment.Slot slot : Attachment.Slot.values()) {
            Attachment a = l.get(slot);
            if (a.slot == slot && gun.allows(a) && (creative || a.unlockedFor(gun, weaponKills))) out = out.with(a);
        }
        return out;
    }

    public static void giveKit(ServerPlayer p, Kit kit, GunType primary, Loadout loadout) {
        ItemStack gun = new ItemStack(ModItems.gun(primary));
        gun.set(ModComponents.LOADOUT.get(), loadout);
        give(p, gun);
        give(p, new ItemStack(ModItems.gun(GunType.P12)));
        switch (kit) {
            case ASSAULT -> {
                give(p, charged(ModItems.MEDIC_BAG, 2));
                give(p, new ItemStack(ModItems.DEFIBRILLATOR.get()));
                give(p, charged(ModItems.FRAG_GRENADE, 2));
            }
            case ENGINEER -> {
                give(p, charged(ModItems.ROCKET_LAUNCHER, 5));
                give(p, new ItemStack(ModItems.REPAIR_TOOL.get()));
                give(p, charged(ModItems.FRAG_GRENADE, 1));
            }
            case SUPPORT -> {
                give(p, charged(ModItems.AMMO_BAG, 2));
                give(p, charged(ModItems.C4, 3));
                give(p, charged(ModItems.FRAG_GRENADE, 2));
            }
            case RECON -> {
                give(p, charged(ModItems.SPAWN_BEACON, 1));
                give(p, new ItemStack(ModItems.FIELD_BINOCULARS.get()));
                give(p, charged(ModItems.SMOKE_GRENADE, 2));
            }
        }
        ItemStack bags = new ItemStack(ModItems.SANDBAG.get(), kit == Kit.ENGINEER ? 24 : 12);
        give(p, bags);
        p.getInventory().selected = 0;
    }

    private static ItemStack charged(Supplier<? extends Item> item, int charges) {
        ItemStack s = new ItemStack(item.get());
        s.set(ModComponents.CHARGES.get(), charges);
        return s;
    }

    private static void give(ServerPlayer p, ItemStack s) {
        s.set(ModComponents.KIT_ITEM.get(), Unit.INSTANCE);
        // kit fills the hotbar from slot 0; displaced items go to the main inventory
        for (int i = 0; i < 9; i++) {
            if (p.getInventory().getItem(i).isEmpty()) {
                p.getInventory().setItem(i, s);
                return;
            }
        }
        if (!p.getInventory().add(s)) p.drop(s, false);
    }

    /** Removes every kit item from the player. */
    public static void stripKit(ServerPlayer p) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).has(ModComponents.KIT_ITEM.get())) inv.setItem(i, ItemStack.EMPTY);
        }
    }

    /** Finds a standable surface spot near {@code center}. */
    public static BlockPos safeNear(ServerLevel level, BlockPos center, int radius) {
        for (int attempt = 0; attempt < 16; attempt++) {
            int x = center.getX() + (attempt == 0 ? 0 : level.random.nextInt(radius * 2 + 1) - radius);
            int z = center.getZ() + (attempt == 0 ? 0 : level.random.nextInt(radius * 2 + 1) - radius);
            // prefer a spot near the requested height (inside structures), else the surface
            for (int dy = 2; dy >= -3; dy--) {
                BlockPos p = new BlockPos(x, center.getY() + dy, z);
                if (standable(level, p)) return p;
            }
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos p = new BlockPos(x, y, z);
            if (standable(level, p) && Math.abs(y - center.getY()) < 12) return p;
        }
        return center;
    }

    private static boolean standable(ServerLevel level, BlockPos p) {
        BlockState below = level.getBlockState(p.below());
        return below.isFaceSturdy(level, p.below(), net.minecraft.core.Direction.UP)
                && level.getBlockState(p).getCollisionShape(level, p).isEmpty()
                && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()
                && level.getFluidState(p).isEmpty();
    }

    /** Respawning inside a match puts you at HQ with the deploy screen open after a short timer. */
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.isEndConquered()) return;
        ConquestMatch m = MatchManager.active(p.serverLevel());
        if (m == null || m.state == ConquestMatch.ENDED) return;
        ConquestMatch.Participant pt = m.participant(p);
        if (pt == null) return;
        pt.deployed = false;
        BlockPos hq = safeNear(p.serverLevel(), m.hq(pt.faction), 3);
        p.teleportTo(hq.getX() + 0.5, hq.getY(), hq.getZ() + 0.5);
        WarfrontNet.toPlayer(p, new SimplePackets.OpenDeploy(100));
    }

    /** True if {@code e} is close to one of its team's spawn sources (used by AI to avoid spawn camping). */
    public static boolean nearHq(ConquestMatch m, Entity e, Faction f) {
        return e.blockPosition().distSqr(m.hq(f)) < ConquestMatch.HQ_RADIUS * ConquestMatch.HQ_RADIUS;
    }

    private Deployment() {}
}
