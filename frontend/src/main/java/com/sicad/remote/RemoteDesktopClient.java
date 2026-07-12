package com.sicad.remote;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Arrays;
import java.util.Base64;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public class RemoteDesktopClient {
    private final String targetHost;
    private final int targetPort;
    private final String localId;
    private final String targetId;
    private final boolean useRelay;
    private Socket socket;
    private PrintWriter out;
    private volatile boolean running = true;

    public RemoteDesktopClient(String targetId, String localId) {
        this.targetId = targetId;
        this.localId = localId;
        this.useRelay = true;
        this.targetHost = null;
        this.targetPort = 0;
    }

    public RemoteDesktopClient(String targetIp, int targetPort, String localId) {
        this.targetHost = targetIp;
        this.targetPort = targetPort;
        this.localId = localId;
        this.targetId = null;
        this.useRelay = false;
    }

    public void connectRelay(String serverHost, int serverPort) {
        new Thread(() -> {
            try {
                socket = new Socket(serverHost, serverPort);
                out = new PrintWriter(socket.getOutputStream(), true);
                java.io.InputStream inStream = socket.getInputStream();

                // Solicita conexão relay com o alvo
                out.println("RELAY_CONNECT:" + targetId);

                String response = lerLinha(inStream);
                if (response != null && response.startsWith("ERRO:")) {
                    String reason = response.substring(5);
                    Platform.runLater(() -> mostrarErro("Conexão Recusada", "Alvo não disponível: " + reason));
                    socket.close();
                    return;
                }

                // Bridge estabelecida — envia AUTH
                out.println("AUTH:" + localId);

                response = lerLinha(inStream);
                if (response == null || !response.startsWith("ACCEPTED")) {
                    String reason = response != null && response.contains(":")
                            ? response.split(":")[1] : "Desconhecida";
                    Platform.runLater(() -> mostrarErro("Conexão Recusada",
                            "O dispositivo alvo recusou a conexão. Motivo: " + reason));
                    socket.close();
                    return;
                }

                // Aceito — abre janela e inicia recepção de vídeo
                Platform.runLater(this::createRemoteWindow);

                DataInputStream dataIn = new DataInputStream(inStream);
                startVideoLoop(dataIn);

            } catch (Exception e) {
                Platform.runLater(() -> mostrarErro("Erro de Conexão",
                        "Não foi possível conectar via relay: " + e.getMessage()));
            }
        }, "remote-client-relay").start();
    }

    public void connect() {
        new Thread(() -> {
            try {
                socket = new Socket(targetHost, targetPort);
                out = new PrintWriter(socket.getOutputStream(), true);
                java.io.InputStream inStream = socket.getInputStream();

                out.println("AUTH:" + localId);

                String response = lerLinha(inStream);
                if (response == null || !response.startsWith("ACCEPTED")) {
                    String reason = response != null && response.contains(":")
                            ? response.split(":")[1] : "Desconhecida";
                    Platform.runLater(() -> mostrarErro("Conexão Recusada",
                            "O dispositivo alvo recusou a conexão. Motivo: " + reason));
                    socket.close();
                    return;
                }

                Platform.runLater(this::createRemoteWindow);

                DataInputStream dataIn = new DataInputStream(inStream);
                startVideoLoop(dataIn);

            } catch (Exception e) {
                Platform.runLater(() -> mostrarErro("Erro de Conexão",
                        "Não foi possível conectar a: " + targetHost + ":" + targetPort
                        + "\n" + e.getMessage()));
            }
        }, "remote-client-connect").start();
    }

    private String lerLinha(java.io.InputStream in) throws Exception {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1 && b != '\n') {
            baos.write(b);
        }
        if (b == -1 && baos.size() == 0) {
            return null;
        }
        return baos.toString("UTF-8").trim();
    }

    private ImageView imageView;
    private Stage remoteStage;

    private void createRemoteWindow() {
        remoteStage = new Stage();
        String windowTitle = "Acesso Remoto — "
                + (targetId != null ? targetId : targetHost + ":" + targetPort);
        remoteStage.setTitle(windowTitle);

        imageView = new ImageView();
        imageView.setPreserveRatio(true);

        // ── Toolbar ──────────────────────────────────────────────────────────
        Button sendFileBtn = new Button("📁  Enviar Arquivo");
        sendFileBtn.setStyle(
                "-fx-background-color: #1A305A; -fx-text-fill: #A9B4D0;" +
                "-fx-background-radius: 5; -fx-padding: 5 14; -fx-cursor: hand; -fx-font-size: 12px;");
        sendFileBtn.setOnMouseEntered(e -> sendFileBtn.setStyle(
                "-fx-background-color: #2E7BFF; -fx-text-fill: #FFFFFF;" +
                "-fx-background-radius: 5; -fx-padding: 5 14; -fx-cursor: hand; -fx-font-size: 12px;"));
        sendFileBtn.setOnMouseExited(e -> sendFileBtn.setStyle(
                "-fx-background-color: #1A305A; -fx-text-fill: #A9B4D0;" +
                "-fx-background-radius: 5; -fx-padding: 5 14; -fx-cursor: hand; -fx-font-size: 12px;"));
        sendFileBtn.setOnAction(e -> enviarArquivo(remoteStage, windowTitle));

        HBox toolbar = new HBox(sendFileBtn);
        toolbar.setStyle("-fx-background-color: #081225; -fx-padding: 6 10; -fx-alignment: CENTER_LEFT;");

        // ── Layout ───────────────────────────────────────────────────────────
        StackPane videoPane = new StackPane(imageView);
        VBox root = new VBox(toolbar, videoPane);
        VBox.setVgrow(videoPane, Priority.ALWAYS);

        Scene scene = new Scene(root, 1024, 768);

        imageView.fitWidthProperty().bind(videoPane.widthProperty());
        imageView.fitHeightProperty().bind(videoPane.heightProperty());

        // ── Eventos de Mouse (no videoPane para coordenadas corretas) ─────────
        videoPane.setOnMouseMoved(e -> {
            if (imageView.getImage() != null) {
                double scaleX = imageView.getImage().getWidth()
                        / Math.max(1, imageView.getBoundsInLocal().getWidth());
                double scaleY = imageView.getImage().getHeight()
                        / Math.max(1, imageView.getBoundsInLocal().getHeight());
                sendCommand("MOUSE_MOVE:" + (int)(e.getX() * scaleX) + ":" + (int)(e.getY() * scaleY));
            }
        });

        videoPane.setOnMouseDragged(e -> {
            if (imageView.getImage() != null) {
                double scaleX = imageView.getImage().getWidth()
                        / Math.max(1, imageView.getBoundsInLocal().getWidth());
                double scaleY = imageView.getImage().getHeight()
                        / Math.max(1, imageView.getBoundsInLocal().getHeight());
                sendCommand("MOUSE_MOVE:" + (int)(e.getX() * scaleX) + ":" + (int)(e.getY() * scaleY));
            }
        });

        videoPane.setOnMousePressed(e -> {
            int button = 1;
            if (e.isSecondaryButtonDown()) button = 3;
            else if (e.isMiddleButtonDown()) button = 2;
            sendCommand("MOUSE_PRESS:" + button);
        });

        videoPane.setOnMouseReleased(e -> {
            int button = 1;
            if (e.getButton() == javafx.scene.input.MouseButton.SECONDARY) button = 3;
            else if (e.getButton() == javafx.scene.input.MouseButton.MIDDLE) button = 2;
            sendCommand("MOUSE_RELEASE:" + button);
        });

        // ── Eventos de Teclado (na scene) ────────────────────────────────────
        scene.setOnKeyPressed(e -> sendCommand("KEY_PRESS:" + e.getCode().getCode()));
        scene.setOnKeyReleased(e -> sendCommand("KEY_RELEASE:" + e.getCode().getCode()));

        remoteStage.setOnCloseRequest(e -> disconnect());
        remoteStage.setScene(scene);
        remoteStage.show();
    }

    /**
     * Abre um FileChooser e envia o arquivo selecionado para o host em chunks Base64.
     * Protocolo: FILE_START → FILE_CHUNK (repetido) → FILE_END
     */
    private void enviarArquivo(Stage ownerStage, String originalTitle) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Selecionar arquivo para enviar ao dispositivo remoto");
        File file = fileChooser.showOpenDialog(ownerStage);
        if (file == null) return;

        new Thread(() -> {
            try {
                long fileSize = file.length();
                String fileName = file.getName();

                Platform.runLater(() -> ownerStage.setTitle("Enviando: " + fileName + " …"));

                // Cabeçalho: informa nome e tamanho
                sendCommand("FILE_START:" + fileName + ":" + fileSize);

                // Envio em chunks de 32KB
                byte[] buffer = new byte[32 * 1024];
                long bytesSent = 0;

                try (FileInputStream fis = new FileInputStream(file)) {
                    int read;
                    while ((read = fis.read(buffer)) != -1 && running) {
                        byte[] chunk = (read == buffer.length) ? buffer : Arrays.copyOf(buffer, read);
                        sendCommand("FILE_CHUNK:" + Base64.getEncoder().encodeToString(chunk));
                        bytesSent += read;

                        final int pct = (int)(bytesSent * 100 / fileSize);
                        Platform.runLater(() -> ownerStage.setTitle(
                                "Enviando: " + fileName + " (" + pct + "%)"));
                    }
                }

                sendCommand("FILE_END");
                Platform.runLater(() -> ownerStage.setTitle(originalTitle));
                System.out.println("Arquivo enviado: " + fileName + " (" + fileSize + " bytes)");

            } catch (Exception e) {
                Platform.runLater(() -> mostrarErro("Erro ao enviar arquivo", e.getMessage()));
            }
        }, "file-sender").start();
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
                    Platform.runLater(() -> mostrarErro("Conexão Perdida", "A sessão remota foi encerrada."));
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
