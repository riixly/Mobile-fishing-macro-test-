package fun.riix.shinobifish;

import java.awt.Graphics2D;
import java.awt.Color;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;

public class DetectorSelfTest {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Pass a reference image path");
        BufferedImage image = ImageIO.read(new File(args[0]));
        FishingDetector.Pixels src = image::getRGB;
        // Calibration is directly inside the green target in the provided screenshot.
        FishingDetector.Reading first = FishingDetector.analyze(src, image.getWidth(), image.getHeight(), 570, 816);
        require(first.barVisible, "expected timing bar");
        require(first.redVisible, "expected red needle");
        require(!first.inGreen, "needle should be outside green zone in the screenshot");
        require(first.greenLeft > 460 && first.greenRight < 680, "target outside expected coordinates");
        require(first.needleX > 390 && first.needleX < 420, "needle outside expected coordinates");
        System.out.printf("Screenshot: bar=%s green=[%d,%d] red=%d inGreen=%s%n", first.barVisible,
                first.greenLeft, first.greenRight, first.needleX, first.inGreen);

        // Move only the red needle into the center of the green target in a *test copy*.
        BufferedImage moved = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D gfx = moved.createGraphics();
        gfx.drawImage(image, 0, 0, null);
        gfx.setColor(new Color(245, 12, 12));
        gfx.fillRect(559, 788, 13, 56);
        gfx.dispose();
        FishingDetector.Reading second = FishingDetector.analyze(moved::getRGB, moved.getWidth(), moved.getHeight(), 570, 816);
        require(second.barVisible && second.redVisible && second.inGreen, "red marker should trigger inside green target");
        System.out.printf("Simulated hit: green=[%d,%d] red=%d inGreen=%s%n", second.greenLeft,
                second.greenRight, second.needleX, second.inGreen);
        System.out.println("PASS: both detector tests");
    }

    private static void require(boolean condition, String error) {
        if (!condition) throw new AssertionError(error);
    }
}
