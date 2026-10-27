package eu.oodev;

import javafx.application.Application;
import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import netscape.javascript.JSObject;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;

public class Gui extends Application {

    public static class JavaBridge {
        private final Stage stage;

        public JavaBridge(Stage stage) {
            this.stage = stage;
        }

        public String chooseAndConvert(String targetFormat, boolean savePreview) {
            try {
                FileChooser chooser = new FileChooser();
                chooser.setTitle("Wybierz obraz do konwersji");
                chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                        "Obrazy (*.png, *.jpg, *.jpeg, *.dihh)", "*.png", "*.jpg", "*.jpeg", "*.dihh"));
                File input = chooser.showOpenDialog(stage);
                if (input == null) {
                    return "{\"cancelled\":true}";
                }

                BufferedImage image = input.getName().toLowerCase().endsWith(".dihh")
                        ? DihhLossy.read(input)
                        : ImageIO.read(input);
                if (image == null) {
                    throw new IOException("Nie można odczytać wybranego obrazu.");
                }

                String format = targetFormat == null ? "" : targetFormat.toLowerCase();
                File output = outputFile(input, format);
                switch (format) {
                    case "dihh" -> DihhLossy.write(image, output, 40);
                    case "png", "jpg" -> {
                        if (!ImageIO.write(image, format, output)) {
                            throw new IOException("Brak obsługi zapisu formatu " + format);
                        }
                    }
                    default -> throw new IOException("Nieobsługiwany format docelowy: " + targetFormat);
                }

                File preview = output;
                if ("dihh".equals(format)) {
                    preview = savePreview
                            ? new File(input.getParentFile(),
                            removeExtension(output.getName()) + "_preview.png")
                            : Files.createTempFile("dihh-preview-", ".png").toFile();
                    ImageIO.write(DihhLossy.read(output), "png", preview);
                    if (!savePreview) {
                        preview.deleteOnExit();
                    }
                }
                long originalSize = Files.size(input.toPath());
                long outputSize = Files.size(output.toPath());
                double ratio = outputSize == 0 ? 0 : (double) originalSize / outputSize;
                return String.format(Locale.ROOT,
                        "{\"cancelled\":false,\"input\":\"%s\",\"output\":\"%s\",\"originalSize\":%d,\"outputSize\":%d,\"ratio\":%.2f,\"width\":%d,\"height\":%d}",
                        escape(input.toURI().toString()), escape(preview.toURI().toString()),
                        originalSize, outputSize, ratio, image.getWidth(), image.getHeight());
            } catch (Exception exception) {
                return "{\"cancelled\":false,\"error\":\"" + escape(exception.getMessage()) + "\"}";
            }
        }

        private static File outputFile(File input, String format) {
            String name = input.getName();
            int dot = name.lastIndexOf('.');
            String base = dot > 0 ? name.substring(0, dot) : name;
            return new File(input.getParentFile(), base + "_converted." + format);
        }

        private static String removeExtension(String name) {
            int dot = name.lastIndexOf('.');
            return dot > 0 ? name.substring(0, dot) : name;
        }

        private static String escape(String value) {
            if (value == null) {
                return "";
            }
            return value.replace("\\", "\\\\").replace("\"", "\\\"");
        }
    }

    @Override
    public void start(Stage primaryStage) {
        WebView webView = new WebView();
        WebEngine webEngine = webView.getEngine();

        // Ładowanie lokalnego pliku HTML z zasobów
        // Ścieżka z "/" na początku = od korzenia classpath
        var resource = getClass().getResource("/html/index.html");
        if (resource == null) {
            throw new IllegalStateException("Nie znaleziono index.html w zasobach!");
        }
        webEngine.load(resource.toExternalForm());
        JavaBridge bridge = new JavaBridge(primaryStage);
        webEngine.getLoadWorker().stateProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == Worker.State.SUCCEEDED) {
                JSObject window = (JSObject) webEngine.executeScript("window");
                window.setMember("app", bridge);
            }
        });
        Scene scene = new Scene(webView, 800, 600);
        primaryStage.setScene(scene);
        primaryStage.setTitle("DIHH IMAGE FORMAT");
        primaryStage.show();
    }
}