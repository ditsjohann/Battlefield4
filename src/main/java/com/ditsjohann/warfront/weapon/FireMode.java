package com.ditsjohann.warfront.weapon;

public enum FireMode {
    AUTO("AUTO"), BURST("BURST"), SEMI("SEMI"), BOLT("BOLT");

    public final String label;

    FireMode(String label) { this.label = label; }
}
