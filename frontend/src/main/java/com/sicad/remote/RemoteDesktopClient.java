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
        
        StackPane root = new StackPane(imageView);
        Scene scene = new Scene(root, 1024, 768);
        
        // Responsivo
        imageView.fitWidthProperty().bind(scene.widthProperty());
        imageView.fitHeightProperty().bind(scene.heightProperty());
 
        // Eventos de Mouse com mapeamento de coordenadas corrigido
        scene.setOnMouseMoved(e -> {
            javafx.geometry.Point2D mapped = mapCoordinates(e.getX(), e.getY());
            if (mapped != null) {
                sendCommand("MOUSE_MOVE:" + (int) mapped.getX() + ":" + (int) mapped.getY());
            }
        });
 
        scene.setOnMouseDragged(e -> {
            javafx.geometry.Point2D mapped = mapCoordinates(e.getX(), e.getY());
            if (mapped != null) {
                sendCommand("MOUSE_MOVE:" + (int) mapped.getX() + ":" + (int) mapped.getY());
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
            int keyCode = e.getCode().getCode();
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
                                adjustStageSize(img);
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
