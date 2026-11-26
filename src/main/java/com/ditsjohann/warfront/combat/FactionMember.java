package com.ditsjohann.warfront.combat;

import org.jetbrains.annotations.Nullable;

/** Implemented by entities that fight for a side (soldiers, vehicles, placed gadgets). */
public interface FactionMember {
    @Nullable Faction getFaction();
}
