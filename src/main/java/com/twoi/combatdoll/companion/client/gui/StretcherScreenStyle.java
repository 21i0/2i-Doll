package com.twoi.combatdoll.companion.client.gui;


import net.minecraft.client.gui.GuiGraphics;

/** Shared palette and panels matching Useless Mod's current button-based G screen. */
public final class StretcherScreenStyle {
    public static final int PANEL_COLOR = 0xFFCBCCD4;
    public static final int HIGHLIGHT_COLOR = 0xFFF2F2F2;
    public static final int SLOT_COLOR = 0xFFADB0C4;
    public static final int SLOT_SHADOW_COLOR = 0xFF9A9FB4;
    public static final int TEXT_COLOR = 0xFF413F54;
    public static final int MUTED_TEXT_COLOR = 0xFF878FA5;
    public static final int SUBTLE_TEXT_COLOR = 0xFF6D7287;
    public static final int ACTIVE_COLOR = 0xFF517497;
    public static final int SUCCESS_COLOR = 0xFF3B7652;
    public static final int WARNING_COLOR = 0xFF9A6B18;

    private StretcherScreenStyle() {
    }

    public static void drawPanel(GuiGraphics graphics, int left, int top, int width, int height) {
        graphics.fill(left, top, left+width, top+height, 0xFF666777);
        graphics.fill(left+1, top+1, left+width-1, top+height-1, HIGHLIGHT_COLOR);
        graphics.fill(left+2, top+2, left+width-2, top+height-2, PANEL_COLOR);
    }

    public static void drawInset(GuiGraphics graphics, int left, int top, int right, int bottom) {
        graphics.fill(left, top, right, bottom, HIGHLIGHT_COLOR);
        graphics.fill(left + 1, top + 1, right - 1, bottom - 1, PANEL_COLOR);
    }
}
