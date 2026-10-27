package eu.oodev;

import javafx.application.Application;
import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import netscape.javascript.JSObject;

public class Gui extends Application {

    public static class JavaBridge {
        public void powiedzHello(String imie) {
            System.out.println("Java odebrała sygnał od: " + imie);
        }
    }

    private final JavaBridge bridge = new JavaBridge();

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
        Scene scene = new Scene(webView, 800, 600);
        primaryStage.setScene(scene);
        primaryStage.setTitle("DIHH IMAGE FORMAT");
        primaryStage.show();
    }
}