package com.ditsjohann.warfront.weapon;

/**
 * A gun's stats after attachments are applied. Shared by the client (spread, recoil, zoom) and
 * the server (velocity, range, suppressed flag) so both sides agree on the weapon's behaviour.
 */
public record EffectiveStats(
        GunType type, Loadout loadout,
        float hipSpread, float adsSpread, float recoilUp, float recoilSide, float firstShotMult,
        float velocity, float rangeMult, float zoom, float adsSpeed, boolean suppressed, boolean bipod) {

    public static EffectiveStats of(GunType type, Loadout l) {
        GunType.Stats s = type.stats;
        float hip = s.hipSpread, ads = s.adsSpread, up = s.recoilUp, side = s.recoilSide, first = s.firstShotMult;
        float vel = s.velocity, range = 1f, adsSpeed = s.adsSpeed;
        switch (l.muzzle()) {
            case SUPPRESSOR -> { vel *= 0.85f; range = 0.8f; }
            case COMPENSATOR -> side *= 0.6f;
            case HEAVY_BARREL -> { ads *= 0.65f; up *= 1.3f; }
            default -> {}
        }
        switch (l.underbarrel()) {
            case VERTICAL_GRIP -> up *= 0.75f;
            case ANGLED_GRIP -> { adsSpeed *= 1.6f; first = 1f + (first - 1f) * 0.8f; }
            default -> {}
        }
        float zoom = l.optic().zoom;
        return new EffectiveStats(type, l, hip, ads, up, side, first, vel, range, zoom, adsSpeed,
                l.suppressed(), l.underbarrel() == Attachment.BIPOD);
    }

    public float damageAt(double distance) {
        return type.stats.damageAt(distance / rangeMult);
    }
}
