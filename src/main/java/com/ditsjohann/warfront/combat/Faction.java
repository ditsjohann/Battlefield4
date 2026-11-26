package com.ditsjohann.warfront.combat;

import net.minecraft.ChatFormatting;

/** The two armies of WARFRONT. */
public enum Faction {
    COALITION("Iron Coalition", "IRC", 0x2F6FD8, ChatFormatting.BLUE, "coalition"),
    LEGION("Ember Legion", "EML", 0xD83A2E, ChatFormatting.RED, "legion");

    public final String displayName;
    public final String tag;
    public final int color;
    public final ChatFormatting chat;
    public final String skin;

    Faction(String displayName, String tag, int color, ChatFormatting chat, String skin) {
        this.displayName = displayName;
        this.tag = tag;
        this.color = color;
        this.chat = chat;
        this.skin = skin;
    }

    public Faction enemy() { return this == COALITION ? LEGION : COALITION; }

    public static Faction byId(int id) { return id == 1 ? LEGION : COALITION; }
}
