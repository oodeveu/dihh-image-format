package eu.oodev;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws IOException {
        Scanner scanner = new Scanner(System.in);

        System.out.println("Podaj sciezke do pliku:");
        String path = scanner.nextLine().trim();

        File in = new File(path);
        if (!in.exists()) {
            System.out.println("Nie ma takiego pliku: " + path);
            return;
        }
        System.out.println("Rozmiar przed konwersja: " + in.length() + " bajtow");

        BufferedImage img = ImageIO.read(in);
        if (img == null) {
            System.out.println("To nie jest obsługiwany obraz.");
            return;
        }

        // nazwy wyjsciowe na podstawie wejsciowej (foto.png -> foto.dihh, foto_back.png)
        String base = path.replaceAll("\\.[^.\\\\/]+$", "");
        File dihh = new File(base + ".dihh");
        File back = new File(base + "_back.png");

        DihhLossy.write(img, dihh, 40);
        long size = dihh.length();
        System.out.printf("Rozmiar po konwersji: %d bajtow (%.1fx mniej)%n",
                size, (double) in.length() / size);

        BufferedImage decoded = DihhLossy.read(dihh);
        ImageIO.write(decoded, "png", back);
        System.out.println("Zapisano podglad po dekompresji: " + back);
    }
}
