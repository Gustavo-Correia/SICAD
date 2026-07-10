package com.sicad;

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
import java.util.concurrent.atomic.AtomicBoolean;

public class DialogHelper {

    public static boolean showConnectionRequestDialog(String deviceId) {
        AtomicBoolean accepted = new AtomicBoolean(false);

        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.TRANSPARENT);

        VBox root = new VBox(20);
        root.getStyleClass().add("card");
        root.setStyle("-fx-background-color: #1E293B; -fx-border-color: #334155; -fx-border-width: 1px; -fx-border-radius: 10px; -fx-background-radius: 10px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.8), 20, 0, 0, 10);");
        root.setPadding(new Insets(30));
        root.setPrefWidth(400);

        Label title = new Label("Solicitação de Acesso Remoto");
        title.getStyleClass().add("title");
        title.setStyle("-fx-font-size: 18px; -fx-text-fill: white;");

        Label content = new Label("O dispositivo " + deviceId + " deseja controlar sua máquina.\n\nVocê permite esta conexão?");
        content.setWrapText(true);
        content.setStyle("-fx-font-size: 14px; -fx-text-fill: #94A3B8;");

        HBox buttonBox = new HBox(15);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        Button btnReject = new Button("Recusar");
        btnReject.getStyleClass().add("btn-secondary");
        btnReject.setStyle("-fx-base: #EF4444; -fx-background-color: #EF4444; -fx-text-fill: white;");
        btnReject.setOnAction(e -> {
            accepted.set(false);
            dialog.close();
        });

        Button btnAccept = new Button("Permitir");
        btnAccept.getStyleClass().add("btn-primary");
        btnAccept.setOnAction(e -> {
            accepted.set(true);
            dialog.close();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        buttonBox.getChildren().addAll(spacer, btnReject, btnAccept);

        root.getChildren().addAll(title, content, buttonBox);

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        
        // Carrega o CSS principal
        java.net.URL cssUrl = Main.class.getResource("/com/sicad/styles.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        }

        dialog.setScene(scene);
        dialog.centerOnScreen();
        dialog.showAndWait();

        return accepted.get();
    }

    public static void showErrorDialog(String titleStr, String message) {
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

    public static void showInfoDialog(String titleStr, String message) {
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
