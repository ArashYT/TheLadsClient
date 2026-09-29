package com.thelads.core.v26_2.feature.screenshots;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;
public final class ScreenshotFileIOTest {
    private static int passed;
    private static void check(boolean test, String description) { if (!test) throw new AssertionError(description); passed++; }
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toAbsolutePath().normalize(); Files.createDirectories(root);
        Path a = root.resolve("first.PNG"), b = root.resolve("second.jpeg");
        BufferedImage image = new BufferedImage(320, 180, BufferedImage.TYPE_INT_RGB);
        image.setRGB(10, 10, 0xffaabbcc); ImageIO.write(image, "png", a.toFile()); ImageIO.write(image, "jpg", b.toFile());
        check(ScreenshotFileIO.imageFile(a), "uppercase PNG listed");
        check(ScreenshotFileIO.imageFile(b), "JPEG listed");
        check(ScreenshotFileIO.read(a).getRGB(10,10) == 0xffaabbcc, "PNG preserves actual pixel");
        check(ScreenshotFileIO.read(b).getWidth() == 320, "JPEG decode supported without global PNG validation bypass");
        check(ScreenshotFileIO.read(a, 100).getWidth() <= 100, "preview source subsampling");
        Path renamed = ScreenshotFileIO.rename(b, "renamed");
        check(renamed.getFileName().toString().equals("renamed.jpeg"), "rename preserves JPEG extension");
        check(!Files.exists(b) && Files.exists(renamed), "rename moves actual image");
        for (String invalid : new String[]{"../outside", "a/b", "a\\b", "", "  ", "..", "x.", "NUL", "con.txt", "bad:name"}) {
            check(!ScreenshotFileIO.validStem(invalid), "invalid filename rejected: " + invalid);
        }
        check(ScreenshotFileIO.validStem("My holiday (2)"), "ordinary filename accepted");
        Path collision = root.resolve("exists.jpeg"); Files.copy(renamed, collision);
        boolean rejected = false; try { ScreenshotFileIO.rename(renamed, "exists"); } catch (java.io.IOException expected) { rejected = true; }
        check(rejected && Files.exists(renamed) && Files.exists(collision), "collision preserves both original files");
        Path corrupt = root.resolve("corrupt.png"); Files.writeString(corrupt,"not an image");
        rejected = false; try { ScreenshotFileIO.read(corrupt); } catch (java.io.IOException expected) { rejected = true; }
        check(rejected, "corrupt image is recoverable IO failure");
        check(ScreenshotFileIO.delete(renamed) && !Files.exists(renamed), "explicit selected-image deletion");
        Path other = root.resolve("keep.txt"); Files.writeString(other,"keep"); rejected = false;
        try { ScreenshotFileIO.delete(other); } catch (java.io.IOException expected) { rejected = true; }
        check(rejected && Files.readString(other).equals("keep"), "non-image delete rejected");
        System.out.println("Lads screenshots file IO END: " + passed + " checks passed, 0 failed; isolated generated fixtures");
    }
}
