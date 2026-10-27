package eu.oodev;


import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class ToPNG extends Convert {
    public static void dihhToPng(File dihh, File png) throws IOException {
        BufferedImage img = Dihh.read(dihh);
        if (!ImageIO.write(img, "png", png)) {
            throw new IOException("Brak writera PNG");
        }
    }

    public static void pngToDihh(File png, File dihh) throws IOException {
        BufferedImage img = ImageIO.read(png);
        if (img == null) {
            throw new IOException("Nie udalo sie wczytac obrazu: " + png);
        }
        Dihh.write(img, dihh);
    }
}
