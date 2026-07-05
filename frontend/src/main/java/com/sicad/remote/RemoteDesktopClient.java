package com.sicad.remote;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

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
        System.out.println("RemoteDesktopClient criado: " + targetIp + ":" + port + " id=" + clientId);
    }

    public void connect() {
        new Thread(() -> {
            try {
                System.out.println("[CLIENT] Conectando a " + targetIp + ":" + port + "...");
                socket = new Socket(targetIp, port);
                System.out.println("[CLIENT] Socket conectado!");

                InputStream rawIn = socket.getInputStream();
                out = new PrintWriter(socket.getOutputStream(), true);

                System.out.println("[CLIENT] Enviando AUTH:" + clientId);
                out.println("AUTH:" + clientId);
                out.flush();
                System.out.println("[CLIENT] AUTH enviado, aguardando resposta...");

                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                int b;
                while ((b = rawIn.read()) != -1 && b != '\n') {
                    buf.write(b);
                }
                String response = new String(buf.toByteArray(), StandardCharsets.UTF_8).trim();
                System.out.println("[CLIENT] Resposta recebida: '" + response + "'");

                if (b == -1) {
                    System.out.println("[CLIENT] ERRO: conexao fechada pelo servidor");
                    showError("Conexão rejeitada", "Conexão fechada pelo servidor.");
                    close();
                    return;
                }

                if (response.startsWith("REJECTED")) {
                    String reason = response.contains(":") ? response.split(":", 2)[1] : "Acesso negado";
                    System.out.println("[CLIENT] REJEITADO: " + reason);
                    showError("Conexão rejeitada", reason);
                    close();
                    return;
                }

                if (!"ACCEPTED".equals(response)) {
                    System.out.println("[CLIENT] ERRO: resposta inesperada: " + response);
                    showError("Conexão rejeitada", "Resposta inesperada: " + response);
                    close();
                    return;
                }

                connected = true;
                dataIn = new DataInputStream(rawIn);
                System.out.println("[CLIENT] Aceito! Iniciando recebimento de tela...");

                Platform.runLater(this::createUI);
                receiveScreen();

            } catch (Exception e) {
                System.err.println("[CLIENT] Erro: " + e.getMessage());
                e.printStackTrace();
                showError("Erro de conexão", "Não foi possível conectar: " + e.getMessage());
                close();
            }
        }, "remote-client-connect").start();
    }

    private void createUI() {
        System.out.println("[CLIENT] Criando UI da sessão remota...");
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
        System.out.println("[CLIENT] UI da sessão remota exibida.");
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
            out.flush();
        }
    }

    private void receiveScreen() {
        new Thread(() -> {
            try {
                int frameCount = 0;
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

                    frameCount++;
                    if (frameCount % 30 == 0) {
                        System.out.println("[CLIENT] Recebidos " + frameCount + " frames (" + length + " bytes)");
                    }

                    ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes);
                    Image frame = new Image(bais);
                    Platform.runLater(() -> {
                        if (imageView != null) {
                            imageView.setImage(frame);
                        }
                    });
                }
            } catch (Exception e) {
                System.out.println("[CLIENT] Recebimento encerrado: " + e.getMessage());
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
                System.out.println("[CLIENT] Socket fechado.");
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
