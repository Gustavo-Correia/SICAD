package com.sicad;

import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

public class AuxiliarDialogo {

    public static boolean mostrarDialogoSolicitacaoConexao(String idDispositivo) {
        return mostrarDialogoSolicitacaoConexao(idDispositivo, 60);
    }

    public static boolean mostrarDialogoSolicitacaoConexao(String idDispositivo, int segundosLimite) {
        AtomicBoolean aceito = new AtomicBoolean(false);

        Stage dialogo = new Stage();
        dialogo.initModality(Modality.APPLICATION_MODAL);
        dialogo.initStyle(StageStyle.TRANSPARENT);

        VBox raiz = new VBox(20);
        raiz.getStyleClass().add("card");
        raiz.setStyle("-fx-background-color: #1E293B; -fx-border-color: #334155; -fx-border-width: 1px; -fx-border-radius: 10px; -fx-background-radius: 10px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.8), 20, 0, 0, 10);");
        raiz.setPadding(new Insets(30));
        raiz.setPrefWidth(400);

        Label titulo = new Label("Solicitação de Acesso Remoto");
        titulo.getStyleClass().add("title");
        titulo.setStyle("-fx-font-size: 18px; -fx-text-fill: white;");

        Label conteudo = new Label("O dispositivo " + idDispositivo + " deseja controlar sua máquina.\n\nVocê permite esta conexão?");
        conteudo.setWrapText(true);
        conteudo.setStyle("-fx-font-size: 14px; -fx-text-fill: #94A3B8;");

        HBox caixaBotoes = new HBox(15);
        caixaBotoes.setAlignment(Pos.CENTER_RIGHT);

        Button botaoRecusar = new Button("Recusar");
        botaoRecusar.getStyleClass().add("btn-secondary");
        botaoRecusar.setStyle("-fx-base: #EF4444; -fx-background-color: #EF4444; -fx-text-fill: white;");
        botaoRecusar.setOnAction(e -> {
            aceito.set(false);
            dialogo.close();
        });

        Button botaoPermitir = new Button("Permitir");
        botaoPermitir.getStyleClass().add("btn-primary");
        botaoPermitir.setOnAction(e -> {
            aceito.set(true);
            dialogo.close();
        });

        Region espacador = new Region();
        HBox.setHgrow(espacador, Priority.ALWAYS);

        caixaBotoes.getChildren().addAll(espacador, botaoRecusar, botaoPermitir);

        raiz.getChildren().addAll(titulo, conteudo, caixaBotoes);

        Scene cena = new Scene(raiz);
        cena.setFill(Color.TRANSPARENT);

        java.net.URL urlCss = Main.class.getResource("/com/sicad/styles.css");
        if (urlCss != null) {
            cena.getStylesheets().add(urlCss.toExternalForm());
        }

        PauseTransition limite = new PauseTransition(Duration.seconds(segundosLimite));
        limite.setOnFinished(e -> dialogo.close());

        dialogo.setScene(cena);
        dialogo.centerOnScreen();
        limite.play();
        dialogo.showAndWait();
        limite.stop();

        return aceito.get();
    }

    public static void mostrarDialogoErro(String titleStr, String message) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.TRANSPARENT);

        VBox root = new VBox(20);
        root.getStyleClass().add("card");
        root.setStyle("-fx-background-color: #1E293B; -fx-border-color: #EF4444; -fx-border-width: 1px; -fx-border-radius: 10px; -fx-background-radius: 10px; -fx-effect: dropshadow(three-pass-box, rgba(239,68,68,0.3), 20, 0, 0, 5);");
        root.setPadding(new Insets(30));
        root.setPrefWidth(400);

        Label title = new Label(titleStr);
        title.getStyleClass().add("title");
        title.setStyle("-fx-font-size: 18px; -fx-text-fill: #EF4444; -fx-font-weight: bold;");

        Label content = new Label(message);
        content.setWrapText(true);
        content.setStyle("-fx-font-size: 14px; -fx-text-fill: #E2E8F0;");

        HBox buttonBox = new HBox();
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        Button btnAccept = new Button("OK");
        btnAccept.getStyleClass().add("btn-primary");
        btnAccept.setOnAction(e -> dialog.close());

        buttonBox.getChildren().add(btnAccept);
        root.getChildren().addAll(title, content, buttonBox);

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);

        java.net.URL cssUrl = Main.class.getResource("/com/sicad/styles.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        }

        dialog.setScene(scene);
        dialog.centerOnScreen();
        dialog.showAndWait();
    }

    public static void mostrarDialogoInformativo(String titleStr, String message) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.TRANSPARENT);

        VBox root = new VBox(20);
        root.getStyleClass().add("card");
        root.setStyle("-fx-background-color: #1E293B; -fx-border-color: #3B82F6; -fx-border-width: 1px; -fx-border-radius: 10px; -fx-background-radius: 10px; -fx-effect: dropshadow(three-pass-box, rgba(59,130,246,0.3), 20, 0, 0, 5);");
        root.setPadding(new Insets(30));
        root.setPrefWidth(400);

        Label title = new Label(titleStr);
        title.getStyleClass().add("title");
        title.setStyle("-fx-font-size: 18px; -fx-text-fill: #3B82F6; -fx-font-weight: bold;");

        Label content = new Label(message);
        content.setWrapText(true);
        content.setStyle("-fx-font-size: 14px; -fx-text-fill: #E2E8F0;");

        HBox buttonBox = new HBox();
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        Button btnAccept = new Button("OK");
        btnAccept.getStyleClass().add("btn-primary");
        btnAccept.setOnAction(e -> dialog.close());

        buttonBox.getChildren().add(btnAccept);
        root.getChildren().addAll(title, content, buttonBox);

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);

        java.net.URL cssUrl = Main.class.getResource("/com/sicad/styles.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        }

        dialog.setScene(scene);
        dialog.centerOnScreen();
        dialog.showAndWait();
    }
}
