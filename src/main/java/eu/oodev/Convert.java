package eu.oodev;

import java.io.File;

public class Convert {
    public static void dihhToJpg(File dihh, File jpg, float quality) throws Exception {
        ToJPG.dihhToJpg(dihh, jpg, quality);
    }

    public static void dihhToPng(File dihh, File png) throws Exception {
        ToPNG.dihhToPng(dihh, png);
    }

    public static void pngToDihh(File png, File dihh) throws Exception {
        ToPNG.pngToDihh(png, dihh);
    }
}
