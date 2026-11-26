package com.ditsjohann.warfront.weapon;

/**
 * Weapon attachments. Each trades one stat for another, in the spirit of BF4 (the heavy barrel
 * tightens spread but adds recoil; the suppressor hides you from the minimap but costs range).
 * Like BF4, attachments unlock per weapon: {@link #killsRequired} kills with that gun.
 */
public enum Attachment {
    // ---- optics
    IRON_SIGHTS(Slot.OPTIC, "Iron Sights", 0, 1.15f),
    RED_DOT(Slot.OPTIC, "Reflex Sight", 0, 1.35f),
    HOLO(Slot.OPTIC, "Holo 2x", 10, 2.0f),
    PRISM_4X(Slot.OPTIC, "Prism 4x", 25, 4.0f),
    SNIPER_8X(Slot.OPTIC, "Hunter 8x", 20, 8.0f),
    // ---- muzzle
    NO_MUZZLE(Slot.MUZZLE, "None", 0, 1),
    SUPPRESSOR(Slot.MUZZLE, "Suppressor", 60, 1),
    COMPENSATOR(Slot.MUZZLE, "Compensator", 5, 1),
    HEAVY_BARREL(Slot.MUZZLE, "Heavy Barrel", 35, 1),
    // ---- underbarrel
    NO_GRIP(Slot.UNDERBARREL, "None", 0, 1),
    VERTICAL_GRIP(Slot.UNDERBARREL, "Vertical Grip", 3, 1),
    ANGLED_GRIP(Slot.UNDERBARREL, "Angled Grip", 45, 1),
    BIPOD(Slot.UNDERBARREL, "Bipod", 15, 1);

    public enum Slot { OPTIC, MUZZLE, UNDERBARREL }

    public final Slot slot;
    public final String label;
    public final int killsRequired;
    /** For optics: FOV zoom factor while aiming. */
    public final float zoom;

    Attachment(Slot slot, String label, int killsRequired, float zoom) {
        this.slot = slot;
        this.label = label;
        this.killsRequired = killsRequired;
        this.zoom = zoom;
    }

    public String describe() {
        return switch (this) {
            case IRON_SIGHTS -> "Clear view, minimal zoom";
            case RED_DOT -> "Fast target acquisition";
            case HOLO -> "Mid-range magnification";
            case PRISM_4X -> "Long-range magnification";
            case SNIPER_8X -> "Extreme magnification; scope glint";
            case NO_MUZZLE, NO_GRIP -> "Standard configuration";
            case SUPPRESSOR -> "Hidden from enemy radar; -15% velocity, -20% range";
            case COMPENSATOR -> "-40% horizontal recoil";
            case HEAVY_BARREL -> "-35% ADS spread, +30% vertical recoil";
            case VERTICAL_GRIP -> "-25% vertical recoil";
            case ANGLED_GRIP -> "+60% aim speed, -20% first-shot recoil";
            case BIPOD -> "-70% recoil while crouched";
        };
    }

    /** Unlocked with enough kills on {@code gun}; a gun's factory attachments are always available. */
    public boolean unlockedFor(GunType gun, int weaponKills) {
        return weaponKills >= killsRequired || gun.defaultLoadout().get(slot) == this;
    }

    public static Attachment byOrdinal(int i) {
        Attachment[] v = values();
        return i >= 0 && i < v.length ? v[i] : IRON_SIGHTS;
    }
}
