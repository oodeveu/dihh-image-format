plugins {
    id("java")
    application // Wtyczka wymagana, aby łatwo uruchamiać aplikację przez Gradle
    id("org.openjfx.javafxplugin") version "0.1.0" // Poprawiona stabilna wersja
}

group = "eu.oodev"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

// Konfiguracja JavaFX - automatycznie pobierze odpowiednie biblioteki dla Twojego systemu
javafx {
    version = "21" // Wersja dopasowana do LTS (działa z JDK 21 i nowszymi)
    modules("javafx.controls", "javafx.web") // 'javafx.web' odpowiada za obsługę HTML/JS
}

application {
    mainClass.set("eu.oodev.MainApp") // Wskaż tutaj pełną ścieżkę do swojej klasy głównej
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
