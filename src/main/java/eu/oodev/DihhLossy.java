package eu.oodev;

import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Arrays;
import java.util.zip.*;

public class DihhLossy extends Dihh {
    // Znacznik na początku pliku, żeby rozpoznać, że to nasz format.
    private static final byte[] MAGIC = {'D', 'I', 'H', 'H'};

    // Tablica kwantyzacji (standard JPEG): im większa liczba, tym mocniej zaokrąglamy
    // dany współczynnik, czyli tracimy więcej szczegółów (głównie drobnych, wysokich częstotliwości).
    private static final int[] Q = {
            16, 11, 10, 16, 24, 40, 51, 61,
            12, 12, 14, 19, 26, 58, 60, 55,
            14, 13, 16, 24, 40, 57, 69, 56,
            14, 17, 22, 29, 51, 87, 80, 62,
            18, 22, 37, 56, 68, 109, 103, 77,
            24, 35, 55, 64, 81, 104, 113, 92,
            49, 64, 78, 87, 103, 121, 120, 101,
            72, 92, 95, 98, 112, 100, 103, 99};

    // Macierz DCT 8x8, wyliczana raz przy starcie klasy.
    private static final double[][] C = new double[8][8];

    static {
        for (int u = 0; u < 8; u++) {
            double a = (u == 0) ? Math.sqrt(1.0 / 8) : 0.5;
            for (int x = 0; x < 8; x++) {
                C[u][x] = a * Math.cos((2 * x + 1) * u * Math.PI / 16);
            }
        }
    }

    public static void write(BufferedImage img, File file, int quality) throws IOException {
        quality = Math.max(1, Math.min(100, quality));
        int w = img.getWidth(), h = img.getHeight();
        int[] q = scale(quality);

        // 1. Zamiana RGB na Y (jasność), Cb i Cr (kolor). Trzy osobne "płaszczyzny".
        double[][] planes = new double[3][w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = img.getRGB(x, y);
                double r = (p >> 16) & 0xFF, g = (p >> 8) & 0xFF, b = p & 0xFF;
                planes[0][y * w + x] = 0.299 * r + 0.587 * g + 0.114 * b;
                planes[1][y * w + x] = 128 - 0.168736 * r - 0.331264 * g + 0.5 * b;
                planes[2][y * w + x] = 128 + 0.5 * r - 0.418688 * g - 0.081312 * b;
            }
        }

        // 2. Każdą płaszczyznę tniemy na bloki 8x8: DCT, dzielenie przez tablicę, zaokrąglenie.
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(raw);
        for (double[] plane : planes) {
            for (int by = 0; by < h; by += 8) {
                for (int bx = 0; bx < w; bx += 8) {
                    double[] block = new double[64];
                    for (int y = 0; y < 8; y++) {
                        for (int x = 0; x < 8; x++) {
                            // na krawędziach obrazu powielamy ostatni piksel
                            int sx = Math.min(bx + x, w - 1);
                            int sy = Math.min(by + y, h - 1);
                            block[y * 8 + x] = plane[sy * w + sx] - 128;
                        }
                    }
                    double[] f = dct(block, false);
                    for (int k = 0; k < 64; k++) {
                        data.writeShort((int) Math.round(f[k] / q[k]));
                    }
                }
            }
        }

        // 3. Zapis: nagłówek + dane skompresowane Deflate (zera i powtórzenia zajmują mało miejsca).
        try (OutputStream fos = new FileOutputStream(file)) {
            DataOutputStream out = new DataOutputStream(fos);
            out.write(MAGIC);
            out.writeInt(w);
            out.writeInt(h);
            out.writeByte(quality);
            try (DeflaterOutputStream z = new DeflaterOutputStream(fos)) {
                z.write(raw.toByteArray());
            }
        }
    }

    public static BufferedImage read(File file) throws IOException {
        try (DataInputStream in = new DataInputStream(new FileInputStream(file))) {
            if (!Arrays.equals(in.readNBytes(4), MAGIC)) throw new IOException("Invalid DIHH file");
            int w = in.readInt(), h = in.readInt();
            int[] q = scale(in.readUnsignedByte());

            DataInputStream data = new DataInputStream(new BufferedInputStream(new InflaterInputStream(in)));

            // 1. Odczyt bloków: mnożenie przez tablicę (odwrotność dzielenia) i odwrotna DCT.
            double[][] planes = new double[3][w * h];
            for (double[] plane : planes) {
                for (int by = 0; by < h; by += 8) {
                    for (int bx = 0; bx < w; bx += 8) {
                        double[] f = new double[64];
                        for (int k = 0; k < 64; k++) {
                            f[k] = data.readShort() * q[k];
                        }
                        double[] block = dct(f, true);
                        for (int y = 0; y < 8 && by + y < h; y++) {
                            for (int x = 0; x < 8 && bx + x < w; x++) {
                                plane[(by + y) * w + (bx + x)] = block[y * 8 + x] + 128;
                            }
                        }
                    }
                }
            }

            // 2. Zamiana Y, Cb, Cr z powrotem na RGB.
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    double Y = planes[0][y * w + x];
                    double cb = planes[1][y * w + x] - 128;
                    double cr = planes[2][y * w + x] - 128;
                    int r = clamp(Y + 1.402 * cr);
                    int g = clamp(Y - 0.344136 * cb - 0.714136 * cr);
                    int b = clamp(Y + 1.772 * cb);
                    img.setRGB(x, y, (r << 16) | (g << 8) | b);
                }
            }
            return img;
        }
    }

    // DCT 2D = DCT 1D po wierszach, a potem po kolumnach.
    // inverse = false: piksele -> współczynniki, true: współczynniki -> piksele.
    private static double[] dct(double[] in, boolean inverse) {
        double[] tmp = new double[64], out = new double[64];
        double[] a = new double[8], b = new double[8];
        for (int y = 0; y < 8; y++) {
            System.arraycopy(in, y * 8, a, 0, 8);
            dct1d(a, b, inverse);
            System.arraycopy(b, 0, tmp, y * 8, 8);
        }
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) a[y] = tmp[y * 8 + x];
            dct1d(a, b, inverse);
            for (int y = 0; y < 8; y++) out[y * 8 + x] = b[y];
        }
        return out;
    }

    private static void dct1d(double[] in, double[] out, boolean inverse) {
        for (int i = 0; i < 8; i++) {
            double s = 0;
            for (int j = 0; j < 8; j++) {
                s += in[j] * (inverse ? C[j][i] : C[i][j]);
            }
            out[i] = s;
        }
    }

    // Skalowanie tablicy  zależnie od jakości Wyższa jakość = mniejsze liczby = mniej strat.
    private static int[] scale(int quality) {
        int s = quality < 50 ? 5000 / quality : 200 - quality * 2;
        int[] q = new int[64];
        for (int i = 0; i < 64; i++) {
            q[i] = Math.max(1, Math.min(255, (Q[i] * s + 50) / 100));
        }
        return q;
    }

    private static int clamp(double v) {
        return (int) Math.max(0, Math.min(255, Math.round(v)));
    }
}