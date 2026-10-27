package eu.oodev;

import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Arrays;
import java.util.zip.*;

public class DihhLossy extends Dihh {
    private static final byte[] MAGIC = {'D', 'I', 'H', 'H'};
    private static final int VER = 4; // YCbCr 4:2:0 + DCT 8x8 + kwantyzacja + RLE + Deflate

    private static final int[] QY = {
            16, 11, 10, 16, 24, 40, 51, 61,
            12, 12, 14, 19, 26, 58, 60, 55,
            14, 13, 16, 24, 40, 57, 69, 56,
            14, 17, 22, 29, 51, 87, 80, 62,
            18, 22, 37, 56, 68, 109, 103, 77,
            24, 35, 55, 64, 81, 104, 113, 92,
            49, 64, 78, 87, 103, 121, 120, 101,
            72, 92, 95, 98, 112, 100, 103, 99};

    private static final int[] QC = {
            17, 18, 24, 47, 99, 99, 99, 99,
            18, 21, 26, 66, 99, 99, 99, 99,
            24, 26, 56, 99, 99, 99, 99, 99,
            47, 66, 99, 99, 99, 99, 99, 99,
            99, 99, 99, 99, 99, 99, 99, 99,
            99, 99, 99, 99, 99, 99, 99, 99,
            99, 99, 99, 99, 99, 99, 99, 99,
            99, 99, 99, 99, 99, 99, 99, 99};

    private static final float[][] C = new float[8][8];
    private static final int[] ZZ = new int[64];

    static {
        for (int u = 0; u < 8; u++) {
            double a = u == 0 ? Math.sqrt(1.0 / 8) : 0.5;
            for (int x = 0; x < 8; x++) {
                C[u][x] = (float) (a * Math.cos((2 * x + 1) * u * Math.PI / 16));
            }
        }
        int x = 0, y = 0;
        for (int i = 0; i < 64; i++) {
            ZZ[i] = y * 8 + x;
            if (((x + y) & 1) == 0) {
                if (x == 7) y++; else if (y == 0) x++; else { x++; y--; }
            } else {
                if (y == 7) x++; else if (x == 0) y++; else { x--; y++; }
            }
        }
    }

    // API

    public static void write(BufferedImage img, File file, int quality) throws IOException {
        byte[] payload = compress(img, quality);
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
            out.write(MAGIC);
            out.writeByte(VER);
            out.writeInt(img.getWidth());
            out.writeInt(img.getHeight());
            out.writeByte(quality);
            out.write(payload);
        }
    }

    public static BufferedImage read(File file) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            byte[] magic = new byte[4];
            in.readFully(magic);
            if (!Arrays.equals(magic, MAGIC)) throw new IOException("Invalid DIHH file");
            int ver = in.readUnsignedByte();
            if (ver != VER) throw new IOException("Unsupported DIHH version: " + ver);
            int w = in.readInt(), h = in.readInt();
            int quality = in.readUnsignedByte();
            byte[] payload = in.readAllBytes();
            return decompress(payload, w, h, quality);
        }
    }

    // kompresja

    public static byte[] compress(BufferedImage img, int quality) throws IOException {
        quality = Math.max(1, Math.min(100, quality));
        int w = img.getWidth(), h = img.getHeight();
        int cw = (w + 1) / 2, ch = (h + 1) / 2;

        float[] Y = new float[w * h];
        float[] Cb = new float[cw * ch];
        float[] Cr = new float[cw * ch];
        int[] cnt = new int[cw * ch];

        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            img.getRGB(0, y, w, 1, row, 0, w);
            for (int x = 0; x < w; x++) {
                int p = row[x];
                float r = (p >> 16) & 0xFF, g = (p >> 8) & 0xFF, b = p & 0xFF;
                Y[y * w + x] = 0.299f * r + 0.587f * g + 0.114f * b;
                int ci = (y / 2) * cw + (x / 2);
                Cb[ci] += 128 - 0.168736f * r - 0.331264f * g + 0.5f * b;
                Cr[ci] += 128 + 0.5f * r - 0.418688f * g - 0.081312f * b;
                cnt[ci]++;
            }
        }
        for (int i = 0; i < cnt.length; i++) { // usrednianie 2x2
            Cb[i] /= cnt[i];
            Cr[i] /= cnt[i];
        }

        int[] qy = scale(QY, quality), qc = scale(QC, quality);
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        encodePlane(Y, w, h, qy, raw);
        encodePlane(Cb, cw, ch, qc, raw);
        encodePlane(Cr, cw, ch, qc, raw);

        ByteArrayOutputStream packed = new ByteArrayOutputStream();
        Deflater def = new Deflater(Deflater.BEST_COMPRESSION);
        try (DeflaterOutputStream d = new DeflaterOutputStream(packed, def)) {
            raw.writeTo(d);
        }
        def.end();
        return packed.toByteArray();
    }

    public static BufferedImage decompress(byte[] payload, int w, int h, int quality) throws IOException {
        int cw = (w + 1) / 2, ch = (h + 1) / 2;
        byte[] data = new InflaterInputStream(new ByteArrayInputStream(payload)).readAllBytes();
        int[] pos = {0};

        int[] qy = scale(QY, quality), qc = scale(QC, quality);
        float[] Y = decodePlane(data, pos, w, h, qy);
        float[] Cb = decodePlane(data, pos, cw, ch, qc);
        float[] Cr = decodePlane(data, pos, cw, ch, qc);

        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float yy = Y[y * w + x];
                int ci = (y / 2) * cw + (x / 2);
                float cb = Cb[ci] - 128, cr = Cr[ci] - 128;
                int r = clamp(yy + 1.402f * cr);
                int g = clamp(yy - 0.344136f * cb - 0.714136f * cr);
                int b = clamp(yy + 1.772f * cb);
                row[x] = (r << 16) | (g << 8) | b;
            }
            img.setRGB(0, y, w, 1, row, 0, w);
        }
        return img;
    }

    // DCT + kwantyzacja + RLE

    private static void encodePlane(float[] src, int sw, int sh, int[] q, ByteArrayOutputStream out) {
        int bw = (sw + 7) / 8, bh = (sh + 7) / 8;
        float[] blk = new float[64];
        int prevDc = 0;

        for (int by = 0; by < bh; by++) {
            for (int bx = 0; bx < bw; bx++) {
                for (int y = 0; y < 8; y++) {
                    for (int x = 0; x < 8; x++) {
                        int sx = Math.min(bx * 8 + x, sw - 1); // powielanie krawedzi
                        int sy = Math.min(by * 8 + y, sh - 1);
                        blk[y * 8 + x] = src[sy * sw + sx] - 128;
                    }
                }
                float[] f = dct(blk);

                int[] qv = new int[64];
                for (int k = 0; k < 64; k++) qv[k] = Math.round(f[k] / q[k]);

                writeVar(out, qv[0] - prevDc);
                prevDc = qv[0];

                int run = 0;
                for (int i = 1; i < 64; i++) {
                    int v = qv[ZZ[i]];
                    if (v == 0) {
                        run++;
                    } else {
                        out.write(run);
                        writeVar(out, v);
                        run = 0;
                    }
                }
                out.write(255); // EOB
            }
        }
    }

    private static float[] decodePlane(byte[] d, int[] pos, int sw, int sh, int[] q) {
        int bw = (sw + 7) / 8, bh = (sh + 7) / 8;
        float[] dst = new float[sw * sh];
        float[] f = new float[64];
        int prevDc = 0;

        for (int by = 0; by < bh; by++) {
            for (int bx = 0; bx < bw; bx++) {
                int[] qv = new int[64];
                prevDc += readVar(d, pos);
                qv[0] = prevDc;

                int i = 1;
                while (true) {
                    int run = d[pos[0]++] & 0xFF;
                    if (run == 255) break;
                    i += run;
                    qv[ZZ[i]] = readVar(d, pos);
                    i++;
                }

                for (int k = 0; k < 64; k++) f[k] = qv[k] * q[k];
                float[] px = idct(f);

                for (int y = 0; y < 8; y++) {
                    int dy = by * 8 + y;
                    if (dy >= sh) break;
                    for (int x = 0; x < 8; x++) {
                        int dx = bx * 8 + x;
                        if (dx >= sw) break;
                        dst[dy * sw + dx] = px[y * 8 + x] + 128;
                    }
                }
            }
        }
        return dst;
    }

    // DCT

    private static float[] dct(float[] b) {
        float[] tmp = new float[64], out = new float[64];
        for (int y = 0; y < 8; y++)
            for (int u = 0; u < 8; u++) {
                float s = 0;
                for (int x = 0; x < 8; x++) s += b[y * 8 + x] * C[u][x];
                tmp[y * 8 + u] = s;
            }
        for (int u = 0; u < 8; u++)
            for (int v = 0; v < 8; v++) {
                float s = 0;
                for (int y = 0; y < 8; y++) s += tmp[y * 8 + u] * C[v][y];
                out[v * 8 + u] = s;
            }
        return out;
    }

    private static float[] idct(float[] f) {
        float[] tmp = new float[64], out = new float[64];
        for (int v = 0; v < 8; v++)
            for (int x = 0; x < 8; x++) {
                float s = 0;
                for (int u = 0; u < 8; u++) s += f[v * 8 + u] * C[u][x];
                tmp[v * 8 + x] = s;
            }
        for (int x = 0; x < 8; x++)
            for (int y = 0; y < 8; y++) {
                float s = 0;
                for (int v = 0; v < 8; v++) s += tmp[v * 8 + x] * C[v][y];
                out[y * 8 + x] = s;
            }
        return out;
    }

    // pomocnicze

    private static int[] scale(int[] base, int quality) {
        int s = quality < 50 ? 5000 / quality : 200 - quality * 2;
        int[] q = new int[64];
        for (int i = 0; i < 64; i++) {
            q[i] = Math.max(1, Math.min(255, (base[i] * s + 50) / 100));
        }
        return q;
    }

    private static void writeVar(ByteArrayOutputStream out, int v) {
        int u = (v << 1) ^ (v >> 31); // znak w najmlodszym bicie
        while (u >= 0x80) {
            out.write((u & 0x7F) | 0x80);
            u >>>= 7;
        }
        out.write(u);
    }

    private static int readVar(byte[] d, int[] pos) {
        int u = 0, shift = 0, b;
        do {
            b = d[pos[0]++] & 0xFF;
            u |= (b & 0x7F) << shift;
            shift += 7;
        } while ((b & 0x80) != 0);
        return (u >>> 1) ^ -(u & 1);
    }

    private static int clamp(float v) {
        int i = Math.round(v);
        return i < 0 ? 0 : Math.min(i, 255);
    }
}