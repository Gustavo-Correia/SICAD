package com.sicad.remote;

import java.awt.MouseInfo;
import java.awt.Point;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.PrintWriter;
import java.net.Socket;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class RemoteDesktopClient {
    private final String targetIp;
    private final int port;
    private final String clientId;
    private Socket socket;
    private PrintWriter out;
    private DataInputStream dataIn;
    private volatile boolean connected = false;
    private ImageView imageView;
    private Stage stage;

    public RemoteDesktopClient(String targetIp, int port, String clientId) {
        this.targetIp = targetIp;
        this.port = port;
        this.clientId = clientId;
    }

    public void connect() {
        new Thread(() -> {
            try {
                socket = new Socket(targetIp, port);
                out = new PrintWriter(socket.getOutputStream(), true);
                dataIn = new DataInputStream(socket.getInputStream());

                out.println("AUTH:" + clientId);

                String response = dataIn.readLine();
                if (response == null) {
                    showError("Conexão rejeitada", "Resposta vazia do servidor.");
                    close();
                    return;
                }

                if (response.startsWith("REJECTED")) {
                    String reason = response.contains(":") ? response.split(":", 2)[1] : "Acesso negado";
                    showError("Conexão rejeitada", reason);
                    close();
                    return;
                }

                if (!"ACCEPTED".equals(response)) {
                    showError("Conexão rejeitada", "Resposta inesperada: " + response);
                    close();
                    return;
                }

                connected = true;

                Platform.runLater(this::createUI);

                receiveScreen();

            } catch (Exception e) {
                showError("Erro de conexão", "Não foi possível conectar: " + e.getMessage());
                close();
            }
        }, "remote-client-connect").start();
    }

    private void createUI() {
        stage = new Stage();
        stage.setTitle("Acesso Remoto - " + clientId);
        stage.setMaximized(true);

        imageView = new ImageView();
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);

        StackPane root = new StackPane(imageView);
        root.setStyle("-fx-background-color: black;");

        Scene scene = new Scene(root);

        scene.setOnMouseMoved(this::handleMouseMoved);
        scene.setOnMousePressed(this::handleMousePressed);
        scene.setOnMouseReleased(this::handleMouseReleased);
        scene.setOnKeyPressed(e -> handleKeyPressed(e.getCode(), e.isShiftDown()));
        scene.setOnKeyReleased(e -> handleKeyReleased(e.getCode(), e.isShiftDown()));

        stage.setScene(scene);
        stage.setOnCloseRequest(e -> close());
        stage.show();
    }

    private void handleMouseMoved(MouseEvent event) {
        if (!connected) return;
        sendCommand("MOUSE_MOVE:" + (int) event.getX() + ":" + (int) event.getY());
    }

    private void handleMousePressed(MouseEvent event) {
        if (!connected) return;
        sendCommand("MOUSE_PRESS:" + getButtonId(event.getButton()));
    }

    private void handleMouseReleased(MouseEvent event) {
        if (!connected) return;
        sendCommand("MOUSE_RELEASE:" + getButtonId(event.getButton()));
    }

    private int getButtonId(MouseButton button) {
        if (button == MouseButton.PRIMARY) return 1;
        if (button == MouseButton.MIDDLE) return 2;
        if (button == MouseButton.SECONDARY) return 3;
        return 1;
    }

    private void handleKeyPressed(KeyCode code, boolean shift) {
        if (!connected) return;
        int keyCode = mapKeyCode(code, shift);
        if (keyCode > 0) {
            sendCommand("KEY_PRESS:" + keyCode);
        }
    }

    private void handleKeyReleased(KeyCode code, boolean shift) {
        if (!connected) return;
        int keyCode = mapKeyCode(code, shift);
        if (keyCode > 0) {
            sendCommand("KEY_RELEASE:" + keyCode);
        }
    }

    private int mapKeyCode(KeyCode code, boolean shift) {
        int c = code.getCode();

        if (code.isLetterKey()) {
            return shift ? c : c + 32;
        }

        return c;
    }

    private void sendCommand(String command) {
        if (out != null) {
            out.println(command);
        }
    }

    private void receiveScreen() {
        new Thread(() -> {
            try {
                while (connected) {
                    int length = dataIn.readInt();
                    byte[] imageBytes = new byte[length];
                    int totalRead = 0;
                    while (totalRead < length) {
                        int read = dataIn.read(imageBytes, totalRead, length - totalRead);
                        if (read == -1) break;
                        totalRead += read;
                    }

                    if (totalRead < length) break;

                    ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes);
                    Image frame = new Image(bais);
                    Platform.runLater(() -> {
                        if (imageView != null) {
                            imageView.setImage(frame);
                        }
                    });
                }
            } catch (Exception e) {
                System.out.println("RemoteDesktopClient - recebimento de tela encerrado: " + e.getMessage());
            } finally {
                Platform.runLater(() -> {
                    if (stage != null) {
                        stage.close();
                    }
                });
                close();
            }
        }, "remote-client-receive").start();
    }

    private void showError(String title, String message) {
        Platform.runLater(() -> {
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(title);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    public void close() {
        connected = false;
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        Platform.runLater(() -> {
            if (stage != null) {
                stage.close();
            }
        });
    }
}
