package com.ditsjohann.warfront;

import net.minecraftforge.common.ForgeConfigSpec;

/** Server-side gameplay tuning (world/serverconfig/warfront-server.toml). */
public final class WarfrontConfig {
    private static final ForgeConfigSpec.Builder B = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.EnumValue<Destruction> DESTRUCTION;
    public static final ForgeConfigSpec.BooleanValue BULLETS_BREAK_GLASS;
    public static final ForgeConfigSpec.BooleanValue TANKS_CRUSH_BLOCKS;
    public static final ForgeConfigSpec.IntValue SOLDIERS_PER_TEAM;
    public static final ForgeConfigSpec.IntValue DEFAULT_TICKETS;
    public static final ForgeConfigSpec.DoubleValue AI_ACCURACY;
    public static final ForgeConfigSpec.DoubleValue DAMAGE_MULTIPLIER;
    public static final ForgeConfigSpec.BooleanValue FRIENDLY_FIRE;
    public static final ForgeConfigSpec.BooleanValue PLAYER_DOWNED_STATE;

    public enum Destruction { ALL, BATTLEFIELD_ONLY, NONE }

    static {
        B.push("destruction");
        DESTRUCTION = B.comment("Which explosions may destroy blocks: ALL, BATTLEFIELD_ONLY (inside an active Conquest area) or NONE")
                .defineEnum("explosionDestruction", Destruction.ALL);
        BULLETS_BREAK_GLASS = B.comment("Bullets shatter glass, panes, ice and leaves").define("bulletsBreakGlass", true);
        TANKS_CRUSH_BLOCKS = B.comment("Tanks flatten leaves, fences, glass, crops and other soft blocks they drive into").define("tanksCrushBlocks", true);
        B.pop();
        B.push("conquest");
        SOLDIERS_PER_TEAM = B.comment("Maximum AI soldiers alive per team during a Conquest match").defineInRange("soldiersPerTeam", 14, 0, 64);
        DEFAULT_TICKETS = B.comment("Starting tickets per team").defineInRange("tickets", 150, 10, 2000);
        AI_ACCURACY = B.comment("AI accuracy multiplier (higher = more accurate)").defineInRange("aiAccuracy", 1.0, 0.1, 3.0);
        FRIENDLY_FIRE = B.comment("Allow bullets and explosions to hurt teammates").define("friendlyFire", false);
        PLAYER_DOWNED_STATE = B.comment("Players in a match are downed (revivable) instead of killed outright").define("playerDownedState", true);
        B.pop();
        B.push("weapons");
        DAMAGE_MULTIPLIER = B.comment("Global bullet damage multiplier").defineInRange("damageMultiplier", 1.0, 0.1, 10.0);
        B.pop();
    }

    public static final ForgeConfigSpec SPEC = B.build();

    private WarfrontConfig() {}
}
