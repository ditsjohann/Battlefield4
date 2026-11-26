package com.ditsjohann.warfront.weapon;

public enum WeaponCategory {
    ASSAULT_RIFLE("Assault Rifle"),
    CARBINE("Carbine"),
    LMG("Light Machine Gun"),
    SNIPER("Sniper Rifle"),
    SHOTGUN("Shotgun"),
    PISTOL("Sidearm");

    public final String label;

    WeaponCategory(String label) { this.label = label; }
}
