package com.sicad;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.scene.image.ImageView;
import java.net.Socket;

public class Main extends Application {

    private Label statusLabel;

    @Override
    public void start(Stage stage) {

        VBox root = new VBox(15);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(30));
        root.setStyle("""
                    -fx-background-color: #0f172a;
                """);

        ImageView logo = new ImageView(
                new Image(getClass()
                        .getResourceAsStream("/com/sicad/assets/logo.png")));

        logo.setFitWidth(220);
        logo.setPreserveRatio(true);

        Label meuIdTitulo = new Label("Seu ID");
        meuIdTitulo.setStyle("""
                    -fx-text-fill: white;
                    -fx-font-size: 14px;
                """);

        String meuId = gerarId();

        Label meuIdLabel = new Label(meuId);
        meuIdLabel.setStyle("""
                    -fx-background-color: #1e293b;
                    -fx-text-fill: #38bdf8;
                    -fx-padding: 12;
                    -fx-background-radius: 10;
                    -fx-font-size: 18px;
                    -fx-font-weight: bold;
                """);

        TextField idField = new TextField();
        idField.setPromptText("Digite o ID do dispositivo");
        idField.setMaxWidth(300);

        idField.setStyle("""
                    -fx-background-color: #1e293b;
                    -fx-text-fill: white;
                    -fx-prompt-text-fill: #94a3b8;
                    -fx-background-radius: 10;
                    -fx-padding: 12;
                    -fx-font-size: 14px;
                """);

        Button conectarBtn = new Button("Conectar");

        conectarBtn.setStyle("""
                    -fx-background-color: #2563eb;
                    -fx-text-fill: white;
                    -fx-font-size: 14px;
                    -fx-font-weight: bold;
                    -fx-background-radius: 10;
                    -fx-padding: 10 25 10 25;
                """);

        statusLabel = new Label("Não conectado");

        statusLabel.setStyle("""
                    -fx-text-fill: #cbd5e1;
                    -fx-font-size: 13px;
                """);

        conectarBtn.setOnAction(e -> {
            String idDestino = idField.getText();

            statusLabel.setText(
                    "Tentando conectar ao ID: " + idDestino);
        });

        root.getChildren().addAll(
                logo,
                meuIdTitulo,
                meuIdLabel,
                idField,
                conectarBtn,
                statusLabel);

        Scene scene = new Scene(root, 500, 650);

        stage.setTitle("SICAD");
        stage.setScene(scene);
        stage.show();
    }

    private String gerarId() {
        return java.util.UUID.randomUUID()
                .toString()
                .substring(0, 12)
                .toUpperCase();
    }

    private void conectar(String ip, String portaTexto) {

        try {
            int porta = Integer.parseInt(portaTexto);

            Socket socket = new Socket(ip, porta);

            statusLabel.setText(
                    "Conectado em " + ip + ":" + porta);

            socket.close();

        } catch (Exception e) {

            statusLabel.setText(
                    "Erro: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        launch();
    }
}