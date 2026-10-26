package eu.oodev;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public  class ToJPG {

    //Kompresija bez straty jakości
    public static void dihhToJpg(File dihh, File jpg, float quality) throws IOException {
        BufferedImage img = Dihh.read(dihh);

        var writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new IOException("Brak writera JPG");
        }
        ImageWriter writer = writers.next();

        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);

        try (ImageOutputStream out = ImageIO.createImageOutputStream(jpg)) {
            writer.setOutput(out);
            writer.write(null, new IIOImage(img, null, null), param);
        } finally {
            writer.dispose();
        }
    }

    public static void main(String[] args) throws IOException {
        dihhToJpg(new File("input.dihh"), new File("output.jpg"), 0.9f);
    }
}