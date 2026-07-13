package com.sicad.remote;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.scene.shape.Circle;
import javafx.scene.paint.Color;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.geometry.Pos;
import javafx.scene.input.*;

public class ClienteRemotoUI implements ClienteRemotoListener {

    private final ClienteDesktopRemoto socketHandler;
    private ImageView imageView;
    private boolean stageSizeAdjusted = false;
    private Circle pingDot;
    private Label pingLbl;
    private final AtomicReference<Image> imagemPendente = new AtomicReference<>();
    private final AtomicBoolean atualizacaoImagemAgendada = new AtomicBoolean();
    private final AtomicBoolean encerramentoNotificado = new AtomicBoolean();

    public ClienteRemotoUI(ClienteDesktopRemoto socketHandler) {
        this.socketHandler = socketHandler;
    }

    @Override
    public void onConexaoEstabelecida() {
        Platform.runLater(this::criarJanelaRemota);
    }

    @Override
    public void onFrameRecebido(byte[] dadosImagem) {
        Image imagem = new Image(new ByteArrayInputStream(dadosImagem));
        publicarImagemMaisRecente(imagem);
    }

    @Override
    public void onPingAtualizado(long rtt) {
        Platform.runLater(() -> atualizarPing(rtt));
    }

    @Override
    public void onConexaoEncerrada(String canal, String mensagem) {
        if (socketHandler.isEmExecucao() && encerramentoNotificado.compareAndSet(false, true)) {
            Platform.runLater(() -> mostrarErro("Conexão Perdida", mensagem));
        }
    }

    @Override
    public void onTextoAreaTransferencia(String texto) {
    }

    private void ajustarTamanhoJanela(Image img) {
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

            double maxStageWidth = localWidth * 0.8;
            double maxStageHeight = localHeight * 0.8;

            double scale = Math.min(maxStageWidth / remoteWidth, maxStageHeight / remoteHeight);
            scale = Math.min(1.0, scale);

            stage.setWidth(remoteWidth * scale);
            stage.setHeight(remoteHeight * scale);
            stage.centerOnScreen();
        } catch (Exception e) {
            System.out.println("Erro ao ajustar tamanho do Stage: " + e.getMessage());
        }
    }

    private javafx.geometry.Point2D mapearCoordenadas(double posicaoCenaX, double posicaoCenaY) {
        if (imageView == null || imageView.getImage() == null) {
            return null;
        }

        javafx.geometry.Point2D pontoLocal = imageView.sceneToLocal(posicaoCenaX, posicaoCenaY);
        javafx.geometry.Bounds limitesImagem = imageView.getBoundsInLocal();
        if (!limitesImagem.contains(pontoLocal)) {
            return null;
        }

        double larguraReferencia = socketHandler.getLarguraTelaRemota() > 0
                ? socketHandler.getLarguraTelaRemota() : imageView.getImage().getWidth();
        double alturaReferencia = socketHandler.getAlturaTelaRemota() > 0
                ? socketHandler.getAlturaTelaRemota() : imageView.getImage().getHeight();
        double proporcaoX = (pontoLocal.getX() - limitesImagem.getMinX()) / limitesImagem.getWidth();
        double proporcaoY = (pontoLocal.getY() - limitesImagem.getMinY()) / limitesImagem.getHeight();
        double posicaoRemotaX = Math.max(0, Math.min(larguraReferencia - 1,
                proporcaoX * larguraReferencia));
        double posicaoRemotaY = Math.max(0, Math.min(alturaReferencia - 1,
                proporcaoY * alturaReferencia));

        return new javafx.geometry.Point2D(posicaoRemotaX, posicaoRemotaY);
    }

    private void criarJanelaRemota() {
        Stage stage = new Stage();
        stage.setTitle("Acesso Remoto - ID: " + socketHandler.getIdAlvo());

        imageView = new ImageView();
        imageView.setPreserveRatio(true);

        HBox header = new HBox(15);
        header.setStyle("-fx-background-color: rgba(15, 23, 42, 0.85); -fx-padding: 10 20; -fx-background-radius: 30; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.4), 10, 0, 0, 5);");
        header.setAlignment(Pos.CENTER);
        header.setMaxWidth(700);
        header.setMaxHeight(50);

        Circle statusDot = new Circle(4, Color.web("#10B981"));
        Label titleLbl = new Label("Conectado a " + socketHandler.getIdAlvo());
        titleLbl.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px; -fx-font-family: 'Inter', sans-serif;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnFullscreen = new Button("Tela Cheia");
        btnFullscreen.setStyle("-fx-background-color: transparent; -fx-text-fill: #94A3B8; -fx-cursor: hand; -fx-font-weight: bold;");
        btnFullscreen.setOnMouseEntered(e -> btnFullscreen.setStyle("-fx-background-color: rgba(255,255,255,0.1); -fx-text-fill: white; -fx-cursor: hand; -fx-font-weight: bold; -fx-background-radius: 5;"));
        btnFullscreen.setOnMouseExited(e -> btnFullscreen.setStyle("-fx-background-color: transparent; -fx-text-fill: #94A3B8; -fx-cursor: hand; -fx-font-weight: bold;"));
        btnFullscreen.setOnAction(e -> stage.setFullScreen(!stage.isFullScreen()));

        Button btnDisconnect = new Button("Desconectar");
        btnDisconnect.setStyle("-fx-background-color: #EF4444; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 15; -fx-padding: 5 15; -fx-cursor: hand;");
        btnDisconnect.setOnAction(e -> {
            socketHandler.desconectar();
            stage.close();
        });

        HBox pingBadge = new HBox(8);
        pingBadge.setAlignment(Pos.CENTER);
        pingBadge.setStyle("-fx-background-color: rgba(0,0,0,0.3); -fx-padding: 5 12; -fx-background-radius: 15;");
        pingDot = new Circle(4, Color.web("#A9B4D0"));
        pingLbl = new Label("Ping: --- ms");
        pingLbl.setStyle("-fx-text-fill: #A9B4D0; -fx-font-size: 12px; -fx-font-weight: bold;");
        pingBadge.getChildren().addAll(pingDot, pingLbl);

        header.getChildren().addAll(statusDot, titleLbl, spacer, pingBadge, btnFullscreen, btnDisconnect);

        StackPane imageContainer = new StackPane(imageView);
        imageContainer.setStyle("-fx-background-color: #000000;");

        StackPane root = new StackPane();
        root.getChildren().addAll(imageContainer, header);
        StackPane.setAlignment(header, Pos.TOP_CENTER);
        StackPane.setMargin(header, new javafx.geometry.Insets(20, 0, 0, 0));

        header.setOpacity(0);
        root.setOnMouseMoved(e -> {
            if (e.getY() < 100) {
                header.setOpacity(1);
            } else {
                header.setOpacity(0);
            }
        });

        Scene scene = new Scene(root, 1024, 768);

        imageView.fitWidthProperty().bind(scene.widthProperty());
        imageView.fitHeightProperty().bind(scene.heightProperty());

        imageContainer.setOnMouseMoved(e -> {
            javafx.geometry.Point2D mapped = mapearCoordenadas(e.getSceneX(), e.getSceneY());
            if (mapped != null) {
                socketHandler.enviarComando("MOUSE_MOVE:" + (int) mapped.getX() + ":" + (int) mapped.getY());
            }
        });

        imageContainer.setOnMouseDragged(e -> {
            javafx.geometry.Point2D mapped = mapearCoordenadas(e.getSceneX(), e.getSceneY());
            if (mapped != null) {
                socketHandler.enviarComando("MOUSE_MOVE:" + (int) mapped.getX() + ":" + (int) mapped.getY());
            }
        });

        imageContainer.setOnMousePressed(e -> {
            int button = 1;
            if (e.isSecondaryButtonDown()) button = 3;
            else if (e.isMiddleButtonDown()) button = 2;
            socketHandler.enviarComando("MOUSE_PRESS:" + button);
        });

        imageContainer.setOnMouseReleased(e -> {
            int button = 1;
            if (e.getButton() == MouseButton.SECONDARY) button = 3;
            else if (e.getButton() == MouseButton.MIDDLE) button = 2;
            socketHandler.enviarComando("MOUSE_RELEASE:" + button);
        });

        scene.setOnKeyPressed(e -> {
            int keyCode = e.getCode().getCode();
            socketHandler.enviarComando("KEY_PRESS:" + keyCode);
        });

        scene.setOnKeyReleased(e -> {
            int keyCode = e.getCode().getCode();
            socketHandler.enviarComando("KEY_RELEASE:" + keyCode);
        });

        iniciarHeartbeatPing();

        configurarArrastarSoltar(imageContainer);

        stage.setOnCloseRequest(e -> socketHandler.desconectar());
        stage.setScene(scene);
        stage.show();
    }

    private void iniciarHeartbeatPing() {
        new Thread(() -> {
            while (socketHandler.isEmExecucao() && socketHandler.getOut() != null) {
                try {
                    socketHandler.enviarComando("PING_CHECK:" + System.currentTimeMillis());
                    Thread.sleep(2000);
                } catch (Exception e) {
                    break;
                }
            }
        }, "viewer-ping-heartbeat").start();
    }

    private void atualizarPing(long rtt) {
        if (pingLbl != null && pingDot != null) {
            pingLbl.setText("Ping: " + rtt + " ms");
            if (rtt < 70) {
                pingDot.setFill(Color.web("#10B981"));
            } else if (rtt < 180) {
                pingDot.setFill(Color.web("#F59E0B"));
            } else {
                pingDot.setFill(Color.web("#EF4444"));
            }
        }
    }

    private void publicarImagemMaisRecente(Image imagem) {
        imagemPendente.set(imagem);
        if (atualizacaoImagemAgendada.compareAndSet(false, true)) {
            Platform.runLater(this::exibirImagemMaisRecente);
        }
    }

    private void exibirImagemMaisRecente() {
        Image imagem = imagemPendente.getAndSet(null);
        if (imagem != null && imageView != null) {
            imageView.setImage(imagem);
            ajustarTamanhoJanela(imagem);
        }

        atualizacaoImagemAgendada.set(false);
        if (imagemPendente.get() != null && atualizacaoImagemAgendada.compareAndSet(false, true)) {
            Platform.runLater(this::exibirImagemMaisRecente);
        }
    }

    private void configurarArrastarSoltar(StackPane container) {
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
                new Thread(() -> {
                    for (File file : files) {
                        socketHandler.enviarArquivo(file);
                    }
                }, "envio-arquivo").start();
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }

    private void mostrarErro(String header, String content) {
        com.sicad.AuxiliarDialogo.mostrarDialogoErro(header, content);
    }
}
