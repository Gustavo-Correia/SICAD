package com.sicad;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage stage) {

        Scene scene = new Scene(
            new Label("SICAD iniciado com sucesso!"),
            500,
            300
        );

        stage.setTitle("SICAD");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}