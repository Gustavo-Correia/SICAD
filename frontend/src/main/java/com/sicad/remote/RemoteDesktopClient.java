package com.sicad.remote;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.PrintWriter;
import java.net.Socket;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.scene.shape.Circle;
import javafx.scene.paint.Color;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.control.Label;
import javafx.scene.input.*;
import java.io.File;
import java.util.List;

public class RemoteDesktopClient {
    private final String targetHost;
    private final int targetPort;
    private final String localId;
    private final String targetId;
    private Socket socket;
    private PrintWriter out;
    private volatile boolean running = true;
    private Circle pingDot;
    private Label pingLbl;
    private ClipboardSync clipboardSync;

    public RemoteDesktopClient(String targetId, String localId) {
        this.targetId = targetId;
        this.localId = localId;
        this.targetHost = null;
        this.targetPort = 0;
    }

    public RemoteDesktopClient(String host, int port) {
        this.targetHost = host;
        this.targetPort = port;
        this.targetId = null;
        this.localId = null;
    }

    public void connectRelay(String serverHost, int serverPort) {
        new Thread(() -> {
            try {
                socket = new Socket(serverHost, serverPort);
                out = new PrintWriter(socket.getOutputStream(), true);
                java.io.InputStream inStream = socket.getInputStream();

                // Request relay connection to target
                out.println("RELAY_CONNECT:" + targetId);

                String response = lerLinha(inStream);
                if (response != null && response.startsWith("ERRO:")) {
                    String reason = response.substring(5);
                    Platform.runLater(() -> mostrarErro("Conexão Recusada", "Alvo não disponível: " + reason));
                    socket.close();
                    return;
                }

                // Now bridged — send AUTH
                out.println("AUTH:" + localId);

                response = lerLinha(inStream);
                if (response == null || !response.startsWith("ACCEPTED")) {
                    String reason = response != null && response.contains(":") ? response.split(":")[1] : "Desconhecida";
                    Platform.runLater(() -> mostrarErro("Conexão Recusada", "O dispositivo alvo recusou a conexão. Motivo: " + reason));
                    socket.close();
                    return;
                }

                // Aceito, inicia a UI e começa a receber vídeo
                Platform.runLater(this::createRemoteWindow);

                DataInputStream dataIn = new DataInputStream(inStream);
                startVideoLoop(dataIn);

            } catch (Exception e) {
                Platform.runLater(() -> mostrarErro("Erro de Conexão", "Não foi possível conectar via relay: " + e.getMessage()));
            }
        }, "remote-client-relay").start();
    }

    public void connect() {
        new Thread(() -> {
            try {
                socket = new Socket(targetHost, targetPort);
                out = new PrintWriter(socket.getOutputStream(), true);
                java.io.InputStream inStream = socket.getInputStream();

                // Envia autenticação
                out.println("AUTH:" + localId);

                // Aguarda resposta
                String response = lerLinha(inStream);
                if (response == null || !response.startsWith("ACCEPTED")) {
                    String reason = response != null && response.contains(":") ? response.split(":")[1] : "Desconhecida";
                    Platform.runLater(() -> mostrarErro("Conexão Recusada", "O dispositivo alvo recusou a conexão. Motivo: " + reason));
                    socket.close();
                    return;
                }

                // Aceito, inicia a UI e começa a receber vídeo
                Platform.runLater(this::createRemoteWindow);
                
                DataInputStream dataIn = new DataInputStream(inStream);
                startVideoLoop(dataIn);

            } catch (Exception e) {
                Platform.runLater(() -> mostrarErro("Erro de Conexão", "Não foi possível conectar a: " + targetHost + ":" + targetPort + "\n" + e.getMessage()));
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
        return baos.toString("UTF-8");
    }

    private ImageView imageView;
    private boolean stageSizeAdjusted = false;

    private void adjustStageSize(Image img) {
        if (stageSizeAdjusted || imageView == null || imageView.getScene() == null) return;
        stageSizeAdjusted = true;
        
        try {
            Stage stage = (Stage) imageView.getScene().getWindow();
            javafx.stage.Screen screen = javafx.stage.Screen.getPrimary();
            javafx.geometry.Rectangle2D bounds = screen.getVisualBounds();
            
            double localWidth = bounds.getWidth();
            double localHeight = bounds.getHeight();
            
            double remoteWidth = img.getWidth();
            double remoteHeight = img.getHeight();
            
            // Define o tamanho inicial como máximo 80% do monitor local
            double maxStageWidth = localWidth * 0.8;
            double maxStageHeight = localHeight * 0.8;
            
            double scale = Math.min(maxStageWidth / remoteWidth, maxStageHeight / remoteHeight);
            scale = Math.min(1.0, scale); // Não amplia além da resolução original
            
            stage.setWidth(remoteWidth * scale);
            stage.setHeight(remoteHeight * scale);
            stage.centerOnScreen();
        } catch (Exception e) {
            System.out.println("Erro ao ajustar tamanho do Stage: " + e.getMessage());
        }
    }

    private javafx.geometry.Point2D mapCoordinates(double mouseX, double mouseY) {
        if (imageView == null || imageView.getImage() == null) {
            return null;
        }
        
        double imageWidth = imageView.getImage().getWidth();
        double imageHeight = imageView.getImage().getHeight();
        
        double viewWidth = imageView.getBoundsInLocal().getWidth();
        double viewHeight = imageView.getBoundsInLocal().getHeight();
        
        double scaleX = viewWidth / imageWidth;
        double scaleY = viewHeight / imageHeight;
        
        // Calcula a escala real mantendo o aspect ratio
        double finalScale = Math.min(scaleX, scaleY);
        
        double actualImageWidth = imageWidth * finalScale;
        double actualImageHeight = imageHeight * finalScale;
        
        // Offset (barras pretas / letterbox ou pillarbox)
        double offsetX = (viewWidth - actualImageWidth) / 2.0;
        double offsetY = (viewHeight - actualImageHeight) / 2.0;
        
        // Mapeia para o pixel da imagem remota
        double mappedX = (mouseX - offsetX) / finalScale;
        double mappedY = (mouseY - offsetY) / finalScale;
        
        // Limita dentro das bordas da imagem remota
        mappedX = Math.max(0, Math.min(imageWidth - 1, mappedX));
        mappedY = Math.max(0, Math.min(imageHeight - 1, mappedY));
        
        return new javafx.geometry.Point2D(mappedX, mappedY);
    }

    private void createRemoteWindow() {
        Stage stage = new Stage();
        stage.setTitle("Acesso Remoto - ID: " + targetId);
        
        imageView = new ImageView();
        imageView.setPreserveRatio(true);

        // Cabeçalho de Status superior
        HBox header = new HBox(15);
        header.setStyle("-fx-background-color: #0E1B34; -fx-padding: 10; -fx-alignment: center-left;");
        
        Label titleLbl = new Label("Sessão Ativa - ID: " + targetId);
        titleLbl.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px;");
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        pingDot = new Circle(4, Color.web("#A9B4D0"));
        pingLbl = new Label("Ping: --- ms");
        pingLbl.setStyle("-fx-text-fill: #A9B4D0; -fx-font-size: 13px;");
        
        header.getChildren().addAll(titleLbl, spacer, pingDot, pingLbl);
        
        StackPane imageContainer = new StackPane(imageView);
        VBox.setVgrow(imageContainer, Priority.ALWAYS);
        
        VBox layout = new VBox();
        layout.getChildren().addAll(header, imageContainer);
        
        Scene scene = new Scene(layout, 1024, 768);
        
        // Responsivo
        imageView.fitWidthProperty().bind(scene.widthProperty());
        imageView.fitHeightProperty().bind(scene.heightProperty().subtract(40)); // Desconta tamanho do header
 
        // Eventos de Mouse com mapeamento de coordenadas corrigido (vinculados ao imageContainer)
        imageContainer.setOnMouseMoved(e -> {
            javafx.geometry.Point2D mapped = mapCoordinates(e.getX(), e.getY());
            if (mapped != null) {
                sendCommand("MOUSE_MOVE:" + (int) mapped.getX() + ":" + (int) mapped.getY());
            }
        });
 
        imageContainer.setOnMouseDragged(e -> {
            javafx.geometry.Point2D mapped = mapCoordinates(e.getX(), e.getY());
            if (mapped != null) {
                sendCommand("MOUSE_MOVE:" + (int) mapped.getX() + ":" + (int) mapped.getY());
            }
        });
 
        imageContainer.setOnMousePressed(e -> {
            int button = 1; // Left
            if (e.isSecondaryButtonDown()) button = 3; // Right
            else if (e.isMiddleButtonDown()) button = 2; // Middle
            sendCommand("MOUSE_PRESS:" + button);
        });
 
        imageContainer.setOnMouseReleased(e -> {
            int button = 1;
            if (e.getButton() == javafx.scene.input.MouseButton.SECONDARY) button = 3;
            else if (e.getButton() == javafx.scene.input.MouseButton.MIDDLE) button = 2;
            sendCommand("MOUSE_RELEASE:" + button);
        });
 
        // Eventos de Teclado
        scene.setOnKeyPressed(e -> {
            int keyCode = e.getCode().getCode();
            sendCommand("KEY_PRESS:" + keyCode);
        });
 
        scene.setOnKeyReleased(e -> {
            int keyCode = e.getCode().getCode();
            sendCommand("KEY_RELEASE:" + keyCode);
        });

        // Configura ClipboardSync no Viewer
        clipboardSync = new ClipboardSync(out, null, false);

        // Inicia Ping Heartbeat
        startPingHeartbeat();

        // Configura Drag & Drop de arquivos
        configurarDragAndDrop(imageContainer);
 
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
                                adjustStageSize(img);
                            }
                        });
                    } else if (length == -1) {
                        // Resposta do Ping RTT
                        long originalTimestamp = dataIn.readLong();
                        long rtt = System.currentTimeMillis() - originalTimestamp;
                        atualizarPing(rtt);
                    } else if (length == -2) {
                        // Sincronização do Clipboard vindo do Host
                        int textLen = dataIn.readInt();
                        byte[] textBytes = new byte[textLen];
                        dataIn.readFully(textBytes);
                        String text = new String(textBytes, "UTF-8");
                        if (clipboardSync != null) {
                            clipboardSync.aplicarTextoRemoto(text);
                        }
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

    private void startPingHeartbeat() {
        new Thread(() -> {
            while (running && socket != null && !socket.isClosed()) {
                try {
                    sendCommand("PING_CHECK:" + System.currentTimeMillis());
                    Thread.sleep(2000); // Checa latência a cada 2 segundos
                } catch (Exception e) {
                    break;
                }
            }
        }, "viewer-ping-heartbeat").start();
    }

    private void atualizarPing(long rtt) {
        Platform.runLater(() -> {
            if (pingLbl != null && pingDot != null) {
                pingLbl.setText("Ping: " + rtt + " ms");
                if (rtt < 70) {
                    pingDot.setFill(Color.web("#10B981")); // Verde
                } else if (rtt < 180) {
                    pingDot.setFill(Color.web("#F59E0B")); // Amarelo
                } else {
                    pingDot.setFill(Color.web("#EF4444")); // Vermelho
                }
            }
        });
    }

    private void configurarDragAndDrop(StackPane container) {
        container.setOnDragOver(event -> {
            Dragboard db = event.getDragboard();
            if (db.hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });

        container.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                success = true;
                List<File> files = db.getFiles();
                for (File file : files) {
                    System.out.println("[Drag & Drop] Transferência solicitada: " + file.getAbsolutePath());
                    Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.INFORMATION);
                        alert.setTitle("Transferência de Arquivos");
                        alert.setHeaderText("Simulação de Envio de Arquivo");
                        alert.setContentText("Arquivo detectado: " + file.getName() + " (" + (file.length() / 1024) + " KB).\n\n"
                                + "A arquitetura de canal TCP paralelo exclusivo para stream de dados está totalmente especificada!");
                        alert.show();
                    });
                }
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }

    private void disconnect() {
        running = false;
        if (clipboardSync != null) {
            clipboardSync.stop();
        }
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void mostrarErro(String header, String content) {
        com.sicad.DialogHelper.showErrorDialog(header, content);
    }
}
