package fun.riix.shinobifish;

/** Stateless pixel classifier for the red marker / green target in the fishing timing bar. */
public final class FishingDetector {
    public interface Pixels { int argb(int x, int y); }

    public static final class Reading {
        public final boolean barVisible;
        public final boolean redVisible;
        public final boolean inGreen;
        public final int greenLeft, greenRight, needleX;
        Reading(boolean visible, boolean red, boolean hit, int left, int right, int needle) {
            barVisible = visible; redVisible = red; inGreen = hit;
            greenLeft = left; greenRight = right; needleX = needle;
        }
    }

    private FishingDetector() {}

    static boolean isGreen(int c) {
        int r = (c >>> 16) & 255, g = (c >>> 8) & 255, b = c & 255;
        return g > 108 && g > r * 1.65 && g > b * 1.35;
    }

    static boolean isNeedleRed(int c) {
        int r = (c >>> 16) & 255, g = (c >>> 8) & 255, b = c & 255;
        return r > 145 && r > g * 1.8 && r > b * 1.5;
    }

    public static Reading analyze(Pixels pixels, int w, int h, int greenCenterX, int barY) {
        if (pixels == null || greenCenterX < 0 || greenCenterX >= w || barY < 18 || barY >= h - 18)
            return new Reading(false, false, false, -1, -1, -1);

        // Search only on a calibrated horizontal scan line. Do not use global image detection:
        // other green/red elements elsewhere in the Roblox HUD are visually similar.
        int radius = Math.min(710, (int) (w * 0.31));
        int x0 = Math.max(0, greenCenterX - radius);
        int x1 = Math.min(w - 1, greenCenterX + radius);
        int minRun = Math.max(4, w / 500);

        // Pick the green segment closest to the calibrated target center.
        int closestL = -1, closestR = -1;
        int bestDistance = Integer.MAX_VALUE;
        int runStart = -1;
        for (int x = x0; x <= x1 + 1; x++) {
            boolean green = x <= x1 && isGreen(pixels.argb(x, barY));
            if (green && runStart < 0) runStart = x;
            if (!green && runStart >= 0) {
                int end = x - 1;
                if (end - runStart + 1 >= minRun) {
                    int distance = greenCenterX < runStart ? runStart - greenCenterX
                            : greenCenterX > end ? greenCenterX - end : 0;
                    if (distance < bestDistance || (distance == bestDistance && end - runStart > closestR - closestL)) {
                        closestL = runStart;
                        closestR = end;
                        bestDistance = distance;
                    }
                }
                runStart = -1;
            }
        }
        // Ignore unrelated green pixels if the fishing panel isn't visible.
        if (closestL < 0 || bestDistance > Math.max(36, w / 13))
            return new Reading(false, false, false, -1, -1, -1);

        // When the needle overlaps the green zone it may split the target in two.
        // Merge nearby green segments on either side (<= 28 px at a 2048 wide display).
        int allowedGap = Math.max(9, w / 72);
        boolean changed;
        do {
            changed = false;
            int start = -1;
            for (int x = x0; x <= x1 + 1; x++) {
                boolean green = x <= x1 && isGreen(pixels.argb(x, barY));
                if (green && start < 0) start = x;
                if (!green && start >= 0) {
                    int end = x - 1;
                    if (end - start + 1 >= minRun &&
                        ((start > closestR && start - closestR - 1 <= allowedGap) ||
                         (end < closestL && closestL - end - 1 <= allowedGap))) {
                        closestL = Math.min(closestL, start);
                        closestR = Math.max(closestR, end);
                        changed = true;
                    }
                    start = -1;
                }
            }
        } while (changed);

        // A fishing green target is a strip, not a single glowing pixel.
        if (closestR - closestL < Math.max(16, w / 140))
            return new Reading(false, false, false, -1, -1, -1);

        // Search red vertical rectangles, not red text. Check y above/below the bar.
        int needle = -1, best = Integer.MAX_VALUE;
        int upper = Math.max(0, barY - Math.max(6, h / 85));
        int lower = Math.min(h - 1, barY + Math.max(6, h / 85));
        runStart = -1;
        for (int x = x0; x <= x1 + 1; x++) {
            boolean red = x <= x1 && isNeedleRed(pixels.argb(x, barY)) &&
                    isNeedleRed(pixels.argb(x, upper)) && isNeedleRed(pixels.argb(x, lower));
            if (red && runStart < 0) runStart = x;
            if (!red && runStart >= 0) {
                int end = x - 1, length = end - runStart + 1;
                if (length >= 3 && length <= Math.max(16, w / 38)) {
                    int center = (runStart + end) / 2;
                    int distance = center < closestL ? closestL - center
                            : center > closestR ? center - closestR : 0;
                    if (distance < best) { best = distance; needle = center; }
                }
                runStart = -1;
            }
        }
        // Don't accidentally click from some faraway vertical red HUD element.
        if (needle < 0 || best > Math.max(w / 6, 65))
            return new Reading(true, false, false, closestL, closestR, -1);

        int margin = Math.max(3, w / 400);
        boolean hit = needle >= closestL + margin && needle <= closestR - margin;
        return new Reading(true, true, hit, closestL, closestR, needle);
    }
}
