package com.ditsjohann.warfront.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Shared styling for WARFRONT screens: dark glass panels with an amber accent. */
final class UiKit {
    static final int BG = 0xD00C1014, PANEL = 0xC0141A20, HOVER = 0xE0202A34, SELECTED = 0xF0283848, ACCENT = 0xFFF2A33A, TEXT = 0xFFE8EEF2,
            DIM = 0xFF8C98A4, LOCKED = 0xFF5A6068, BLUE = 0xFF2F6FD8, RED = 0xFFD83A2E;

    static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, PANEL);
        g.fill(x, y, x + w, y + 1, 0x40FFFFFF);
        g.fill(x, y + h - 1, x + w, y + h, 0x40000000);
    }

    static void header(GuiGraphics g, Font f, String text, int x, int y) {
        g.drawString(f, text, x, y, ACCENT, false);
        g.fill(x, y + 10, x + f.width(text), y + 11, 0x80F2A33A);
    }

    static boolean button(GuiGraphics g, Font f, String text, int x, int y, int w, int h, int mx, int my, boolean selected, boolean enabled, int stripe) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
        g.fill(x, y, x + w, y + h, !enabled ? 0x90101418 : selected ? SELECTED : hover ? HOVER : PANEL);
        if (stripe != 0) g.fill(x, y, x + 3, y + h, stripe);
        if (selected) g.fill(x, y + h - 1, x + w, y + h, ACCENT);
        g.drawString(f, text, x + (stripe != 0 ? 8 : w / 2 - f.width(text) / 2), y + h / 2 - 4, enabled ? TEXT : LOCKED, false);
        return hover && enabled;
    }

    static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    static void bar(GuiGraphics g, Font f, String label, float value, int x, int y, int w) {
        g.drawString(f, label, x, y, DIM, false);
        int bx = x + 70, bw = w - 70;
        g.fill(bx, y + 2, bx + bw, y + 6, 0x40FFFFFF);
        g.fill(bx, y + 2, bx + Math.round(bw * Math.max(0, Math.min(1, value))), y + 6, ACCENT);
    }

    private UiKit() {}
}
