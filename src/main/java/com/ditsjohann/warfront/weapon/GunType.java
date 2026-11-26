package com.ditsjohann.warfront.weapon;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static com.ditsjohann.warfront.weapon.Attachment.*;

/**
 * Every firearm in WARFRONT and its ballistic profile. Units: damage in half-hearts (20 = full
 * health), distances in blocks, velocity in blocks/tick, gravity in blocks/tick², angles in
 * degrees. The model follows the documented BF4 weapon model: radial spread + per-shot bloom +
 * decay, first-shot recoil multiplier, linear damage falloff, and projectile drop.
 */
public enum GunType {
    AR17("ar17", "AR-17 Warden", WeaponCategory.ASSAULT_RIFLE, 0, new Stats()
            .modes(FireMode.AUTO, FireMode.SEMI).rpm(720).mag(30, 120).reload(46, 58)
            .damage(5.0f, 3.6f, 12, 50).ballistics(18, 0.025f)
            .spread(2.6f, 0.22f, 0.12f, 1.6f, 0.2f).recoil(0.55f, 0.32f, 1.9f)
            .sound("rifle_shot").optics(RED_DOT, IRON_SIGHTS, RED_DOT, HOLO, PRISM_4X)
            .visual(0.95f, 0xFFD27A)),
    KV12("kv12", "KV-12 Sabre", WeaponCategory.ASSAULT_RIFLE, 2, new Stats()
            .modes(FireMode.AUTO, FireMode.SEMI).rpm(600).mag(25, 100).reload(48, 62)
            .damage(5.7f, 4.2f, 14, 55).ballistics(19, 0.025f)
            .spread(2.8f, 0.24f, 0.16f, 1.8f, 0.18f).recoil(0.78f, 0.38f, 1.8f)
            .sound("rifle_shot").pitch(0.88f).optics(IRON_SIGHTS, IRON_SIGHTS, RED_DOT, HOLO, PRISM_4X)
            .visual(1.0f, 0xFFC060)),
    CX9("cx9", "CX-9 Viper", WeaponCategory.CARBINE, 0, new Stats()
            .modes(FireMode.AUTO, FireMode.BURST, FireMode.SEMI).rpm(860).mag(30, 150).reload(40, 52)
            .damage(4.3f, 3.0f, 8, 40).ballistics(15, 0.028f)
            .spread(2.1f, 0.3f, 0.1f, 1.5f, 0.24f).recoil(0.44f, 0.4f, 1.7f)
            .sound("carbine_shot").optics(RED_DOT, IRON_SIGHTS, RED_DOT, HOLO)
            .visual(0.78f, 0xFFE08A)),
    HMG240("hmg240", "HMG-240 Bulwark", WeaponCategory.LMG, 0, new Stats()
            .modes(FireMode.AUTO).rpm(620).mag(100, 200).reload(110, 130)
            .damage(5.0f, 3.8f, 12, 55).ballistics(19, 0.024f)
            .spread(3.8f, 0.34f, 0.08f, 2.4f, 0.14f).recoil(0.6f, 0.55f, 1.5f).moveMult(2.2f)
            .suppression(0.16f).adsSpeed(0.6f).sound("lmg_shot").optics(RED_DOT, IRON_SIGHTS, RED_DOT, HOLO, PRISM_4X)
            .visual(1.1f, 0xFF9A50)),
    SR338("sr338", "SR-338 Longshot", WeaponCategory.SNIPER, 0, new Stats()
            .modes(FireMode.BOLT).rpm(46).mag(5, 30).reload(70, 92)
            .damage(19f, 15f, 40, 130).headMult(2.5f).ballistics(30, 0.03f)
            .spread(6f, 0.0f, 0f, 0f, 0.5f).recoil(3.5f, 0.6f, 1f).suppression(0.35f).adsSpeed(0.7f)
            .sound("sniper_shot").optics(SNIPER_8X, IRON_SIGHTS, PRISM_4X, SNIPER_8X)
            .visual(1.4f, 0xFFF4C0)),
    M7("m7", "M-7 Marksman", WeaponCategory.SNIPER, 3, new Stats()
            .modes(FireMode.SEMI).rpm(320).mag(10, 60).reload(52, 66)
            .damage(9f, 7f, 20, 70).ballistics(26, 0.028f)
            .spread(3.5f, 0.12f, 0.35f, 2.0f, 0.1f).recoil(1.25f, 0.4f, 1.4f).suppression(0.2f)
            .sound("sniper_shot").pitch(1.25f).optics(PRISM_4X, IRON_SIGHTS, RED_DOT, HOLO, PRISM_4X, SNIPER_8X)
            .visual(1.2f, 0xFFF0B0)),
    SG12("sg12", "SG-12 Breaker", WeaponCategory.SHOTGUN, 1, new Stats()
            .modes(FireMode.SEMI).rpm(75).mag(7, 35).reload(64, 76).pellets(9)
            .damage(2.1f, 0.6f, 6, 22).ballistics(12, 0.04f)
            .spread(3.8f, 3.0f, 0f, 0f, 0.5f).recoil(2.6f, 0.8f, 1f).suppression(0.05f)
            .sound("shotgun_shot").optics(IRON_SIGHTS, IRON_SIGHTS, RED_DOT)
            .visual(0.9f, 0xFFB070)),
    P12("p12", "P-12 Hornet", WeaponCategory.PISTOL, 0, new Stats()
            .modes(FireMode.SEMI).rpm(420).mag(15, 60).reload(30, 38)
            .damage(4.0f, 2.6f, 8, 30).ballistics(12, 0.03f)
            .spread(1.4f, 0.35f, 0.3f, 1.4f, 0.3f).recoil(0.8f, 0.35f, 1.2f).adsSpeed(1.6f)
            .sound("pistol_shot").optics(IRON_SIGHTS, IRON_SIGHTS, RED_DOT)
            .visual(0.45f, 0xFFE0A0));

    public final String id;
    public final String displayName;
    public final WeaponCategory category;
    public final int rankRequired;
    public final Stats stats;

    GunType(String id, String displayName, WeaponCategory category, int rankRequired, Stats stats) {
        this.id = id;
        this.displayName = displayName;
        this.category = category;
        this.rankRequired = rankRequired;
        this.stats = stats;
    }

    public Loadout defaultLoadout() {
        return new Loadout(stats.defaultOptic, NO_MUZZLE, NO_GRIP);
    }

    public boolean allows(Attachment a) {
        return switch (a.slot) {
            case OPTIC -> stats.optics.contains(a);
            case MUZZLE -> category != WeaponCategory.SHOTGUN || a != SUPPRESSOR;
            case UNDERBARREL -> category != WeaponCategory.PISTOL && (a != BIPOD
                    || category == WeaponCategory.LMG || category == WeaponCategory.SNIPER || a == NO_GRIP);
        };
    }

    public List<Attachment> options(Attachment.Slot slot) {
        return java.util.Arrays.stream(Attachment.values()).filter(a -> a.slot == slot && allows(a)).toList();
    }

    public static GunType byId(String id) {
        for (GunType t : values()) if (t.id.equals(id)) return t;
        return AR17;
    }

    /** Mutable builder for weapon stats; frozen once assigned to an enum constant. */
    public static final class Stats {
        public Set<FireMode> modes = EnumSet.of(FireMode.SEMI);
        public FireMode[] modeOrder = {FireMode.SEMI};
        public int rpm = 600, magSize = 30, reserve = 120, reloadTicks = 40, reloadEmptyTicks = 50, pellets = 1, burst = 3;
        public float damageMax = 5, damageMin = 3, falloffStart = 10, falloffEnd = 40, headMult = 2.0f;
        public float velocity = 16, gravity = 0.025f;
        public float hipSpread = 2.5f, adsSpread = 0.25f, spreadPerShot = 0.1f, spreadMax = 1.5f, spreadDecay = 0.2f;
        public float recoilUp = 0.5f, recoilSide = 0.3f, firstShotMult = 1.5f, moveMult = 1.6f;
        public float suppression = 0.1f, adsSpeed = 1.0f, pitch = 1.0f;
        public String sound = "rifle_shot";
        public Attachment defaultOptic = IRON_SIGHTS;
        public Set<Attachment> optics = EnumSet.of(IRON_SIGHTS);
        public float muzzleLength = 1.0f;
        public int tracerColor = 0xFFD27A;

        Stats modes(FireMode... m) { modes = EnumSet.copyOf(List.of(m)); modeOrder = m; return this; }
        Stats rpm(int v) { rpm = v; return this; }
        Stats mag(int m, int r) { magSize = m; reserve = r; return this; }
        Stats reload(int t, int empty) { reloadTicks = t; reloadEmptyTicks = empty; return this; }
        Stats damage(float max, float min, float start, float end) { damageMax = max; damageMin = min; falloffStart = start; falloffEnd = end; return this; }
        Stats headMult(float v) { headMult = v; return this; }
        Stats ballistics(float vel, float grav) { velocity = vel; gravity = grav; return this; }
        Stats spread(float hip, float ads, float perShot, float max, float decay) { hipSpread = hip; adsSpread = ads; spreadPerShot = perShot; spreadMax = max; spreadDecay = decay; return this; }
        Stats recoil(float up, float side, float first) { recoilUp = up; recoilSide = side; firstShotMult = first; return this; }
        Stats moveMult(float v) { moveMult = v; return this; }
        Stats suppression(float v) { suppression = v; return this; }
        Stats adsSpeed(float v) { adsSpeed = v; return this; }
        Stats pellets(int v) { pellets = v; return this; }
        Stats pitch(float v) { pitch = v; return this; }
        Stats sound(String s) { sound = s; return this; }
        Stats optics(Attachment def, Attachment... allowed) { defaultOptic = def; optics = EnumSet.copyOf(List.of(allowed)); return this; }
        Stats visual(float muzzle, int tracer) { muzzleLength = muzzle; tracerColor = tracer; return this; }

        /** Ticks between shots (fractional). */
        public float shotInterval() { return 1200f / rpm; }

        public float damageAt(double distance) {
            if (distance <= falloffStart) return damageMax;
            if (distance >= falloffEnd) return damageMin;
            float t = (float) ((distance - falloffStart) / (falloffEnd - falloffStart));
            return damageMax + (damageMin - damageMax) * t;
        }
    }
}
