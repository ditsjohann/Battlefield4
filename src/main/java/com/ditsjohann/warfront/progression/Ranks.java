package com.ditsjohann.warfront.progression;

import com.ditsjohann.warfront.weapon.Attachment;
import com.ditsjohann.warfront.weapon.GunType;

import java.util.ArrayList;
import java.util.List;

/** Rank ladder. XP comes from score; each rank unlocks weapons and attachments. */
public final class Ranks {
    public static final String[] NAMES = {
            "Recruit", "Private", "Private First Class", "Lance Corporal", "Corporal", "Sergeant",
            "Staff Sergeant", "Master Sergeant", "Warrant Officer", "Lieutenant", "Captain", "Major", "Colonel", "General"
    };
    public static final String[] SHORT = {"RCT", "PVT", "PFC", "LCPL", "CPL", "SGT", "SSG", "MSG", "WO", "LT", "CPT", "MAJ", "COL", "GEN"};

    /** Cumulative XP required for each rank. */
    public static int xpFor(int rank) {
        if (rank <= 0) return 0;
        // 1000, 2500, 4500, 7000 ... grows ~quadratically
        return 1000 * rank + 250 * rank * (rank - 1);
    }

    public static int rankFor(int xp) {
        int r = 0;
        while (r + 1 < NAMES.length && xp >= xpFor(r + 1)) r++;
        return r;
    }

    public static String name(int rank) { return NAMES[Math.max(0, Math.min(rank, NAMES.length - 1))]; }

    public static String shortName(int rank) { return SHORT[Math.max(0, Math.min(rank, SHORT.length - 1))]; }

    public static List<String> unlocksAt(int rank) {
        List<String> out = new ArrayList<>();
        for (GunType g : GunType.values()) if (g.rankRequired == rank && rank > 0) out.add(g.displayName);
        for (Attachment a : Attachment.values()) if (a.rankRequired == rank && rank > 0) out.add(a.label);
        return out;
    }

    private Ranks() {}
}
