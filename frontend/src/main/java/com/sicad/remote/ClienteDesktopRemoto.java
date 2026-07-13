package com.sicad.remote;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
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
import javafx.scene.control.Button;
import javafx.geometry.Pos;
import javafx.scene.input.*;
import java.io.File;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class ClienteDesktopRemoto {
    private final String hostAlvo;
    private final int portaAlvo;
    private final String idLocal;
    private final String idAlvo;
    private volatile Socket socket;
    private volatile Socket socketVideo;
    private volatile PrintWriter out;
    private volatile boolean emExecucao = true;
    private Circle pingDot;
    private Label pingLbl;
    private SincronizadorAreaTransferencia clipboardSync;
    private final Object monitorQuadroCodificado = new Object();
    private byte[] quadroCodificadoPendente;
    private final AtomicReference<Image> imagemPendente = new AtomicReference<>();
    private final AtomicBoolean atualizacaoImagemAgendada = new AtomicBoolean();
    private final AtomicBoolean encerramentoNotificado = new AtomicBoolean();
    private volatile int larguraTelaRemota;
    private volatile int alturaTelaRemota;

    public ClienteDesktopRemoto(String targetId, String localId) {
        this.idAlvo = targetId;
        this.idLocal = localId;
        this.hostAlvo = null;
        this.portaAlvo = 0;
    }

    public void conectarRelay(String hostServidor, int portaServidor) {
        conectarRelay(hostServidor, portaServidor, hostServidor, portaServidor);
    }

    public void conectarRelay(String hostServidor, int portaServidor, String hostVideo, int portaVideo) {
        new Thread(() -> {
            try {
                String identificadorSessao = UUID.randomUUID().toString().replace("-", "");

                socket = abrirCanalRelay(hostServidor, portaServidor, "CONTROLE", 1);
                socket.setSoTimeout(70_000);
                out = new PrintWriter(socket.getOutputStream(), true);
                java.io.InputStream entradaControle = socket.getInputStream();

                out.println("AUTH:" + idLocal + ":" + identificadorSessao);

                String respostaControle = lerLinha(entradaControle);
                if (respostaControle == null || !respostaControle.startsWith("ACCEPTED")) {
                    throw new IOException(obterMotivoRecusa(respostaControle));
                }
                socket.setSoTimeout(0);

                try {
                    socketVideo = abrirCanalRelay(hostVideo, portaVideo, "VIDEO", 5);
                } catch (Exception e) {
                    System.out.println("Video direto indisponivel (" + hostVideo + ":" + portaVideo
                            + "), usando bore como fallback: " + e.getMessage());
                    socketVideo = abrirCanalRelay(hostServidor, portaServidor, "VIDEO", 10);
                }
                socketVideo.setSoTimeout(15_000);
                PrintWriter saidaVideo = new PrintWriter(socketVideo.getOutputStream(), true);
                java.io.InputStream entradaVideo = socketVideo.getInputStream();
                saidaVideo.println("AUTH:" + idLocal + ":" + identificadorSessao);

                String respostaVideo = lerLinha(entradaVideo);
                if (respostaVideo == null || !respostaVideo.startsWith("ACCEPTED")) {
                    throw new IOException(obterMotivoRecusa(respostaVideo));
                }
                socketVideo.setSoTimeout(0);

                clipboardSync = new SincronizadorAreaTransferencia(out, null, false);
                Platform.runLater(this::criarJanelaRemota);
                iniciarFluxoControle(new DataInputStream(entradaControle));
                iniciarFluxoVideo(new DataInputStream(entradaVideo));

            } catch (Exception e) {
                desconectar();
                Platform.runLater(() -> mostrarErro("Erro de Conexão", "Não foi possível conectar via relay: " + e.getMessage()));
            }
        }, "cliente-remoto-relay").start();
    }

    private String lerLinha(java.io.InputStream entrada) throws Exception {
        java.io.ByteArrayOutputStream conteudo = new java.io.ByteArrayOutputStream();
        int byteLido;
        while ((byteLido = entrada.read()) != -1 && byteLido != '\n') {
            if (conteudo.size() >= 4096) {
                throw new IOException("Linha de handshake muito extensa");
            }
            conteudo.write(byteLido);
        }
        if (byteLido == -1 && conteudo.size() == 0) {
            return null;
        }
        return conteudo.toString("UTF-8").trim();
    }

    private Socket abrirCanalRelay(String hostServidor, int portaServidor, String canal, int tentativas) throws Exception {
        String ultimaFalha = "Canal indisponivel";
        for (int tentativa = 1; tentativa <= tentativas; tentativa++) {
            Socket socketCanal = new Socket();
            try {
                configurarSocketBaixaLatencia(socketCanal);
                socketCanal.connect(new InetSocketAddress(hostServidor, portaServidor), 5000);
                socketCanal.setSoTimeout(5000);
                PrintWriter saidaCanal = new PrintWriter(socketCanal.getOutputStream(), true);
                saidaCanal.println("CONECTAR_CANAL_RELAY:" + idAlvo + ":" + canal);
                String resposta = lerLinha(socketCanal.getInputStream());
                if ("OK".equals(resposta)) {
                    socketCanal.setSoTimeout(0);
                    return socketCanal;
                }
                ultimaFalha = obterMotivoRecusa(resposta);
            } catch (Exception e) {
                ultimaFalha = e.getMessage();
            }

            fecharSocket(socketCanal);
            if (tentativa < tentativas) {
                Thread.sleep(300);
            }
        }
        throw new IOException(ultimaFalha);
    }

    private String obterMotivoRecusa(String resposta) {
        if (resposta == null || resposta.isBlank()) {
            return "Conexao encerrada durante a autenticacao";
        }
        if (resposta.contains("Comando desconhecido")) {
            return "Backend remoto desatualizado; reconstrua o container backend-1";
        }
        int separador = resposta.indexOf(':');
        return separador >= 0 ? resposta.substring(separador + 1) : resposta;
    }

    private ImageView imageView;
    private boolean stageSizeAdjusted = false;

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

        double larguraReferencia = larguraTelaRemota > 0
                ? larguraTelaRemota : imageView.getImage().getWidth();
        double alturaReferencia = alturaTelaRemota > 0
                ? alturaTelaRemota : imageView.getImage().getHeight();
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
        stage.setTitle("Acesso Remoto - ID: " + idAlvo);

        imageView = new ImageView();
        imageView.setPreserveRatio(true);

        HBox header = new HBox(15);
        header.setStyle("-fx-background-color: rgba(15, 23, 42, 0.85); -fx-padding: 10 20; -fx-background-radius: 30; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.4), 10, 0, 0, 5);");
        header.setAlignment(Pos.CENTER);
        header.setMaxWidth(700);
        header.setMaxHeight(50);

        Circle statusDot = new Circle(4, Color.web("#10B981"));
        Label titleLbl = new Label("Conectado a " + idAlvo);
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
            desconectar();
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
                enviarComando("MOUSE_MOVE:" + (int) mapped.getX() + ":" + (int) mapped.getY());
            }
        });

        imageContainer.setOnMouseDragged(e -> {
            javafx.geometry.Point2D mapped = mapearCoordenadas(e.getSceneX(), e.getSceneY());
            if (mapped != null) {
                enviarComando("MOUSE_MOVE:" + (int) mapped.getX() + ":" + (int) mapped.getY());
            }
        });

        imageContainer.setOnMousePressed(e -> {
            int button = 1;
            if (e.isSecondaryButtonDown()) button = 3;
            else if (e.isMiddleButtonDown()) button = 2;
            enviarComando("MOUSE_PRESS:" + button);
        });

        imageContainer.setOnMouseReleased(e -> {
            int button = 1;
            if (e.getButton() == javafx.scene.input.MouseButton.SECONDARY) button = 3;
            else if (e.getButton() == javafx.scene.input.MouseButton.MIDDLE) button = 2;
            enviarComando("MOUSE_RELEASE:" + button);
        });

        scene.setOnKeyPressed(e -> {
            int keyCode = e.getCode().getCode();
            enviarComando("KEY_PRESS:" + keyCode);
        });

        scene.setOnKeyReleased(e -> {
            int keyCode = e.getCode().getCode();
            enviarComando("KEY_RELEASE:" + keyCode);
        });

        if (clipboardSync == null) {
            clipboardSync = new SincronizadorAreaTransferencia(out, null, false);
        }

        iniciarHeartbeatPing();

        configurarArrastarSoltar(imageContainer);

        stage.setOnCloseRequest(e -> desconectar());
        stage.setScene(scene);
        stage.show();
    }

    private void iniciarFluxoVideo(DataInputStream entradaDados) {
        Thread tarefaDecodificacao = new Thread(this::decodificarQuadros, "decodificacao-quadros");
        tarefaDecodificacao.start();

        new Thread(() -> {
            try {
                while (emExecucao) {
                    int tamanho = entradaDados.readInt();
                    if (tamanho == -3) {
                        processarMensagemControle(tamanho, entradaDados);
                        continue;
                    }
                    if (tamanho <= 0 || tamanho > 32 * 1024 * 1024) {
                        throw new IOException("Tamanho de quadro invalido: " + tamanho);
                    }
                    receberQuadro(entradaDados, tamanho);
                }
            } catch (Exception e) {
                notificarConexaoEncerrada("video", e);
                desconectar();
            } finally {
                synchronized (monitorQuadroCodificado) {
                    monitorQuadroCodificado.notifyAll();
                }
            }
        }, "remote-client-video").start();
    }

    private void iniciarFluxoControle(DataInputStream entradaDados) {
        new Thread(() -> {
            try {
                while (emExecucao) {
                    processarMensagemControle(entradaDados.readInt(), entradaDados);
                }
            } catch (Exception e) {
                notificarConexaoEncerrada("controle", e);
                desconectar();
            }
        }, "cliente-remoto-controle").start();
    }

    private void receberQuadro(DataInputStream entradaDados, int tamanho) throws Exception {
        byte[] dadosImagem = new byte[tamanho];
        entradaDados.readFully(dadosImagem);
        synchronized (monitorQuadroCodificado) {
            quadroCodificadoPendente = dadosImagem;
            monitorQuadroCodificado.notify();
        }
    }

    private void processarMensagemControle(int tipoMensagem, DataInputStream entradaDados) throws Exception {
        if (tipoMensagem == -1) {
            long instanteOriginal = entradaDados.readLong();
            atualizarPing(System.currentTimeMillis() - instanteOriginal);
            return;
        }
        if (tipoMensagem == -2) {
            int tamanhoTexto = entradaDados.readInt();
            if (tamanhoTexto < 0 || tamanhoTexto > 4 * 1024 * 1024) {
                throw new IOException("Tamanho de texto invalido: " + tamanhoTexto);
            }
            byte[] dadosTexto = new byte[tamanhoTexto];
            entradaDados.readFully(dadosTexto);
            String texto = new String(dadosTexto, java.nio.charset.StandardCharsets.UTF_8);
            if (clipboardSync != null) {
                clipboardSync.aplicarTextoRemoto(texto);
            }
            return;
        }
        if (tipoMensagem == -3) {
            larguraTelaRemota = entradaDados.readInt();
            alturaTelaRemota = entradaDados.readInt();
            if (larguraTelaRemota <= 0 || alturaTelaRemota <= 0) {
                throw new IOException("Dimensoes remotas invalidas");
            }
            return;
        }
        throw new IOException("Tipo de mensagem de controle invalido: " + tipoMensagem);
    }

    private void notificarConexaoEncerrada(String canal, Exception erro) {
        if (emExecucao && encerramentoNotificado.compareAndSet(false, true)) {
            System.out.println("Canal remoto de " + canal + " encerrado: " + erro.getMessage());
            Platform.runLater(() -> mostrarErro("Conexão Perdida", "A sessão remota foi encerrada."));
        }
    }

    private void decodificarQuadros() {
        try {
            while (emExecucao) {
                byte[] dadosImagem;
                synchronized (monitorQuadroCodificado) {
                    while (emExecucao && quadroCodificadoPendente == null) {
                        monitorQuadroCodificado.wait();
                    }
                    dadosImagem = quadroCodificadoPendente;
                    quadroCodificadoPendente = null;
                }

                if (dadosImagem != null) {
                    Image imagem = new Image(new ByteArrayInputStream(dadosImagem));
                    publicarImagemMaisRecente(imagem);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            if (emExecucao) {
                System.out.println("Erro ao decodificar quadro remoto: " + e.getMessage());
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

    private void configurarSocketBaixaLatencia(Socket socketConfigurado) throws Exception {
        socketConfigurado.setTcpNoDelay(true);
        socketConfigurado.setSendBufferSize(16 * 1024);
        socketConfigurado.setReceiveBufferSize(16 * 1024);
    }

    private void enviarComando(String command) {
        if (out != null && emExecucao) {
            out.println(command);
        }
    }

    private void iniciarHeartbeatPing() {
        new Thread(() -> {
            while (emExecucao && socket != null && !socket.isClosed()) {
                try {
                    enviarComando("PING_CHECK:" + System.currentTimeMillis());
                    Thread.sleep(2000);
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
                    pingDot.setFill(Color.web("#10B981"));
                } else if (rtt < 180) {
                    pingDot.setFill(Color.web("#F59E0B"));
                } else {
                    pingDot.setFill(Color.web("#EF4444"));
                }
            }
        });
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

    private void desconectar() {
        emExecucao = false;
        synchronized (monitorQuadroCodificado) {
            monitorQuadroCodificado.notifyAll();
        }
        if (clipboardSync != null) {
            clipboardSync.stop();
        }
        fecharSocket(socket);
        fecharSocket(socketVideo);
    }

    private void fecharSocket(Socket socketFechado) {
        if (socketFechado == null) {
            return;
        }
        try {
            socketFechado.close();
        } catch (Exception e) {
        }
    }

    private void mostrarErro(String header, String content) {
        com.sicad.AuxiliarDialogo.mostrarDialogoErro(header, content);
    }
}
