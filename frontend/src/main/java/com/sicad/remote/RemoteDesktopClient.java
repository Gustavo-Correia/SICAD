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

public class RemoteDesktopClient {
    private final String targetHost;
    private final int targetPort;
    private final String localId;
    private final String targetId;
    private volatile Socket socket;
    private volatile Socket socketVideo;
    private volatile PrintWriter out;
    private volatile boolean running = true;
    private Circle pingDot;
    private Label pingLbl;
    private ClipboardSync clipboardSync;
    private final Object monitorQuadroCodificado = new Object();
    private byte[] quadroCodificadoPendente;
    private final AtomicReference<Image> imagemPendente = new AtomicReference<>();
    private final AtomicBoolean atualizacaoImagemAgendada = new AtomicBoolean();
    private final AtomicBoolean encerramentoNotificado = new AtomicBoolean();
    private volatile int larguraTelaRemota;
    private volatile int alturaTelaRemota;

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
        conectarRelay(hostServidor, portaServidor, hostServidor, portaServidor);
    }

    /** Conecta controle via um host/porta (ex: bore) e video diretamente ao backend para menor latencia. */
    public void conectarRelay(String hostServidor, int portaServidor, String hostVideo, int portaVideo) {
        new Thread(() -> {
            try {
                String identificadorSessao = UUID.randomUUID().toString().replace("-", "");

                socket = abrirCanalRelay(hostServidor, portaServidor, "CONTROLE", 1);
                socket.setSoTimeout(70_000);
                out = new PrintWriter(socket.getOutputStream(), true);
                java.io.InputStream entradaControle = socket.getInputStream();

                out.println("AUTH:" + localId + ":" + identificadorSessao);

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
                saidaVideo.println("AUTH:" + localId + ":" + identificadorSessao);

                String respostaVideo = lerLinha(entradaVideo);
                if (respostaVideo == null || !respostaVideo.startsWith("ACCEPTED")) {
                    throw new IOException(obterMotivoRecusa(respostaVideo));
                }
                socketVideo.setSoTimeout(0);

                clipboardSync = new ClipboardSync(out, null, false);
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
                socket.setSoTimeout(0);

                // Aceito, inicia a UI e começa a receber vídeo
                clipboardSync = new ClipboardSync(out, null, false);
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
        header.setMaxWidth(700);
        header.setMaxHeight(50);
        
        // Ícone/Texto de conexão
        Circle statusDot = new Circle(4, Color.web("#10B981"));
        Label titleLbl = new Label("Conectado a " + targetId);
        titleLbl.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px; -fx-font-family: 'Inter', sans-serif;");
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Botões de Ação
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
        
        header.getChildren().addAll(statusDot, titleLbl, spacer, pingBadge, btnFullscreen, btnDisconnect);
        
        StackPane imageContainer = new StackPane(imageView);
        imageContainer.setStyle("-fx-background-color: #000000;"); // Fundo preto
        
        StackPane root = new StackPane();
        root.getChildren().addAll(imageContainer, header);
        StackPane.setAlignment(header, Pos.TOP_CENTER);
        StackPane.setMargin(header, new javafx.geometry.Insets(20, 0, 0, 0)); // Margem do topo

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
        
        // Responsivo
        imageView.fitWidthProperty().bind(scene.widthProperty());
        imageView.fitHeightProperty().bind(scene.heightProperty());

        // Eventos de Mouse com mapeamento de coordenadas corrigido (vinculados ao imageContainer)
        imageContainer.setOnMouseMoved(e -> {
            javafx.geometry.Point2D mapped = mapearCoordenadas(e.getSceneX(), e.getSceneY());
            if (mapped != null) {
                sendCommand("MOUSE_MOVE:" + (int) mapped.getX() + ":" + (int) mapped.getY());
            }
        });
 
        imageContainer.setOnMouseDragged(e -> {
            javafx.geometry.Point2D mapped = mapearCoordenadas(e.getSceneX(), e.getSceneY());
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
        if (clipboardSync == null) {
            clipboardSync = new ClipboardSync(out, null, false);
        }

        // Inicia Ping Heartbeat
        startPingHeartbeat();

        // Configura Drag & Drop de arquivos
        configurarDragAndDrop(imageContainer);
 
        stage.setOnCloseRequest(e -> desconectar());
        stage.setScene(scene);
        stage.show();
    }
 
    /** Le somente JPEGs do canal de video e conserva o ultimo quadro recebido. */
    private void iniciarFluxoVideo(DataInputStream entradaDados) {
        Thread tarefaDecodificacao = new Thread(this::decodificarQuadros, "decodificacao-quadros");
        tarefaDecodificacao.start();

        new Thread(() -> {
            try {
                while (running) {
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
        tarefaDecodificacao.start();
        new Thread(() -> {
            try {
                while (running) {
                    int tamanho = entradaDados.readInt();
                    if (tamanho > 0 && tamanho <= 32 * 1024 * 1024) {
                        receberQuadro(entradaDados, tamanho);
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
    private void receberQuadro(DataInputStream entradaDados, int tamanho) throws Exception {
        byte[] dadosImagem = new byte[tamanho];
        entradaDados.readFully(dadosImagem);
        synchronized (monitorQuadroCodificado) {
            quadroCodificadoPendente = dadosImagem;
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

    /** Mostra uma unica mensagem quando qualquer um dos canais da sessao e perdido. */
    private void notificarConexaoEncerrada(String canal, Exception erro) {
        if (running && encerramentoNotificado.compareAndSet(false, true)) {
            System.out.println("Canal remoto de " + canal + " encerrado: " + erro.getMessage());
            Platform.runLater(() -> mostrarErro("Conexão Perdida", "A sessão remota foi encerrada."));
        }
    }

    /** Decodifica somente o JPEG mais recente e descarta quadros substituidos durante o processamento. */
    private void decodificarQuadros() {
        try {
            while (running) {
                byte[] dadosImagem;
                synchronized (monitorQuadroCodificado) {
                    while (running && quadroCodificadoPendente == null) {
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
            if (running) {
                System.out.println("Erro ao decodificar quadro remoto: " + e.getMessage());
            }
        }
    }

    /** Guarda a imagem mais recente e garante no maximo uma atualizacao pendente no JavaFX. */
    private void publicarImagemMaisRecente(Image imagem) {
        imagemPendente.set(imagem);
        if (atualizacaoImagemAgendada.compareAndSet(false, true)) {
            Platform.runLater(this::exibirImagemMaisRecente);
        }
    }

    /** Exibe o ultimo quadro disponivel e reagenda apenas se outro chegou durante a renderizacao. */
    private void exibirImagemMaisRecente() {
        Image imagem = imagemPendente.getAndSet(null);
        if (imagem != null && imageView != null) {
            imageView.setImage(imagem);
            adjustStageSize(imagem);
        }

        atualizacaoImagemAgendada.set(false);
        if (imagemPendente.get() != null && atualizacaoImagemAgendada.compareAndSet(false, true)) {
            Platform.runLater(this::exibirImagemMaisRecente);
        }
    }

    /** Configura o socket antes da conexao para limitar filas TCP sem desabilitar o fluxo confiavel. */
    private void configurarSocketBaixaLatencia(Socket socketConfigurado) throws Exception {
        socketConfigurado.setTcpNoDelay(true);
        socketConfigurado.setSendBufferSize(16 * 1024);
        socketConfigurado.setReceiveBufferSize(16 * 1024);
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
        if (socketFechado == null) {
            return;
        }
        try {
            socketFechado.close();
        } catch (Exception e) {
            // O outro canal pode ter encerrado a sessao primeiro.
        }
    }

    private void mostrarErro(String header, String content) {
        com.sicad.DialogHelper.showErrorDialog(header, content);
    }
}
