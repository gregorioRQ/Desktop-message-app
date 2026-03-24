package com.pola;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

import java.io.IOException;
import com.pola.view.ViewManager;
import com.pola.view.SystemTrayManager;

public class App extends Application {

    private SystemTrayManager systemTrayManager;

    @Override
    public void start(Stage stage) throws IOException {
        Platform.setImplicitExit(false);

        ViewManager viewManager = new ViewManager(stage);
        systemTrayManager = new SystemTrayManager(stage);
        viewManager.setSystemTrayManager(systemTrayManager);

        viewManager.showLoginView();
    }

    @Override
    public void stop() {
        System.out.println("[App] Cerrando la aplicación completamente...");
        if (systemTrayManager != null) {
            systemTrayManager.shutdown();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}