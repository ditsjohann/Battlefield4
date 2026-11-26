package com.ditsjohann.warfront.match;

import com.ditsjohann.warfront.weapon.GunType;

import java.util.List;

/** The four classes. Each kit makes the team depend on it for something. */
public enum Kit {
    ASSAULT("Assault", "Frontline medic. Heals with the medic bag and revives the fallen with the defibrillator.",
            List.of(GunType.AR17, GunType.KV12, GunType.SG12), 0xE8E8E8),
    ENGINEER("Engineer", "Anti-armour specialist. Destroys vehicles with rockets and keeps friendly armour alive with the repair tool.",
            List.of(GunType.CX9, GunType.SG12), 0xE0B020),
    SUPPORT("Support", "Suppression and supply. Keeps the squad stocked with ammo and demolishes defences with C4.",
            List.of(GunType.HMG240, GunType.SG12), 0x5FBF3A),
    RECON("Recon", "Eyes of the army. Long-range precision, binocular spotting and a mobile spawn beacon.",
            List.of(GunType.SR338, GunType.M7, GunType.SG12), 0x9A6AD0);

    public final String label;
    public final String description;
    public final List<GunType> primaries;
    public final int color;

    Kit(String label, String description, List<GunType> primaries, int color) {
        this.label = label;
        this.description = description;
        this.primaries = primaries;
        this.color = color;
    }

    public static Kit byId(int i) {
        Kit[] v = values();
        return v[Math.floorMod(i, v.length)];
    }
}
