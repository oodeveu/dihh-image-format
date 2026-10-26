package eu.oodev;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.zip.*;

public class Main {
    static void main() throws IOException {
        BufferedImage img = ImageIO.read(new File("cat.png"));
        Dihh.write(img, new File("cat.dihh"));
        BufferedImage back = Dihh.read(new File("cat.dihh"));

        ToPNG.dihhToPng(new File("cat.dihh"), new File("cat2.png"));
    }
}
