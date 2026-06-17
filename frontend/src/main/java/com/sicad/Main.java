package com.sicad;

import com.sicad.Model.ClienteSocket;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class Main extends Application {

    private Label meuIdLabel;
    private Label statusLabel;

    private ClienteSocket clienteSocket;

    @Override
    public void start(Stage stage) {

        clienteSocket = new ClienteSocket();

        VBox root = new VBox(15);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(30));

        root.setStyle("""
                -fx-background-color: #0f172a;
                """);

        ImageView logo = null;

        try {

            logo = new ImageView(
                    new Image(
                            getClass().getResourceAsStream(
                                    "/com/sicad/assets/logo.png"
                            )
                    )
            );

            logo.setFitWidth(220);
            logo.setPreserveRatio(true);

        } catch (Exception ignored) {
        }

        Label meuIdTitulo = new Label("Seu ID SICAD");

        meuIdTitulo.setStyle("""
                -fx-text-fill: white;
                -fx-font-size: 14px;
                """);

        meuIdLabel = new Label("Conectando...");

        meuIdLabel.setStyle("""
                -fx-background-color: #1e293b;
                -fx-text-fill: #38bdf8;
                -fx-padding: 12;
                -fx-background-radius: 10;
                -fx-font-size: 18px;
                -fx-font-weight: bold;
                """);

        Label remotoTitulo = new Label("ID Remoto");

        remotoTitulo.setStyle("""
                -fx-text-fill: white;
                -fx-font-size: 14px;
                """);

        TextField idRemotoField = new TextField();

        idRemotoField.setPromptText(
                "Digite o ID do dispositivo"
        );

        idRemotoField.setMaxWidth(300);

        idRemotoField.setStyle("""
                -fx-background-color: #1e293b;
                -fx-text-fill: white;
                -fx-prompt-text-fill: #94a3b8;
                -fx-background-radius: 10;
                -fx-padding: 12;
                -fx-font-size: 14px;
                """);

        Button conectarBtn =
                new Button("Conectar");

        conectarBtn.setStyle("""
                -fx-background-color: #2563eb;
                -fx-text-fill: white;
                -fx-font-size: 14px;
                -fx-font-weight: bold;
                -fx-background-radius: 10;
                -fx-padding: 10 25 10 25;
                """);

        statusLabel = new Label(
                "Conectando ao servidor..."
        );

        statusLabel.setStyle("""
                -fx-text-fill: #cbd5e1;
                -fx-font-size: 13px;
                """);

        conectarBtn.setOnAction(e -> {

            String idDestino =
                    idRemotoField.getText().trim();

            if (idDestino.isEmpty()) {

                statusLabel.setText(
                        "Informe um ID válido."
                );

                return;
            }

            statusLabel.setText(
                    "Solicitando conexão para "
                            + idDestino
            );

            /*
            clienteSocket.enviarMensagem(
                    new Mensagem(
                            "REQUEST_CONNECTION",
                            idDestino
                    )
            );
            */
        });

        if (logo != null) {
            root.getChildren().add(logo);
        }

        root.getChildren().addAll(
                meuIdTitulo,
                meuIdLabel,

                remotoTitulo,
                idRemotoField,

                conectarBtn,

                statusLabel
        );

        Scene scene =
                new Scene(root, 500, 650);

        stage.setTitle("SICAD");

        stage.setScene(scene);

        stage.show();

        conectarServidor();
    }

    private void conectarServidor() {

        try {

            boolean conectado =
                    clienteSocket.conectar();

            if (conectado) {

                statusLabel.setText(
                        "Conectado ao servidor"
                );

            } else {

                statusLabel.setText(
                        "Falha ao conectar"
                );
            }

        } catch (Exception e) {

            statusLabel.setText(
                    "Erro: " + e.getMessage()
            );
        }
    }

    public void atualizarMeuId(String id) {

        meuIdLabel.setText(id);
    }

    public void atualizarStatus(String status) {

        statusLabel.setText(status);
    }

    public static void main(String[] args) {
        launch();
    }
}