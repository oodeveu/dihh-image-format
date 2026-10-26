package eu.oodev;

import java.awt.image.BufferedImage;
import java.io.*;
import java.util.zip.*;

public class Dihh {
    private static final byte[] MAGIC = {'D', 'I', 'H', 'H'};
    private static final int VER = 3; // RGB bez alfy

    public static void write(BufferedImage img, File file) throws IOException {
        int w = img.getWidth();
        int h = img.getHeight();
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
            out.write(MAGIC);
            out.writeByte(VER);
            out.writeInt(w);
            out.writeInt(h);

            DeflaterOutputStream deflater = new DeflaterOutputStream(out);
            int[] argb = new int[w];
            byte[] row = new byte[w * 3];

            for (int y = 0; y < h; y++) {
                img.getRGB(0, y, w, 1, argb, 0, w);
                for (int x = 0; x < w; x++) {
                    int p = argb[x];
                    row[x * 3]     = (byte) (p >> 16); // R
                    row[x * 3 + 1] = (byte) (p >> 8);  // G
                    row[x * 3 + 2] = (byte) p;         // B
                }
                deflater.write(row);
            }
            deflater.finish(); // kończy kompresję, nie zamyka out
        }
    }

    public static BufferedImage read(File file) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            byte[] magic = new byte[4];
            in.readFully(magic);
            if (!java.util.Arrays.equals(magic, MAGIC)) {
                throw new IOException("Invalid DIHH file");
            }
            int ver = in.readUnsignedByte();
            if (ver != VER) {
                throw new IOException("Unsupported DIHH version: " + ver);
            }

            int w = in.readInt();
            int h = in.readInt();

            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);

            DataInputStream pixels = new DataInputStream(new InflaterInputStream(in));
            byte[] row = new byte[w * 3];
            int[] rgb = new int[w];

            for (int y = 0; y < h; y++) {
                pixels.readFully(row);
                for (int x = 0; x < w; x++) {
                    rgb[x] = ((row[x * 3] & 0xFF) << 16)
                            | ((row[x * 3 + 1] & 0xFF) << 8)
                            |  (row[x * 3 + 2] & 0xFF);
                }
                img.setRGB(0, y, w, 1, rgb, 0, w);
            }
            return img;
        }
    }
}