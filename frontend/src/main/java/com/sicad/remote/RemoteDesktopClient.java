package com.sicad.remote;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class RemoteDesktopClient {
    private final String targetHost;
    private final int targetPort;
    private final String localId;
    private Socket socket;
    private PrintWriter out;
    private volatile boolean running = true;

    public RemoteDesktopClient(String targetIp, int targetPort, String localId) {
        this.targetHost = targetIp;
        this.targetPort = targetPort;
        this.localId = localId;
    }

    public void connect() {
        new Thread(() -> {
            try {
                socket = new Socket(targetHost, targetPort);
                out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                // Envia autenticação
                out.println("AUTH:" + localId);

                // Aguarda resposta
                String response = in.readLine();
                if (response == null || !response.startsWith("ACCEPTED")) {
                    String reason = response != null && response.contains(":") ? response.split(":")[1] : "Desconhecida";
                    Platform.runLater(() -> mostrarErro("Conexão Recusada", "O dispositivo alvo recusou a conexão. Motivo: " + reason));
                    socket.close();
                    return;
                }

                // Aceito, inicia a UI e começa a receber vídeo
                Platform.runLater(this::createRemoteWindow);
                
                DataInputStream dataIn = new DataInputStream(socket.getInputStream());
                startVideoLoop(dataIn);

            } catch (Exception e) {
                Platform.runLater(() -> mostrarErro("Erro de Conexão", "Não foi possível conectar a: " + targetHost + ":" + targetPort + "\n" + e.getMessage()));
            }
        }, "remote-client-connect").start();
    }

    private ImageView imageView;

    private void createRemoteWindow() {
        Stage stage = new Stage();
        stage.setTitle("Acesso Remoto - " + targetHost + ":" + targetPort);
        
        imageView = new ImageView();
        imageView.setPreserveRatio(true);
        
        StackPane root = new StackPane(imageView);
        Scene scene = new Scene(root, 1024, 768);
        
        // Responsivo
        imageView.fitWidthProperty().bind(scene.widthProperty());
        imageView.fitHeightProperty().bind(scene.heightProperty());

        // Eventos de Mouse
        scene.setOnMouseMoved(e -> {
            // Mapeia a coordenada da tela local para a imagem
            if (imageView.getImage() != null) {
                double scaleX = imageView.getImage().getWidth() / imageView.getBoundsInLocal().getWidth();
                double scaleY = imageView.getImage().getHeight() / imageView.getBoundsInLocal().getHeight();
                int x = (int) (e.getX() * scaleX);
                int y = (int) (e.getY() * scaleY);
                sendCommand("MOUSE_MOVE:" + x + ":" + y);
            }
        });

        scene.setOnMouseDragged(e -> {
            if (imageView.getImage() != null) {
                double scaleX = imageView.getImage().getWidth() / imageView.getBoundsInLocal().getWidth();
                double scaleY = imageView.getImage().getHeight() / imageView.getBoundsInLocal().getHeight();
                int x = (int) (e.getX() * scaleX);
                int y = (int) (e.getY() * scaleY);
                sendCommand("MOUSE_MOVE:" + x + ":" + y);
            }
        });

        scene.setOnMousePressed(e -> {
            int button = 1; // Left
            if (e.isSecondaryButtonDown()) button = 3; // Right
            else if (e.isMiddleButtonDown()) button = 2; // Middle
            sendCommand("MOUSE_PRESS:" + button);
        });

        scene.setOnMouseReleased(e -> {
            int button = 1;
            if (e.getButton() == javafx.scene.input.MouseButton.SECONDARY) button = 3;
            else if (e.getButton() == javafx.scene.input.MouseButton.MIDDLE) button = 2;
            sendCommand("MOUSE_RELEASE:" + button);
        });

        // Eventos de Teclado
        scene.setOnKeyPressed(e -> {
            int keyCode = e.getCode().getCode(); // Nota: o mapeamento exato JavaFX -> AWT pode precisar de ajustes finos
            sendCommand("KEY_PRESS:" + keyCode);
        });

        scene.setOnKeyReleased(e -> {
            int keyCode = e.getCode().getCode();
            sendCommand("KEY_RELEASE:" + keyCode);
        });

        stage.setOnCloseRequest(e -> disconnect());
        stage.setScene(scene);
        stage.show();
    }

    private void startVideoLoop(DataInputStream dataIn) {
        new Thread(() -> {
            try {
                while (running) {
                    int length = dataIn.readInt();
                    if (length > 0) {
                        byte[] imageBytes = new byte[length];
                        dataIn.readFully(imageBytes);
                        
                        Image img = new Image(new ByteArrayInputStream(imageBytes));
                        Platform.runLater(() -> {
                            if (imageView != null) {
                                imageView.setImage(img);
                            }
                        });
                    }
                }
            } catch (Exception e) {
                if (running) {
                    System.out.println("Conexão de vídeo encerrada: " + e.getMessage());
                    Platform.runLater(() -> {
                        mostrarErro("Conexão Perdida", "A sessão remota foi encerrada.");
                    });
                }
                disconnect();
            }
        }, "remote-client-video").start();
    }

    private void sendCommand(String command) {
        if (out != null && running) {
            out.println(command);
        }
    }

    private void disconnect() {
        running = false;
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void mostrarErro(String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Erro");
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.show();
    }
}
