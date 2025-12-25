package com.pola;

import javafx.application.Application;
import javafx.stage.Stage;

import java.io.IOException;

import com.pola.view.ViewManager;

public class App extends Application {


    @Override
    public void start(Stage stage) throws IOException {
        ViewManager viewManager = new ViewManager(stage);
        viewManager.showLoginView();
    }

    @Override
    public void stop(){
        System.out.println("Cerrando la app...");
    }

    public static void main(String[] args) {
        launch(args);
    }

}