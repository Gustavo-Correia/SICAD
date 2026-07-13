package com.sicad.remote;

import java.io.ByteArrayInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.io.File;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.input.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;

/**
 * Cliente de acesso remoto otimizado com suporte a dirty rectangles,
 * input ultra-responsivo via OutputStream direto, e canvas persistente.
 */
public class RemoteDesktopClient {
    private final String targetHost;
    private final int targetPort;
    private final String localId;
    private final String targetId;
    private volatile Socket socket;
    private volatile Socket socketVideo;
    private volatile OutputStream comandoOut;
    private volatile boolean running = true;
    private Circle pingDot;
    private Label pingLbl;
    private Label metricsLbl;
    private ClipboardSync clipboardSync;
    private final Object monitorQuadroCodificado = new Object();
    private byte[] quadroCodificadoPendente;
    private int tipoPendente; // 0=frame completo, -4=dirty rect
    private int pendingDirtyX, pendingDirtyY, pendingDirtyW, pendingDirtyH;
    private final AtomicBoolean atualizacaoImagemAgendada = new AtomicBoolean();
    private final AtomicBoolean encerramentoNotificado = new AtomicBoolean();
    private volatile int larguraTelaRemota;
    private volatile int alturaTelaRemota;

    // Canvas persistente para dirty rectangles
    private WritableImage canvasPersistente;
    private ImageView imageView;
    private boolean stageSizeAdjusted = false;

    // Métricas de sessão
    private final SessionMetrics metrics = new SessionMetrics();
    private volatile int clientFrameCount = 0;
    private long lastClientFpsTime = System.currentTimeMillis();
    private javafx.scene.control.Tooltip metricsTooltip;

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

    /** Abre canais relay separados para impedir que os quadros bloqueiem comandos e respostas de ping. */
    public void conectarRelay(String hostServidor, int portaServidor) {
        new Thread(() -> {
            try {
                String identificadorSessao = UUID.randomUUID().toString().replace("-", "");

                socket = abrirCanalRelay(hostServidor, portaServidor, "CONTROLE", 1);
                socket.setSoTimeout(70_000);
                comandoOut = new BufferedOutputStream(socket.getOutputStream(), 512);
                java.io.InputStream entradaControle = socket.getInputStream();

                enviarComando("AUTH:" + localId + ":" + identificadorSessao);

                String respostaControle = lerLinha(entradaControle);
                if (respostaControle == null || !respostaControle.startsWith("ACCEPTED")) {
                    throw new IOException(obterMotivoRecusa(respostaControle));
                }
                socket.setSoTimeout(0);

                socketVideo = abrirCanalRelay(hostServidor, portaServidor, "VIDEO", 10);
                socketVideo.setSoTimeout(15_000);
                PrintWriter saidaVideo = new PrintWriter(socketVideo.getOutputStream(), true);
                java.io.InputStream entradaVideo = socketVideo.getInputStream();
                saidaVideo.println("AUTH:" + localId + ":" + identificadorSessao);

                String respostaVideo = lerLinha(entradaVideo);
                if (respostaVideo == null || !respostaVideo.startsWith("ACCEPTED")) {
                    throw new IOException(obterMotivoRecusa(respostaVideo));
                }
                socketVideo.setSoTimeout(0);

                clipboardSync = new ClipboardSync(null, null, false) {
                    // Override para usar nosso OutputStream otimizado
                };
                Platform.runLater(this::createRemoteWindow);
                iniciarFluxoControle(new DataInputStream(entradaControle));
                iniciarFluxoVideo(new DataInputStream(entradaVideo));

            } catch (Exception e) {
                desconectar();
                Platform.runLater(() -> mostrarErro("Erro de Conexão", "Não foi possível conectar via relay: " + e.getMessage()));
            }
        }, "cliente-remoto-relay").start();
    }

    /** Conecta diretamente ao host usando a mesma configuracao de baixa latencia do relay. */
    public void conectar() {
        new Thread(() -> {
            try {
                socket = new Socket();
                configurarSocketBaixaLatencia(socket);
                socket.connect(new InetSocketAddress(targetHost, targetPort), 5000);
                socket.setSoTimeout(70_000);
                comandoOut = new BufferedOutputStream(socket.getOutputStream(), 512);
                java.io.InputStream inStream = socket.getInputStream();

                enviarComando("AUTH:" + localId);

                String response = lerLinha(inStream);
                if (response == null || !response.startsWith("ACCEPTED")) {
                    String reason = response != null && response.contains(":") ? response.split(":")[1] : "Desconhecida";
                    Platform.runLater(() -> mostrarErro("Conexão Recusada", "O dispositivo alvo recusou a conexão. Motivo: " + reason));
                    socket.close();
                    return;
                }
                socket.setSoTimeout(0);

                clipboardSync = new ClipboardSync(new PrintWriter(comandoOut, true), null, false);
                Platform.runLater(this::createRemoteWindow);

                DataInputStream entradaDados = new DataInputStream(inStream);
                iniciarFluxoUnificado(entradaDados);

            } catch (Exception e) {
                Platform.runLater(() -> mostrarErro("Erro de Conexão", "Não foi possível conectar a: " + targetHost + ":" + targetPort + "\n" + e.getMessage()));
            }
        }, "remote-client-connect").start();
    }

    /** Le uma linha curta do handshake sem consumir o fluxo binario que vem em seguida. */
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

    /** Abre um socket para um canal relay e repete apenas enquanto o host conclui os dois registros. */
    private Socket abrirCanalRelay(String hostServidor, int portaServidor, String canal, int tentativas) throws Exception {
        String ultimaFalha = "Canal indisponivel";
        for (int tentativa = 1; tentativa <= tentativas; tentativa++) {
            Socket socketCanal = new Socket();
            try {
                configurarSocketBaixaLatencia(socketCanal);
                socketCanal.connect(new InetSocketAddress(hostServidor, portaServidor), 5000);
                socketCanal.setSoTimeout(5000);
                PrintWriter saidaCanal = new PrintWriter(socketCanal.getOutputStream(), true);
                saidaCanal.println("CONECTAR_CANAL_RELAY:" + targetId + ":" + canal);
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

    /** Extrai uma mensagem legivel da resposta textual de rejeicao do relay ou do host. */
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

    /** Converte a posicao exibida para coordenadas da resolucao real do computador remoto. */
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

    /** Cria a janela de visualizacao e registra os controles da sessao remota. */
    private void createRemoteWindow() {
        Stage stage = new Stage();
        stage.setTitle("Acesso Remoto - ID: " + targetId);

        imageView = new ImageView();
        imageView.setPreserveRatio(true);

        // Barra de ferramentas flutuante
        HBox header = new HBox(15);
        header.setStyle("-fx-background-color: rgba(15, 23, 42, 0.85); -fx-padding: 10 20; -fx-background-radius: 30; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.4), 10, 0, 0, 5);");
        header.setAlignment(Pos.CENTER);
        header.setMaxWidth(900);
        header.setMaxHeight(50);

        Circle statusDot = new Circle(4, Color.web("#10B981"));
        Label titleLbl = new Label("Conectado a " + targetId);
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

        // Ping Badge
        HBox pingBadge = new HBox(8);
        pingBadge.setAlignment(Pos.CENTER);
        pingBadge.setStyle("-fx-background-color: rgba(0,0,0,0.3); -fx-padding: 5 12; -fx-background-radius: 15;");
        pingDot = new Circle(4, Color.web("#A9B4D0"));
        pingLbl = new Label("Ping: --- ms");
        pingLbl.setStyle("-fx-text-fill: #A9B4D0; -fx-font-size: 12px; -fx-font-weight: bold;");
        pingBadge.getChildren().addAll(pingDot, pingLbl);

        // Tooltip de métricas
        metricsTooltip = new javafx.scene.control.Tooltip(metrics.getFormattedTooltipText());
        metricsTooltip.setShowDelay(javafx.util.Duration.ZERO); // Sem delay para exibir as métricas
        metricsTooltip.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 11px;");
        javafx.scene.control.Tooltip.install(pingBadge, metricsTooltip);

        header.getChildren().addAll(statusDot, titleLbl, spacer, pingBadge, btnFullscreen, btnDisconnect);

        StackPane imageContainer = new StackPane(imageView);
        imageContainer.setStyle("-fx-background-color: #000000;");

        StackPane root = new StackPane();
        root.getChildren().addAll(imageContainer, header);
        StackPane.setAlignment(header, Pos.TOP_CENTER);
        StackPane.setMargin(header, new javafx.geometry.Insets(20, 0, 0, 0));

        // Ocultar a barra quando o mouse sair do topo
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

        // ========== EVENTOS DE MOUSE (SEM THROTTLE — envio imediato) ==========
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
            if (e.getButton() == MouseButton.SECONDARY) button = 3;
            else if (e.getButton() == MouseButton.MIDDLE) button = 2;
            enviarComando("MOUSE_RELEASE:" + button);
        });

        // Scroll do mouse
        imageContainer.setOnScroll(e -> {
            int clicks = (int) (-e.getDeltaY() / 40);
            if (clicks != 0) {
                enviarComando("MOUSE_WHEEL:" + clicks);
            }
        });

        // Eventos de Teclado
        scene.setOnKeyPressed(e -> {
            int keyCode = e.getCode().getCode();
            enviarComando("KEY_PRESS:" + keyCode);
        });

        scene.setOnKeyReleased(e -> {
            int keyCode = e.getCode().getCode();
            enviarComando("KEY_RELEASE:" + keyCode);
        });

        // Inicia Ping Heartbeat
        startPingHeartbeat();

        // Configura Drag & Drop de arquivos
        configurarDragAndDrop(imageContainer);

        stage.setOnCloseRequest(e -> desconectar());
        stage.setScene(scene);
        stage.show();
    }

    // ==================== Fluxos de Video e Controle ====================

    /** Le somente JPEGs e dirty rects do canal de video. */
    private void iniciarFluxoVideo(DataInputStream entradaDados) {
        Thread tarefaDecodificacao = new Thread(this::decodificarQuadros, "decodificacao-quadros");
        tarefaDecodificacao.setDaemon(true);
        tarefaDecodificacao.start();

        new Thread(() -> {
            try {
                while (running) {
                    int tamanho = entradaDados.readInt();
                    if (tamanho == -3) {
                        processarMensagemControle(tamanho, entradaDados);
                        continue;
                    }
                    if (tamanho == -5) {
                        processarMensagemControle(tamanho, entradaDados);
                        continue;
                    }
                    if (tamanho == -4) {
                        // Dirty rectangle
                        int rx = entradaDados.readInt();
                        int ry = entradaDados.readInt();
                        int rw = entradaDados.readInt();
                        int rh = entradaDados.readInt();
                        int jpegLen = entradaDados.readInt();
                        if (jpegLen <= 0 || jpegLen > 32 * 1024 * 1024) {
                            throw new IOException("Tamanho de dirty rect invalido: " + jpegLen);
                        }
                        byte[] dadosImagem = new byte[jpegLen];
                        entradaDados.readFully(dadosImagem);
                        enfileirarDirtyRect(dadosImagem, rx, ry, rw, rh);
                        continue;
                    }
                    if (tamanho <= 0 || tamanho > 32 * 1024 * 1024) {
                        throw new IOException("Tamanho de quadro invalido: " + tamanho);
                    }
                    receberQuadroCompleto(entradaDados, tamanho);
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

    /** Le ping e area de transferencia sem disputar a fila TCP usada pelos quadros. */
    private void iniciarFluxoControle(DataInputStream entradaDados) {
        new Thread(() -> {
            try {
                while (running) {
                    processarMensagemControle(entradaDados.readInt(), entradaDados);
                }
            } catch (Exception e) {
                notificarConexaoEncerrada("controle", e);
                desconectar();
            }
        }, "cliente-remoto-controle").start();
    }

    /** Preserva o protocolo de socket unico usado pela conexao direta legada. */
    private void iniciarFluxoUnificado(DataInputStream entradaDados) {
        Thread tarefaDecodificacao = new Thread(this::decodificarQuadros, "decodificacao-quadros-direta");
        tarefaDecodificacao.setDaemon(true);
        tarefaDecodificacao.start();
        new Thread(() -> {
            try {
                while (running) {
                    int tamanho = entradaDados.readInt();
                    if (tamanho == -4) {
                        int rx = entradaDados.readInt();
                        int ry = entradaDados.readInt();
                        int rw = entradaDados.readInt();
                        int rh = entradaDados.readInt();
                        int jpegLen = entradaDados.readInt();
                        if (jpegLen > 0 && jpegLen <= 32 * 1024 * 1024) {
                            byte[] dadosImagem = new byte[jpegLen];
                            entradaDados.readFully(dadosImagem);
                            enfileirarDirtyRect(dadosImagem, rx, ry, rw, rh);
                        }
                    } else if (tamanho > 0 && tamanho <= 32 * 1024 * 1024) {
                        receberQuadroCompleto(entradaDados, tamanho);
                    } else {
                        processarMensagemControle(tamanho, entradaDados);
                    }
                }
            } catch (Exception e) {
                notificarConexaoEncerrada("unificado", e);
                desconectar();
            }
        }, "cliente-remoto-unificado").start();
    }

    /** Copia um JPEG completo para a vaga unica aguardada pela tarefa de decodificacao. */
    private void receberQuadroCompleto(DataInputStream entradaDados, int tamanho) throws Exception {
        byte[] dadosImagem = new byte[tamanho];
        entradaDados.readFully(dadosImagem);
        synchronized (monitorQuadroCodificado) {
            quadroCodificadoPendente = dadosImagem;
            tipoPendente = 0;
            monitorQuadroCodificado.notify();
        }
    }

    private void enfileirarDirtyRect(byte[] dados, int x, int y, int w, int h) {
        synchronized (monitorQuadroCodificado) {
            quadroCodificadoPendente = dados;
            tipoPendente = -4;
            pendingDirtyX = x;
            pendingDirtyY = y;
            pendingDirtyW = w;
            pendingDirtyH = h;
            monitorQuadroCodificado.notify();
        }
    }

    /** Processa ping, area de transferencia e dimensoes auxiliares da sessao. */
    private void processarMensagemControle(int tipoMensagem, DataInputStream entradaDados) throws Exception {
        if (tipoMensagem == -1) {
            long instanteOriginal = entradaDados.readLong();
            atualizarPing(System.currentTimeMillis() - instanteOriginal);
            return;
        }
        if (tipoMensagem == -5) {
            int capture = entradaDados.readInt();
            int encode = entradaDados.readInt();
            int send = entradaDados.readInt();
            int fps = entradaDados.readInt();
            int dirty = entradaDados.readInt();
            float quality = entradaDados.readFloat();
            metrics.updateHostMetrics(capture, encode, send, fps, dirty, quality);
            Platform.runLater(this::atualizarTooltipMetricas);
            return;
        }
        if (tipoMensagem == -2) {
            int tamanhoTexto = entradaDados.readInt();
            if (tamanhoTexto < 0 || tamanhoTexto > 4 * 1024 * 1024) {
                throw new IOException("Tamanho de texto invalido: " + tamanhoTexto);
            }
            byte[] dadosTexto = new byte[tamanhoTexto];
            entradaDados.readFully(dadosTexto);
            String texto = new String(dadosTexto, StandardCharsets.UTF_8);
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

    /** Mostra uma unica mensagem quando qualquer um dos canais da sessao e perdido. */
    private void notificarConexaoEncerrada(String canal, Exception erro) {
        if (running && encerramentoNotificado.compareAndSet(false, true)) {
            System.out.println("Canal remoto de " + canal + " encerrado: " + erro.getMessage());
            Platform.runLater(() -> mostrarErro("Conexão Perdida", "A sessão remota foi encerrada."));
        }
    }

    // ==================== Decodificacao com Dirty Rectangles ====================

    /** Decodifica o JPEG mais recente e aplica sobre o canvas persistente. */
    private void decodificarQuadros() {
        try {
            while (running) {
                byte[] dadosImagem;
                int tipo;
                int drX, drY, drW, drH;

                synchronized (monitorQuadroCodificado) {
                    while (running && quadroCodificadoPendente == null) {
                        monitorQuadroCodificado.wait();
                    }
                    dadosImagem = quadroCodificadoPendente;
                    tipo = tipoPendente;
                    drX = pendingDirtyX;
                    drY = pendingDirtyY;
                    drW = pendingDirtyW;
                    drH = pendingDirtyH;
                    quadroCodificadoPendente = null;
                }

                if (dadosImagem == null) continue;

                Image imagemDecodificada = new Image(new ByteArrayInputStream(dadosImagem));

                if (tipo == -4 && canvasPersistente != null) {
                    // Dirty rectangle: pintar sobre o canvas existente
                    aplicarDirtyRect(imagemDecodificada, drX, drY, drW, drH);
                } else {
                    // Frame completo: substituir o canvas
                    canvasPersistente = null; // Forcar recriacao
                    publicarImagemMaisRecente(imagemDecodificada);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            if (running) {
                System.out.println("Erro ao decodificar quadro remoto: " + e.getMessage());
            }
        }
    }

    /** Aplica um fragmento decodificado sobre a posicao correta do canvas persistente. */
    private void aplicarDirtyRect(Image fragmento, int x, int y, int w, int h) {
        if (canvasPersistente == null) {
            // Sem canvas base ainda — tratar como frame completo
            publicarImagemMaisRecente(fragmento);
            return;
        }

        try {
            PixelWriter pw = canvasPersistente.getPixelWriter();
            javafx.scene.image.PixelReader pr = fragmento.getPixelReader();
            if (pr == null) return;

            int writeW = Math.min(w, (int) fragmento.getWidth());
            int writeH = Math.min(h, (int) fragmento.getHeight());
            int canvasW = (int) canvasPersistente.getWidth();
            int canvasH = (int) canvasPersistente.getHeight();

            // Clamp para nao sair do canvas
            if (x + writeW > canvasW) writeW = canvasW - x;
            if (y + writeH > canvasH) writeH = canvasH - y;
            if (writeW <= 0 || writeH <= 0) return;

            pw.setPixels(x, y, writeW, writeH, pr, 0, 0);

            // Notificar JavaFX que a imagem mudou
            publicarCanvasAtualizado();
        } catch (Exception e) {
            // Fallback: tratar como frame completo
            publicarImagemMaisRecente(fragmento);
        }
    }

    /** Agenda uma atualizacao do ImageView com o canvas persistente. */
    private void publicarCanvasAtualizado() {
        if (atualizacaoImagemAgendada.compareAndSet(false, true)) {
            Platform.runLater(() -> {
                if (imageView != null && canvasPersistente != null) {
                    imageView.setImage(canvasPersistente);
                    registrarFrameRenderizado();
                }
                atualizacaoImagemAgendada.set(false);
            });
        }
    }

    /** Guarda a imagem mais recente e garante no maximo uma atualizacao pendente no JavaFX. */
    private void publicarImagemMaisRecente(Image imagem) {
        // Criar canvas persistente para futuras dirty rects
        int iw = (int) imagem.getWidth();
        int ih = (int) imagem.getHeight();
        if (iw > 0 && ih > 0) {
            WritableImage novoCanvas = new WritableImage(iw, ih);
            javafx.scene.image.PixelReader pr = imagem.getPixelReader();
            if (pr != null) {
                novoCanvas.getPixelWriter().setPixels(0, 0, iw, ih, pr, 0, 0);
            }
            canvasPersistente = novoCanvas;
        }

        if (atualizacaoImagemAgendada.compareAndSet(false, true)) {
            Platform.runLater(() -> {
                if (imageView != null) {
                    Image imgToShow = canvasPersistente != null ? canvasPersistente : imagem;
                    imageView.setImage(imgToShow);
                    adjustStageSize(imgToShow);
                    registrarFrameRenderizado();
                }
                atualizacaoImagemAgendada.set(false);
            });
        }
    }

    // ==================== Rede ====================

    /** Configura o socket antes da conexao para limitar filas TCP sem desabilitar o fluxo confiavel. */
    private void configurarSocketBaixaLatencia(Socket socketConfigurado) throws Exception {
        socketConfigurado.setTcpNoDelay(true);
        socketConfigurado.setSendBufferSize(8 * 1024);   // Controle = buffer pequeno
        socketConfigurado.setReceiveBufferSize(64 * 1024); // Video = buffer grande pra receber
    }

    /** Envia um comando de texto seguido de newline via OutputStream direto (sem PrintWriter). */
    private void enviarComando(String command) {
        if (comandoOut == null || !running) return;
        try {
            byte[] bytes = (command + "\n").getBytes(StandardCharsets.UTF_8);
            synchronized (comandoOut) {
                comandoOut.write(bytes);
                comandoOut.flush();
            }
        } catch (Exception e) {
            // Conexao pode ter sido encerrada
        }
    }

    private void startPingHeartbeat() {
        new Thread(() -> {
            while (running && socket != null && !socket.isClosed()) {
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
        metrics.updateRtt(rtt);
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
            atualizarTooltipMetricas();
        });
    }

    private void registrarFrameRenderizado() {
        clientFrameCount++;
        long agora = System.currentTimeMillis();
        if (agora - lastClientFpsTime >= 1000) {
            int fps = (int) (clientFrameCount * 1000L / (agora - lastClientFpsTime));
            metrics.updateClientFps(fps);
            clientFrameCount = 0;
            lastClientFpsTime = agora;
            atualizarTooltipMetricas();
        }
    }

    private void atualizarTooltipMetricas() {
        if (metricsTooltip != null) {
            metricsTooltip.setText(metrics.getFormattedTooltipText());
        }
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

    /** Encerra a sessao e libera as threads que aguardam dados de video. */
    private void desconectar() {
        running = false;
        synchronized (monitorQuadroCodificado) {
            monitorQuadroCodificado.notifyAll();
        }
        if (clipboardSync != null) {
            clipboardSync.stop();
        }
        fecharSocket(socket);
        fecharSocket(socketVideo);
    }

    /** Fecha silenciosamente um dos sockets da sessao remota. */
    private void fecharSocket(Socket socketFechado) {
        if (socketFechado == null) return;
        try {
            socketFechado.close();
        } catch (Exception e) {
            // O outro canal pode ter encerrado a sessao primeiro.
        }
    }

    private void mostrarErro(String header, String content) {
        com.sicad.DialogHelper.showInfoDialog(header, content);
    }
}
