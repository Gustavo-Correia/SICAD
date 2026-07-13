package com.sicad;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.List;
import java.util.Map;

import com.sun.jna.Native;
import com.sun.jna.win32.StdCallLibrary;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.Circle;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import com.sicad.remote.ClienteDesktopRemoto;
import com.sicad.remote.TransmissorTela;
import com.sicad.remote.ReceptorEntrada;
import java.awt.Robot;

interface Kernel32 extends StdCallLibrary {
    Kernel32 INSTANCE = Native.load("kernel32", Kernel32.class);
    boolean AllocConsole();
}

public class Main extends Application {

    public static final boolean SHOW_CONSOLE = false;

    public static String SERVIDOR_HOST = "127.0.0.1";
    public static int SERVIDOR_PORTA = 5000;

    private BorderPane root;
    private ConexaoServidor conexaoServidor;
    private Circle statusDot;
    private Label statusText;
    private Button btnReconnect;
    private Label idLabel;
    private String meuID;

    private final Object monitorSessaoRelay = new Object();
    private volatile Socket socketRelayControle;
    private volatile Socket socketRelayVideo;
    private volatile String identificadorSessaoAceita;
    private volatile TransmissorTela transmissorTelaAtivo;
    private final AtomicBoolean registroRelayIniciado = new AtomicBoolean();
    private TextField idInput;
    private FlowPane recentsGrid;
    private ScrollPane centerScrollPane;
    private List<StackPane> sidebarButtons = new java.util.ArrayList<>();

    @Override
    public void start(Stage stage) {
        try {
            java.util.Properties props = GerenciadorConfiguracoes.carregarConfiguracoes();
            SERVIDOR_HOST = props.getProperty("server.host", "127.0.0.1");
            SERVIDOR_PORTA = Integer.parseInt(props.getProperty("server.port", "5000"));
        } catch (Exception e) {
            System.out.println("Erro ao carregar configurações salvas: " + e.getMessage());
        }

        if (SHOW_CONSOLE) {
            Kernel32.INSTANCE.AllocConsole();
            try {
                FileOutputStream fos = new FileOutputStream("CONOUT$");
                System.setOut(new PrintStream(fos, true));
                System.setErr(new PrintStream(fos, true));
            } catch (Exception e) {
                System.out.println("Erro ao redirecionar console: " + e.getMessage());
            }
            System.out.println("=== SICAD - Console Ativado ===");
        }

        root = new BorderPane();
        root.getStyleClass().add("root");

        VBox sidebar = criarBarraLateral();
        root.setLeft(sidebar);

        VBox topArea = new VBox();
        topArea.getChildren().addAll(criarCabecalho());
        root.setTop(topArea);

        centerScrollPane = new ScrollPane();
        centerScrollPane.setFitToWidth(true);
        centerScrollPane.getStyleClass().add("scroll-pane");
        centerScrollPane.setContent(criarConteudoDashboard());
        root.setCenter(centerScrollPane);

        Scene scene = new Scene(root, 1400, 850);
        java.net.URL cssUrl = getClass().getResource("styles.css");
        if (cssUrl == null) {
            cssUrl = getClass().getResource("/com/sicad/styles.css");
        }
        if (cssUrl == null) {
            cssUrl = Main.class.getResource("styles.css");
        }
        if (cssUrl == null) {
            cssUrl = Main.class.getClassLoader().getResource("com/sicad/styles.css");
        }
        if (cssUrl == null) {
            throw new RuntimeException("Não foi possível encontrar o arquivo styles.css no classpath!");
        }
        String css = cssUrl.toExternalForm();
        scene.getStylesheets().add(css);

        stage.setTitle("SICAD - Sistema Integrado de Conexão");
        stage.setScene(scene);
        stage.setMinWidth(1000);
        stage.setMinHeight(700);
        stage.show();

        this.conexaoServidor = new ConexaoServidor(this);

        this.conexaoServidor.conectarComFallback(
                SERVIDOR_HOST, SERVIDOR_PORTA,
                SERVIDOR_HOST, SERVIDOR_PORTA
        );

        inicializarID();
    }

    @Override
    public void stop() throws Exception {
        if (conexaoServidor != null) {
            conexaoServidor.desconectarServidor();
        }
        encerrarSessaoRelay(null);
        fecharSocketRelay(socketRelayControle);
        fecharSocketRelay(socketRelayVideo);
        super.stop();
        System.out.println("Finalizando todos os processos...");
        System.exit(0);
    }

    public void atualizarStatusConexao(boolean conectado) {
        Platform.runLater(() -> {
            if (statusDot != null && statusText != null) {
                if (conectado) {
                    statusDot.setFill(Color.web("#10B981"));
                    statusText.setText("Online");
                    if (btnReconnect != null) {
                        btnReconnect.setVisible(false);
                        btnReconnect.setManaged(false);
                        btnReconnect.setDisable(false);
                        btnReconnect.setText("Reconectar");
                    }
                } else {
                    statusDot.setFill(Color.web("#EF4444"));
                    statusText.setText("Offline");
                    if (btnReconnect != null) {
                        btnReconnect.setVisible(true);
                        btnReconnect.setManaged(true);
                        btnReconnect.setDisable(false);
                        btnReconnect.setText("Reconectar");
                    }
                }
            }
        });
    }

    public void atualizarID(String id) {
        this.meuID = id;
        if (idLabel != null) {
            idLabel.setText(id);
        }
    }

    private void inicializarID() {
        new Thread(() -> {
            int tentativas = 0;
            while (!conexaoServidor.isConectado() && tentativas < 60) {
                try { Thread.sleep(250); } catch (InterruptedException e) { break; }
                tentativas++;
            }

            if (!conexaoServidor.isConectado()) {
                System.out.println("Não foi possível conectar ao servidor para obter ID.");
                return;
            }

            GerenciadorID gerenciador = new GerenciadorID(conexaoServidor);
            String id = gerenciador.obterOuCriarID();

            carregarConfigServidor(id);

            Platform.runLater(() -> {
                atualizarID(id);
            });

            iniciarRelayHost(id);
        }, "inicializar-id").start();
    }

    private void carregarConfigServidor(String id) {
        try {
            String resposta = conexaoServidor.enviarComando("LOAD_CONFIG:" + id);
            if (resposta != null && resposta.startsWith("CONFIG:")) {
                String[] valores = resposta.substring(7).split(",");
                if (valores.length == 4) {
                    GerenciadorConfiguracoes.carregarCasterSettingsDoServidor(
                            valores[0], valores[1], valores[2], valores[3]);
                    System.out.println("Configuracoes carregadas do servidor para " + id);
                }
            }
        } catch (Exception e) {
            System.out.println("Erro ao carregar config do servidor: " + e.getMessage());
        }
    }

    private void iniciarRelayHost(String id) {
        if (!registroRelayIniciado.compareAndSet(false, true)) {
            return;
        }
        new Thread(() -> manterCanalControle(id), "relay-host-controle").start();
        new Thread(() -> manterCanalVideo(id), "relay-host-video").start();
    }

    private void manterCanalControle(String id) {
        while (!Thread.currentThread().isInterrupted()) {
            Socket canalControle = null;
            String identificadorSessao = null;
            try {
                canalControle = registrarCanalRelay(id, "CONTROLE");
                socketRelayControle = canalControle;

                String autenticacao = lerLinhaRelay(canalControle.getInputStream());
                String[] partes = autenticacao != null ? autenticacao.split(":", 3) : new String[0];
                if (partes.length != 3 || !"AUTH".equals(partes[0])) {
                    enviarRespostaRelay(canalControle, "REJECTED:Autenticacao invalida");
                    continue;
                }

                String idRemoto = partes[1];
                identificadorSessao = partes[2];
                if (!solicitarAutorizacaoRemota(idRemoto)) {
                    enviarRespostaRelay(canalControle, "REJECTED:Acesso negado pelo usuario");
                    continue;
                }

                synchronized (monitorSessaoRelay) {
                    identificadorSessaoAceita = identificadorSessao;
                }
                enviarRespostaRelay(canalControle, "ACCEPTED");

                DataOutputStream saidaControle = new DataOutputStream(canalControle.getOutputStream());
                ReceptorEntrada receptor = new ReceptorEntrada(canalControle, saidaControle, new Robot());
                receptor.run();
                receptor.pararRecebimento();
            } catch (Exception e) {
                System.out.println("Canal de controle relay encerrado: " + e.getMessage());
            } finally {
                if (identificadorSessao != null) {
                    encerrarSessaoRelay(identificadorSessao);
                }
                limparCanalRelay("CONTROLE", canalControle);
                aguardarNovaTentativaRelay();
            }
        }
    }

    private void manterCanalVideo(String id) {
        while (!Thread.currentThread().isInterrupted()) {
            Socket canalVideo = null;
            String identificadorSessao = null;
            try {
                try {
                    canalVideo = registrarCanalRelay(SERVIDOR_HOST, SERVIDOR_PORTA, id, "VIDEO");
                } catch (Exception e) {
                    System.out.println("Video relay falhou (" + SERVIDOR_HOST + ":" + SERVIDOR_PORTA
                            + "): " + e.getMessage());
                    break;
                }
                socketRelayVideo = canalVideo;

                String autenticacao = lerLinhaRelay(canalVideo.getInputStream());
                String[] partes = autenticacao != null ? autenticacao.split(":", 3) : new String[0];
                if (partes.length != 3 || !"AUTH".equals(partes[0])) {
                    enviarRespostaRelay(canalVideo, "REJECTED:Autenticacao invalida");
                    continue;
                }

                identificadorSessao = partes[2];
                synchronized (monitorSessaoRelay) {
                    if (!identificadorSessao.equals(identificadorSessaoAceita)) {
                        enviarRespostaRelay(canalVideo, "REJECTED:Sessao nao autorizada");
                        continue;
                    }
                }

                enviarRespostaRelay(canalVideo, "ACCEPTED");
                TransmissorTela transmissor = new TransmissorTela(
                        new DataOutputStream(canalVideo.getOutputStream()), new Robot());
                transmissorTelaAtivo = transmissor;
                transmissor.run();
            } catch (Exception e) {
                System.out.println("Canal de video relay encerrado: " + e.getMessage());
            } finally {
                if (identificadorSessao != null) {
                    encerrarSessaoRelay(identificadorSessao);
                }
                limparCanalRelay("VIDEO", canalVideo);
                aguardarNovaTentativaRelay();
            }
        }
    }

    private Socket registrarCanalRelay(String host, int porta, String id, String canal) throws Exception {
        Socket socketCanal = new Socket();
        try {
            socketCanal.setTcpNoDelay(true);
            socketCanal.setKeepAlive(true);
            socketCanal.setSendBufferSize(16 * 1024);
            socketCanal.setReceiveBufferSize(16 * 1024);
            socketCanal.connect(new java.net.InetSocketAddress(host, porta), 5000);
            PrintWriter saidaRegistro = new PrintWriter(socketCanal.getOutputStream(), true);
            saidaRegistro.println("REGISTRAR_CANAL_RELAY:" + id + ":" + canal);
            socketCanal.setSoTimeout(5000);
            String resposta = lerLinhaRelay(socketCanal.getInputStream());
            if (!"REGISTRO_OK".equals(resposta)) {
                if (resposta != null && resposta.contains("Comando desconhecido")) {
                    throw new IOException("Backend remoto desatualizado; reconstrua o container backend-1");
                }
                throw new IOException("Registro do canal recusado: " + resposta);
            }
            socketCanal.setSoTimeout(0);
            System.out.println("Canal relay registrado: " + id + " [" + canal + "] em " + host + ":" + porta);
            return socketCanal;
        } catch (Exception e) {
            fecharSocketRelay(socketCanal);
            throw e;
        }
    }

    private Socket registrarCanalRelay(String id, String canal) throws Exception {
        return registrarCanalRelay(SERVIDOR_HOST, SERVIDOR_PORTA, id, canal);
    }

    private boolean solicitarAutorizacaoRemota(String idRemoto) throws InterruptedException {
        CountDownLatch confirmacao = new CountDownLatch(1);
        boolean[] autorizado = {false};
        Platform.runLater(() -> {
            try {
                autorizado[0] = com.sicad.AuxiliarDialogo.mostrarDialogoSolicitacaoConexao(idRemoto, 60);
            } finally {
                confirmacao.countDown();
            }
        });
        return confirmacao.await(65, TimeUnit.SECONDS) && autorizado[0];
    }

    private String lerLinhaRelay(InputStream entrada) throws Exception {
        ByteArrayOutputStream conteudo = new ByteArrayOutputStream();
        int byteLido;
        while ((byteLido = entrada.read()) != -1 && byteLido != '\n') {
            if (conteudo.size() >= 4096) {
                throw new IOException("Linha de handshake relay muito extensa");
            }
            conteudo.write(byteLido);
        }
        return conteudo.size() > 0 ? conteudo.toString("UTF-8").trim() : null;
    }

    private void enviarRespostaRelay(Socket socketCanal, String resposta) throws Exception {
        OutputStream saida = socketCanal.getOutputStream();
        saida.write((resposta + "\n").getBytes());
        saida.flush();
    }

    private void encerrarSessaoRelay(String identificadorEsperado) {
        Socket controle;
        Socket video;
        TransmissorTela transmissor;
        synchronized (monitorSessaoRelay) {
            if (identificadorEsperado != null && !identificadorEsperado.equals(identificadorSessaoAceita)) {
                return;
            }
            identificadorSessaoAceita = null;
            controle = socketRelayControle;
            video = socketRelayVideo;
            transmissor = transmissorTelaAtivo;
            socketRelayControle = null;
            socketRelayVideo = null;
            transmissorTelaAtivo = null;
        }
        if (transmissor != null) {
            transmissor.pararTransmissao();
        }
        fecharSocketRelay(controle);
        fecharSocketRelay(video);
    }

    private void limparCanalRelay(String canal, Socket socketCanal) {
        synchronized (monitorSessaoRelay) {
            if ("CONTROLE".equals(canal) && socketRelayControle == socketCanal) {
                socketRelayControle = null;
            } else if ("VIDEO".equals(canal) && socketRelayVideo == socketCanal) {
                socketRelayVideo = null;
            }
        }
        fecharSocketRelay(socketCanal);
    }

    private void fecharSocketRelay(Socket socketCanal) {
        if (socketCanal == null) {
            return;
        }
        try {
            socketCanal.close();
        } catch (Exception e) {
        }
    }

    private void aguardarNovaTentativaRelay() {
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private VBox criarBarraLateral() {
        VBox sidebar = new VBox(15);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPadding(new Insets(20, 0, 20, 0));
        sidebar.setPrefWidth(70);
        sidebar.setAlignment(Pos.TOP_CENTER);

        sidebarButtons.clear();

        String homeIcon = "M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z";
        String linkIcon = "M3.9 12c0-1.71 1.39-3.1 3.1-3.1h4V7H7c-2.76 0-5 2.24-5 5s2.24 5 5 5h4v-1.9H7c-1.71 0-3.1-1.39-3.1-3.1zM8 13h8v-2H8v2zm9-6h-4v1.9h4c1.71 0 3.1 1.39 3.1 3.1s-1.39 3.1-3.1 3.1h-4V17h4c2.76 0 5-2.24 5-5s-2.24-5-5-5z";
        String starIcon = "M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z";
        String clockIcon = "M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zM12 20c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8zm.5-13H11v6l5.25 3.15.75-1.23-4.5-2.67z";
        String settingsIcon = "M19.14,12.94c0.04-0.3,0.06-0.61,0.06-0.94c0-0.32-0.02-0.64-0.06-0.94l2.03-1.58c0.18-0.14,0.23-0.41,0.12-0.61 l-1.92-3.32c-0.12-0.22-0.37-0.29-0.59-0.22l-2.39,0.96c-0.5-0.38-1.03-0.7-1.62-0.94L14.4,2.81c-0.04-0.24-0.24-0.41-0.48-0.41 h-3.84c-0.24,0-0.43,0.17-0.47,0.41L9.25,5.35C8.66,5.59,8.12,5.92,7.63,6.29L5.24,5.33c-0.22-0.08-0.47,0-0.59,0.22L2.73,8.87 C2.62,9.08,2.66,9.34,2.86,9.48l2.03,1.58C4.84,11.36,4.8,11.69,4.8,12s0.02,0.64,0.06,0.94l-2.03,1.58 c-0.18,0.14-0.23,0.41-0.12,0.61l1.92,3.32c0.12,0.22,0.37,0.29,0.59,0.22l2.39-0.96c0.5,0.38,1.03,0.7,1.62,0.94l0.36,2.54 c0.05,0.24,0.24,0.41,0.48,0.41h3.84c0.24,0,0.43-0.17,0.47-0.41l0.36-2.54c0.59-0.24,1.13-0.56,1.62-0.94l2.39,0.96 c0.22,0.08,0.47,0,0.59-0.22l1.92-3.32c0.12-0.22,0.07-0.49-0.12-0.61L19.14,12.94z M12,15.6c-1.98,0-3.6-1.62-3.6-3.6 s1.62-3.6,3.6-3.6s3.6,1.62,3.6,3.6S13.98,15.6,12,15.6z";

        sidebar.getChildren().addAll(
                criarBotaoBarraLateral("Início", homeIcon, true, () -> exibirDashboard()),
                criarEspacador(),
                criarBotaoBarraLateral("Configurações", settingsIcon, false, () -> exibirConfiguracoes())
        );

        return sidebar;
    }

    private Region criarEspacador() {
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    private StackPane criarBotaoBarraLateral(String tooltipText, String svg, boolean isActive, Runnable onClick) {
        StackPane btn = new StackPane();
        btn.getStyleClass().add("sidebar-btn");
        if (isActive) btn.getStyleClass().add("active");

        SVGPath path = new SVGPath();
        path.setContent(svg);
        path.getStyleClass().add("sidebar-icon");

        Tooltip tooltip = new Tooltip(tooltipText);
        tooltip.setShowDelay(javafx.util.Duration.millis(200));
        tooltip.setStyle("-fx-font-size: 13px; -fx-background-color: #1E293B; -fx-text-fill: #E2E8F0; -fx-padding: 6px 12px; -fx-background-radius: 4px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.4), 10, 0, 0, 4);");
        Tooltip.install(btn, tooltip);

        btn.getChildren().add(path);
        btn.setOnMouseClicked(e -> {
            for (StackPane b : sidebarButtons) {
                b.getStyleClass().remove("active");
            }
            btn.getStyleClass().add("active");
            if (onClick != null) {
                onClick.run();
            }
        });
        sidebarButtons.add(btn);
        return btn;
    }

    private HBox criarCabecalho() {
        HBox header = new HBox(15);
        header.getStyleClass().add("header");
        header.setAlignment(Pos.CENTER_LEFT);

        ImageView logo = new ImageView(new Image(getClass().getResourceAsStream("/com/sicad/assets/logo.png")));
        logo.setFitHeight(40);
        logo.setPreserveRatio(true);

        VBox titles = new VBox();
        Label title = new Label("SICAD");
        title.getStyleClass().add("title");
        Label subtitle = new Label("Sistema Inteligente de Conexão e Acesso Remoto");
        subtitle.getStyleClass().add("subtitle");
        titles.getChildren().addAll(title, subtitle);

        ComboBox<String> themeSelector = new ComboBox<>();
        themeSelector.getItems().addAll("Dark", "Claro", "Azul", "Laranja");
        themeSelector.setValue("Dark");
        themeSelector.setStyle("-fx-background-color: transparent; -fx-text-fill: #A9B4D0; -fx-border-color: rgba(255,255,255,0.1); -fx-border-radius: 6;");
        themeSelector.setOnAction(e -> {
            root.getStyleClass().removeAll("theme-light", "theme-blue", "theme-orange");
            switch (themeSelector.getValue()) {
                case "Claro": root.getStyleClass().add("theme-light"); break;
                case "Azul": root.getStyleClass().add("theme-blue"); break;
                case "Laranja": root.getStyleClass().add("theme-orange"); break;
            }
        });

        VBox statusContainer = new VBox(5);
        statusContainer.setAlignment(Pos.CENTER_RIGHT);

        HBox statusBox = new HBox(8);
        statusBox.setAlignment(Pos.CENTER_RIGHT);

        statusDot = new Circle(4, Color.web("#10B981"));
        statusText = new Label("Conectado");
        statusText.getStyleClass().add("text-secondary");
        statusBox.getChildren().addAll(statusDot, statusText);

        btnReconnect = new Button("Reconectar");
        btnReconnect.getStyleClass().add("btn-secondary");
        btnReconnect.setStyle("-fx-font-size: 11px; -fx-padding: 4 8;");
        btnReconnect.setVisible(false);
        btnReconnect.setManaged(false);
        btnReconnect.setOnAction(e -> {
            btnReconnect.setDisable(true);
            btnReconnect.setText("Conectando...");
            new Thread(() -> {
                conexaoServidor.conectarComFallback(
                        SERVIDOR_HOST, SERVIDOR_PORTA,
                        SERVIDOR_HOST, SERVIDOR_PORTA
                );
                inicializarID();
            }).start();
        });

        statusContainer.getChildren().addAll(statusBox, btnReconnect);

        header.getChildren().addAll(logo, titles, criarEspacador(), themeSelector, statusContainer);
        return header;
    }

    private VBox criarConteudoDashboard() {
        VBox content = new VBox(30);
        content.setPadding(new Insets(40, 60, 40, 60));

        HBox connectionArea = new HBox(40);
        connectionArea.setAlignment(Pos.TOP_CENTER);

        VBox myIdCard = criarCartaoMeuId();
        VBox connectCard = criarCartaoConexao();

        HBox.setHgrow(myIdCard, Priority.ALWAYS);
        HBox.setHgrow(connectCard, Priority.ALWAYS);

        connectionArea.getChildren().addAll(myIdCard, connectCard);

        HBox mainSections = new HBox(40);
        mainSections.setAlignment(Pos.TOP_LEFT);

        VBox recentsSection = new VBox(15);
        Label recentsTitle = new Label("Sessões Recentes");
        recentsTitle.getStyleClass().add("title");
        recentsTitle.setStyle("-fx-font-size: 18px;");
        recentsGrid = new FlowPane(20, 20);
        HBox.setHgrow(recentsSection, Priority.ALWAYS);
        atualizarRecentes();
        recentsSection.getChildren().addAll(recentsTitle, recentsGrid);
        mainSections.getChildren().addAll(recentsSection);

        content.getChildren().addAll(connectionArea, mainSections);
        return content;
    }

    private VBox criarCartaoMeuId() {
        VBox card = new VBox(15);
        card.getStyleClass().add("card");
        card.setAlignment(Pos.CENTER);

        Label title = new Label("Seu ID");
        title.getStyleClass().add("subtitle");

        idLabel = new Label(meuID != null ? meuID : "Carregando...");
        idLabel.getStyleClass().add("id-label");

        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER);
        Button copyBtn = new Button("Copiar");
        copyBtn.getStyleClass().add("btn-secondary");
        copyBtn.setOnAction(e -> {
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(idLabel.getText());
            clipboard.setContent(content);
        });
        Button qrBtn = new Button("QR Code");
        qrBtn.getStyleClass().add("btn-secondary");
        actions.getChildren().addAll(copyBtn, qrBtn);

        card.getChildren().addAll(title, idLabel, actions);
        return card;
    }

    private VBox criarCartaoConexao() {
        VBox card = new VBox(15);
        card.getStyleClass().add("card");
        card.setAlignment(Pos.CENTER);

        Label title = new Label("Conectar a outro dispositivo");
        title.getStyleClass().add("subtitle");

        idInput = new TextField();
        idInput.setPromptText("Digite o ID do dispositivo");
        idInput.getStyleClass().add("input-modern");
        idInput.setMaxWidth(400);

        idInput.textProperty().addListener((obs, oldText, newText) -> {
            if (newText.equals(oldText)) return;

            String plain = newText.toUpperCase().replaceAll("[^A-Z0-9]", "");
            if (plain.length() > 11) {
                plain = plain.substring(0, 11);
            }

            StringBuilder formatted = new StringBuilder(plain);
            if (plain.length() > 8) {
                formatted.insert(8, "-");
            }

            String finalText = formatted.toString();
            if (!newText.equals(finalText)) {
                javafx.application.Platform.runLater(() -> {
                    idInput.setText(finalText);
                    idInput.positionCaret(finalText.length());
                });
            }
        });

        Button connectBtn = new Button("Conectar");
        connectBtn.getStyleClass().add("btn-primary");
        connectBtn.setPrefWidth(200);

        connectBtn.setOnAction(e -> {
            String targetId = idInput.getText().trim();

            if (targetId.isEmpty()) {
                mostrarAlerta("Erro", "ID inválido", "Por favor, digite um ID válido.", Alert.AlertType.WARNING);
                return;
            }

            if (targetId.equals(idLabel.getText())) {
                mostrarAlerta("Aviso", "Conexão inválida", "Você não pode conectar ao seu próprio dispositivo.", Alert.AlertType.WARNING);
                return;
            }

            if (!conexaoServidor.isConectado()) {
                mostrarAlerta("Erro", "Sem conexão", "Você não está conectado ao servidor.", Alert.AlertType.ERROR);
                return;
            }

            GerenciadorHistorico.adicionarConexao(targetId);

            Platform.runLater(() -> {
                atualizarRecentes();
            });

            connectBtn.setDisable(true);
            connectBtn.setText("Conectando...");

            new Thread(() -> {
                Platform.runLater(() -> {
                    connectBtn.setDisable(false);
                    connectBtn.setText("Conectar");
                });

                ClienteDesktopRemoto client = new ClienteDesktopRemoto(targetId, idLabel.getText());
                client.conectarRelay(SERVIDOR_HOST, SERVIDOR_PORTA);
            }).start();
        });

        card.getChildren().addAll(title, idInput, connectBtn);
        return card;
    }

    private void mostrarAlerta(String title, String header, String content, Alert.AlertType type) {
        if (type == Alert.AlertType.ERROR || type == Alert.AlertType.WARNING) {
            com.sicad.AuxiliarDialogo.mostrarDialogoErro(header, content);
        } else {
            com.sicad.AuxiliarDialogo.mostrarDialogoInformativo(header, content);
        }
    }

    private void atualizarRecentes() {
        if (recentsGrid == null) return;
        recentsGrid.getChildren().clear();
        List<String> historico = GerenciadorHistorico.carregarHistorico();
        if (historico.isEmpty()) {
            Label noRecents = new Label("Nenhuma conexão recente.");
            noRecents.getStyleClass().add("text-secondary");
            noRecents.setStyle("-fx-font-style: italic; -fx-padding: 10 0 0 0;");
            recentsGrid.getChildren().add(noRecents);
        } else {
            for (String id : historico) {
                recentsGrid.getChildren().add(criarCartaoRecente(id));
            }
        }
    }

    private VBox criarCartaoRecente(String id) {
        VBox card = new VBox(8);
        card.getStyleClass().add("session-card");
        card.setPrefWidth(240);
        card.setStyle("-fx-cursor: hand;");

        String monitorSvg = "M21 2H3c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h7v2H8v2h8v-2h-2v-2h7c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm0 14H3V4h18v12z";

        SVGPath icon = new SVGPath();
        icon.setContent(monitorSvg);
        icon.setFill(Color.web("#A9B4D0"));

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        Label nameLbl = new Label("Dispositivo");
        nameLbl.getStyleClass().add("text-primary");
        nameLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 15px;");
        header.getChildren().addAll(icon, nameLbl);

        Label idLbl = new Label(id);
        idLbl.getStyleClass().add("text-primary");
        idLbl.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 14px;");

        card.getChildren().addAll(header, idLbl);

        card.setOnMouseClicked(e -> {
            if (idInput != null) {
                idInput.setText(id);
            }
        });
        return card;
    }

    private void exibirDashboard() {
        if (centerScrollPane != null) {
            centerScrollPane.setContent(criarConteudoDashboard());
        }
    }

    private void exibirConfiguracoes() {
        if (centerScrollPane != null) {
            centerScrollPane.setContent(criarConteudoConfiguracoes());
        }
    }

    private VBox criarConteudoConfiguracoes() {
        VBox content = new VBox(25);
        content.setPadding(new Insets(30));
        content.getStyleClass().add("content-area");

        Label mainTitle = new Label("Configurações do Sistema");
        mainTitle.getStyleClass().add("title");
        mainTitle.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");

        java.util.Properties props = GerenciadorConfiguracoes.carregarConfiguracoes();

        VBox redeSection = new VBox(15);
        redeSection.getStyleClass().add("card");
        redeSection.setPadding(new Insets(20));

        Label redeTitle = new Label("Parâmetros de Rede");
        redeTitle.getStyleClass().add("subtitle");
        redeTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        GridPane redeGrid = new GridPane();
        redeGrid.setHgap(15);
        redeGrid.setVgap(12);

        Label hostLabel = new Label("Servidor Central (Host):");
        hostLabel.getStyleClass().add("text-secondary");
        TextField hostInput = new TextField(props.getProperty("server.host"));
        hostInput.getStyleClass().add("input-modern");
        hostInput.setPrefWidth(300);

        Label portLabel = new Label("Porta do Servidor Central:");
        portLabel.getStyleClass().add("text-secondary");
        TextField portInput = new TextField(props.getProperty("server.port"));
        portInput.getStyleClass().add("input-modern");
        portInput.setPrefWidth(300);

        redeGrid.add(hostLabel, 0, 0);
        redeGrid.add(hostInput, 1, 0);
        redeGrid.add(portLabel, 0, 1);
        redeGrid.add(portInput, 1, 1);

        redeSection.getChildren().addAll(redeTitle, redeGrid);

        VBox casterSection = new VBox(15);
        casterSection.getStyleClass().add("card");
        casterSection.setPadding(new Insets(20));

        Label casterTitle = new Label("Otimizações de Transmissão de Vídeo");
        casterTitle.getStyleClass().add("subtitle");
        casterTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        GridPane casterGrid = new GridPane();
        casterGrid.setHgap(15);
        casterGrid.setVgap(12);

        Label fpsLabel = new Label("Taxa de Quadros Limite (FPS):");
        fpsLabel.getStyleClass().add("text-secondary");

        Slider fpsSlider = new Slider(5, 60, Double.parseDouble(props.getProperty("caster.fps", "15")));
        fpsSlider.setShowTickLabels(true);
        fpsSlider.setShowTickMarks(true);
        fpsSlider.setMajorTickUnit(5);
        fpsSlider.setMinorTickCount(0);
        fpsSlider.setSnapToTicks(true);
        fpsSlider.setPrefWidth(300);

        Label fpsValLabel = new Label(((int) fpsSlider.getValue()) + " FPS");
        fpsValLabel.getStyleClass().add("text-primary");
        fpsValLabel.setStyle("-fx-font-weight: bold;");
        fpsSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            fpsValLabel.setText(newVal.intValue() + " FPS");
        });

        Label qualityLabel = new Label("Qualidade de Compactação JPEG:");
        qualityLabel.getStyleClass().add("text-secondary");

        double currentQuality = Double.parseDouble(props.getProperty("caster.quality", "0.85")) * 100;
        Slider qualitySlider = new Slider(50, 95, Math.min(95, Math.max(50, currentQuality)));
        qualitySlider.setShowTickLabels(true);
        qualitySlider.setShowTickMarks(true);
        qualitySlider.setMajorTickUnit(20);
        qualitySlider.setPrefWidth(300);

        Label qualityValLabel = new Label(((int) qualitySlider.getValue()) + "%");
        qualityValLabel.getStyleClass().add("text-primary");
        qualityValLabel.setStyle("-fx-font-weight: bold;");
        qualitySlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            qualityValLabel.setText(newVal.intValue() + "%");
        });

        casterGrid.add(fpsLabel, 0, 0);
        casterGrid.add(fpsSlider, 1, 0);
        casterGrid.add(fpsValLabel, 2, 0);

        casterGrid.add(qualityLabel, 0, 1);
        casterGrid.add(qualitySlider, 1, 1);
        casterGrid.add(qualityValLabel, 2, 1);

        Label limiteBandaLabel = new Label("Limite do vídeo (Kbps):");
        limiteBandaLabel.getStyleClass().add("text-secondary");

        Slider limiteBandaSlider = new Slider(1000, 10000,
                Double.parseDouble(props.getProperty("caster.maxKbps", "5000")));
        limiteBandaSlider.setShowTickLabels(true);
        limiteBandaSlider.setShowTickMarks(true);
        limiteBandaSlider.setMajorTickUnit(1000);
        limiteBandaSlider.setBlockIncrement(128);
        limiteBandaSlider.setPrefWidth(300);

        Label limiteBandaValor = new Label(((int) limiteBandaSlider.getValue()) + " Kbps");
        limiteBandaValor.getStyleClass().add("text-primary");
        limiteBandaValor.setStyle("-fx-font-weight: bold;");
        limiteBandaSlider.valueProperty().addListener((observavel, valorAntigo, valorNovo) ->
                limiteBandaValor.setText(valorNovo.intValue() + " Kbps"));

        casterGrid.add(limiteBandaLabel, 0, 2);
        casterGrid.add(limiteBandaSlider, 1, 2);
        casterGrid.add(limiteBandaValor, 2, 2);

        Label resolucaoLabel = new Label("Resolução transmitida:");
        resolucaoLabel.getStyleClass().add("text-secondary");

        double resolucaoAtual = Double.parseDouble(props.getProperty("caster.scale", "0.85")) * 100;
        Slider resolucaoSlider = new Slider(50, 100, Math.max(50, Math.min(100, resolucaoAtual)));
        resolucaoSlider.setShowTickLabels(true);
        resolucaoSlider.setShowTickMarks(true);
        resolucaoSlider.setMajorTickUnit(10);
        resolucaoSlider.setPrefWidth(300);

        Label resolucaoValor = new Label(((int) resolucaoSlider.getValue()) + "%");
        resolucaoValor.getStyleClass().add("text-primary");
        resolucaoValor.setStyle("-fx-font-weight: bold;");
        resolucaoSlider.valueProperty().addListener((observavel, valorAntigo, valorNovo) ->
                resolucaoValor.setText(valorNovo.intValue() + "%"));

        casterGrid.add(resolucaoLabel, 0, 3);
        casterGrid.add(resolucaoSlider, 1, 3);
        casterGrid.add(resolucaoValor, 2, 3);

        casterSection.getChildren().addAll(casterTitle, casterGrid);

        HBox buttonBox = new HBox(15);
        buttonBox.setAlignment(Pos.CENTER_LEFT);

        Button saveBtn = new Button("Salvar Alterações");
        saveBtn.getStyleClass().add("btn-primary");
        saveBtn.setPrefWidth(200);

        Label saveMsg = new Label("");
        saveMsg.setStyle("-fx-text-fill: #10B981; -fx-font-weight: bold;");

        saveBtn.setOnAction(e -> {
            try {
                String host = hostInput.getText().trim();
                String portStr = portInput.getText().trim();
                int fps = (int) fpsSlider.getValue();
                float quality = (float) (qualitySlider.getValue() / 100.0);
                int limiteKbps = (int) limiteBandaSlider.getValue();
                float escala = (float) (resolucaoSlider.getValue() / 100.0);

                if (host.isEmpty() || portStr.isEmpty()) {
                    saveMsg.setText("Erro: Preencha todos os campos.");
                    saveMsg.setStyle("-fx-text-fill: #EF4444;");
                    return;
                }

                GerenciadorConfiguracoes.salvarConfiguracoesRede(host, portStr);

                SERVIDOR_HOST = host;
                SERVIDOR_PORTA = Integer.parseInt(portStr);

                if (conexaoServidor != null && conexaoServidor.isConectado() && meuID != null) {
                    String configStr = fps + "," + quality + "," + limiteKbps + "," + escala;
                    new Thread(() -> {
                        try {
                            conexaoServidor.enviarComando("SAVE_CONFIG:" + meuID + ":" + configStr);
                        } catch (Exception ex) {
                            System.out.println("Erro ao salvar config no servidor: " + ex.getMessage());
                        }
                    }, "salvar-config-servidor").start();
                }
                GerenciadorConfiguracoes.carregarCasterSettingsDoServidor(
                        String.valueOf(fps), String.valueOf(quality),
                        String.valueOf(limiteKbps), String.valueOf(escala));

                saveMsg.setText("Configurações salvas para a próxima sessão!");
                saveMsg.setStyle("-fx-text-fill: #10B981;");
            } catch (Exception ex) {
                saveMsg.setText("Erro ao salvar: " + ex.getMessage());
                saveMsg.setStyle("-fx-text-fill: #EF4444;");
            }
        });

        buttonBox.getChildren().addAll(saveBtn, saveMsg);

        content.getChildren().addAll(mainTitle, redeSection, casterSection, buttonBox);
        return content;
    }

    public static void main(String[] args) {
        launch();
    }
}
