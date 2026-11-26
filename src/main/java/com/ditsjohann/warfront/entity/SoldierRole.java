package com.ditsjohann.warfront.entity;

import com.ditsjohann.warfront.weapon.GunType;
import net.minecraft.util.RandomSource;

/** AI soldier classes, mirroring the player kits. */
public enum SoldierRole {
    ASSAULT("assault", new GunType[]{GunType.AR17, GunType.AR17, GunType.KV12}),
    ENGINEER("engineer", new GunType[]{GunType.CX9, GunType.CX9, GunType.SG12}),
    SUPPORT("support", new GunType[]{GunType.HMG240}),
    RECON("recon", new GunType[]{GunType.SR338, GunType.M7});

    public final String skin;
    public final GunType[] guns;

    SoldierRole(String skin, GunType[] guns) {
        this.skin = skin;
        this.guns = guns;
    }

    public GunType pickGun(RandomSource r) {
        return guns[r.nextInt(guns.length)];
    }

    /** Squad composition: leader is assault, then support, then a mix of engineer/recon/assault. */
    public static SoldierRole pick(RandomSource r, int indexInSquad) {
        return switch (indexInSquad) {
            case 0 -> ASSAULT;
            case 1 -> SUPPORT;
            case 2 -> r.nextFloat() < 0.6f ? ENGINEER : ASSAULT;
            default -> r.nextFloat() < 0.55f ? RECON : ENGINEER;
        };
    }

    public static SoldierRole byId(int i) {
        SoldierRole[] v = values();
        return v[Math.floorMod(i, v.length)];
    }
}
