package com.twoi.combatdoll.companion.client.gui;

/** Pixel geometry only. Artwork may scale freely; bitmap glyphs use whole screen pixels. */
public record CanvasTextLayout(double guiScale, double canvasScale, double left, double top, int pixelScale) {
    public CanvasTextLayout {
        if (!Double.isFinite(guiScale) || !Double.isFinite(canvasScale) || !Double.isFinite(left)
                || !Double.isFinite(top) || guiScale <= 0 || canvasScale <= 0 || pixelScale < 1)
            throw new IllegalArgumentException("Invalid canvas text geometry");
    }

    public static CanvasTextLayout create(double guiScale, double canvasScale, double left, double top,
                                          double requestedSize) {
        if (!Double.isFinite(requestedSize) || requestedSize <= 0)
            throw new IllegalArgumentException("Invalid text size");
        return new CanvasTextLayout(guiScale, canvasScale, left, top,
                Math.max(1, (int) Math.round(guiScale * canvasScale * requestedSize)));
    }

    public CanvasTextLayout withPixelScale(int pixels) {
        return new CanvasTextLayout(guiScale, canvasScale, left, top, pixels);
    }

    /** Applied inside the existing canvas transform, never on the artwork or hit boxes. */
    public float relativeScale() { return (float) (pixelScale / (guiScale * canvasScale)); }

    public float x(double canvasX) {
        return (float) ((Math.round((left + canvasX * canvasScale) * guiScale) / guiScale - left) / canvasScale);
    }

    public float y(double canvasY) {
        return (float) ((Math.round((top + canvasY * canvasScale) * guiScale) / guiScale - top) / canvasScale);
    }

    public double centeredLeft(double center, int fontWidth) {
        return center - fontWidth * pixelScale / (guiScale * canvasScale * 2);
    }

    /** Keep centered text inside the same snapped bounds used for truncation. */
    public double centeredLeftWithin(double canvasX, double canvasWidth, int fontWidth) {
        double first = Math.round((left + canvasX * canvasScale) * guiScale);
        double end = Math.floor((left + (canvasX + canvasWidth) * canvasScale) * guiScale);
        double width = fontWidth * (double) pixelScale;
        double origin = Math.max(first, Math.min(Math.round((first + end - width) / 2), end - width));
        return (origin / guiScale - left) / canvasScale;
    }

    public double lineY(double firstY, int line, int fontLineHeight) {
        return firstY + line * fontLineHeight * pixelScale / (guiScale * canvasScale);
    }

    /** The snapped start and the floored far edge share the same physical-pixel budget. */
    public int wrapWidth(double canvasX, double canvasWidth) {
        double first = Math.round((left + canvasX * canvasScale) * guiScale);
        double end = Math.floor((left + (canvasX + canvasWidth) * canvasScale) * guiScale);
        return Math.max(1, (int) Math.floor((end - first) / pixelScale));
    }

    public int rows(double canvasY, double canvasHeight, int fontLineHeight) {
        if (fontLineHeight < 1) throw new IllegalArgumentException("Invalid line height");
        double first = Math.round((top + canvasY * canvasScale) * guiScale);
        double end = Math.floor((top + (canvasY + canvasHeight) * canvasScale) * guiScale);
        return Math.max(1, (int) Math.floor((end - first) / (pixelScale * fontLineHeight)));
    }
}
