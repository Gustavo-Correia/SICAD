package com.sicad;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;

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
import javafx.stage.Stage;
    
import com.sicad.remote.RemoteDesktopClient;
import com.sicad.remote.ScreenCaster;
import com.sicad.remote.InputReceiver;
import java.awt.Robot;

interface Kernel32 extends StdCallLibrary {
    Kernel32 INSTANCE = Native.load("kernel32", Kernel32.class);
    boolean AllocConsole();
}

public class Main extends Application {

    /** Mude para true se quiser exibir o terminal com logs */
    public static final boolean SHOW_CONSOLE = false;

    /** Subdomínio público (Cloudflare Tunnel → nginx:8080) */
    public static final String SERVIDOR_REMOTO_HOST = "sicad.felipesilva.tec.br";
    public static final int PORTA_LOCAL = 8080;
    public static final int PORTA_REMOTA = 40762;

    /**
     * Endereço público do túnel TCP para acesso remoto (porta 25457).
     * Deixe vazio "" para usar o IP local (mesma rede).
     * Ex: "bore.pub:12345"
     */
    public static final String REMOTE_DESKTOP_PUBLIC_ADDR = "bore.pub:40762";

    private BorderPane root;
    private ConexaoServidor conexaoServidor;
    private Circle statusDot;
    private Label statusText;
    private Label idLabel;
    
    private volatile Socket relaySocket;

    @Override
    public void start(Stage stage) {
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

        // 1. Sidebar (Left)
        VBox sidebar = createSidebar();
        root.setLeft(sidebar);

        // 2. Top Area (Header + Navigation)
        VBox topArea = new VBox();
        topArea.getChildren().add(createHeader());
        root.setTop(topArea);

        // 3. Main Content (Center)
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("scroll-pane");
        scrollPane.setContent(createDashboardContent());
        root.setCenter(scrollPane);

        Scene scene = new Scene(root, 1400, 850);
        String css = getClass().getResource("/com/sicad/styles.css").toExternalForm();
        scene.getStylesheets().add(css);

        stage.setTitle("SICAD - Sistema Integrado de Conexão");
        stage.setScene(scene);
        stage.setMinWidth(1000);
        stage.setMinHeight(700);
        stage.show();
        
        this.conexaoServidor = new ConexaoServidor(this);

        this.conexaoServidor.conectarComFallback(
                "127.0.0.1", PORTA_LOCAL,
                SERVIDOR_REMOTO_HOST, PORTA_REMOTA
        );   

        // Iniciar verificação/geração de ID em background (usa a mesma conexão)
        inicializarID();
    }

    @Override
    public void stop() throws Exception {
        if (conexaoServidor != null) {
            conexaoServidor.desconectarServidor();
        }
        if (relaySocket != null) {
            try { relaySocket.close(); } catch (Exception e) {}
        }
        super.stop();
    } 

    public void atualizarStatusConexao(boolean conectado) {
        if (statusDot != null && statusText != null) {
            if (conectado) {
                statusDot.setFill(Color.web("#10B981"));
                statusText.setText("Online");
            } else {
                statusDot.setFill(Color.web("#EF4444"));
                statusText.setText("Offline");
            }
        }
    }

    /**
     * Atualiza o label de ID na tela principal.
     */
    public void atualizarID(String id) {
        if (idLabel != null) {
            idLabel.setText(id);
        }
    }

    /**
     * Inicializa o ID da máquina:
     * 1. Aguarda a conexão com o servidor ficar pronta
     * 2. Consulta o servidor usando o IP local
     * 3. Se não encontrar, gera um novo ID e registra
     * 4. Atualiza o label na UI
     */
    private void inicializarID() {
        new Thread(() -> {
            // Espera a conexão ficar pronta (máx ~15s — inclui probe local + remoto)
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

            Platform.runLater(() -> {
                atualizarID(id);
            });

            iniciarRelayHost(id);
        }, "inicializar-id").start();
    }

    private void iniciarRelayHost(String id) {
        new Thread(() -> {
            while (!Thread.interrupted()) {
                try {
                    Socket sock = new Socket(SERVIDOR_REMOTO_HOST, PORTA_REMOTA);
                    relaySocket = sock;
                    PrintWriter out = new PrintWriter(sock.getOutputStream(), true);
                    out.println("REGISTER_RELAY:" + id);
                    System.out.println("Relay host registrado: " + id);

                    InputStream in = sock.getInputStream();
                    Robot robot = new Robot();

                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    int b;
                    while ((b = in.read()) != -1 && b != '\n') {
                        baos.write(b);
                    }
                    if (baos.size() == 0) {
                        sock.close();
                        continue;
                    }

                    String line = baos.toString("UTF-8").trim();
                    if (!line.startsWith("AUTH:")) {
                        sock.close();
                        continue;
                    }

                    String remoteId = line.substring(5);
                    System.out.println("Relay AUTH recebido de: " + remoteId);

                    CountDownLatch latch = new CountDownLatch(1);
                    final boolean[] accepted = {false};
                    Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                        alert.setTitle("Solicitação de Acesso Remoto");
                        alert.setHeaderText("Conexão Recebida");
                        alert.setContentText("O dispositivo " + remoteId + " deseja controlar sua máquina. Permitir?");
                        alert.showAndWait().ifPresent(response -> {
                            accepted[0] = response == ButtonType.OK;
                        });
                        latch.countDown();
                    });
                    latch.await();

                    OutputStream os = sock.getOutputStream();
                    if (accepted[0]) {
                        os.write("ACCEPTED\n".getBytes());
                        os.flush();

                        DataOutputStream dataOut = new DataOutputStream(os);
                        ScreenCaster caster = new ScreenCaster(dataOut, robot);
                        InputReceiver receiver = new InputReceiver(sock, robot);

                        Thread casterThread = new Thread(caster, "relay-caster");
                        Thread receiverThread = new Thread(receiver, "relay-receiver");
                        casterThread.start();
                        receiverThread.start();

                        try {
                            receiverThread.join();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        caster.stopCasting();
                        System.out.println("Sessão remota encerrada via relay.");
                    } else {
                        os.write("REJECTED:Acesso negado pelo usuÃ¡rio\n".getBytes());
                        os.flush();
                    }

                    sock.close();
                } catch (Exception e) {
                    System.out.println("Relay host: " + e.getMessage());
                    try { Thread.sleep(3000); } catch (InterruptedException ie) { break; }
                } finally {
                    if (relaySocket != null) {
                        try { relaySocket.close(); } catch (Exception e) {}
                        relaySocket = null;
                    }
                }
            }
        }, "relay-host").start();
    }

    private VBox createSidebar() {
        VBox sidebar = new VBox(15);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPadding(new Insets(20, 0, 20, 0));
        sidebar.setPrefWidth(70);
        sidebar.setAlignment(Pos.TOP_CENTER);

        String homeIcon     = "M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z";
        String settingsIcon = "M19.14,12.94c0.04-0.3,0.06-0.61,0.06-0.94c0-0.32-0.02-0.64-0.06-0.94l2.03-1.58c0.18-0.14,0.23-0.41,0.12-0.61 l-1.92-3.32c-0.12-0.22-0.37-0.29-0.59-0.22l-2.39,0.96c-0.5-0.38-1.03-0.7-1.62-0.94L14.4,2.81c-0.04-0.24-0.24-0.41-0.48-0.41 h-3.84c-0.24,0-0.43,0.17-0.47,0.41L9.25,5.35C8.66,5.59,8.12,5.92,7.63,6.29L5.24,5.33c-0.22-0.08-0.47,0-0.59,0.22L2.73,8.87 C2.62,9.08,2.66,9.34,2.86,9.48l2.03,1.58C4.84,11.36,4.8,11.69,4.8,12s0.02,0.64,0.06,0.94l-2.03,1.58 c-0.18,0.14-0.23,0.41-0.12,0.61l1.92,3.32c0.12,0.22,0.37,0.29,0.59,0.22l2.39-0.96c0.5,0.38,1.03,0.7,1.62,0.94l0.36,2.54 c0.05,0.24,0.24,0.41,0.48,0.41h3.84c0.24,0,0.43-0.17,0.47-0.41l0.36-2.54c0.59-0.24,1.13-0.56,1.62-0.94l2.39,0.96 c0.22,0.08,0.47,0,0.59-0.22l1.92-3.32c0.12-0.22,0.07-0.49-0.12-0.61L19.14,12.94z M12,15.6c-1.98,0-3.6-1.62-3.6-3.6 s1.62-3.6,3.6-3.6s3.6,1.62,3.6,3.6S13.98,15.6,12,15.6z";

        sidebar.getChildren().addAll(
                createSidebarButton(homeIcon, true),
                createSpacer(),
                createSidebarButton(settingsIcon, false)
        );

        return sidebar;
    }

    private Region createSpacer() {
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    private StackPane createSidebarButton(String svg, boolean isActive) {
        StackPane btn = new StackPane();
        btn.getStyleClass().add("sidebar-btn");
        if (isActive) btn.getStyleClass().add("active");

        SVGPath path = new SVGPath();
        path.setContent(svg);
        path.getStyleClass().add("sidebar-icon");
        
        btn.getChildren().add(path);
        return btn;
    }

    private HBox createHeader() {
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

        // Theme Selector
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

        // Status Indicator
        HBox statusBox = new HBox(8);
        statusBox.setAlignment(Pos.CENTER);

        statusDot = new Circle(4, Color.web("#10B981"));
        statusText = new Label("Conectado");
        statusText.getStyleClass().add("text-secondary");
        statusBox.getChildren().addAll(statusDot, statusText);

        header.getChildren().addAll(logo, titles, createSpacer(), themeSelector, statusBox);
        return header;
    }



    private VBox createDashboardContent() {
        VBox content = new VBox(30);
        content.setPadding(new Insets(40, 60, 40, 60));

        // Área de conexão: card do ID + card de conectar
        HBox connectionArea = new HBox(40);
        connectionArea.setAlignment(Pos.TOP_CENTER);

        VBox myIdCard = createMyIdCard();
        VBox connectCard = createConnectCard();

        HBox.setHgrow(myIdCard, Priority.ALWAYS);
        HBox.setHgrow(connectCard, Priority.ALWAYS);

        connectionArea.getChildren().addAll(myIdCard, connectCard);
        content.getChildren().add(connectionArea);
        return content;
    }

    private VBox createMyIdCard() {
        VBox card = new VBox(15);
        card.getStyleClass().add("card");
        card.setAlignment(Pos.CENTER);

        Label title = new Label("Seu ID");
        title.getStyleClass().add("subtitle");

        idLabel = new Label("Carregando...");
        idLabel.getStyleClass().add("id-label");

        Button copyBtn = new Button("Copiar ID");
        copyBtn.getStyleClass().add("btn-secondary");
        copyBtn.setOnAction(e -> {
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(idLabel.getText());
            clipboard.setContent(content);
        });

        card.getChildren().addAll(title, idLabel, copyBtn);
        return card;
    }

    private VBox createConnectCard() {
        VBox card = new VBox(15);
        card.getStyleClass().add("card");
        card.setAlignment(Pos.CENTER);

        Label title = new Label("Conectar a outro dispositivo");
        title.getStyleClass().add("subtitle");

        TextField input = new TextField();
        input.setPromptText("Digite o ID do dispositivo");
        input.getStyleClass().add("input-modern");
        input.setMaxWidth(400);

        Button connectBtn = new Button("Conectar");
        connectBtn.getStyleClass().add("btn-primary");
        connectBtn.setPrefWidth(200);

        connectBtn.setOnAction(e -> {
            String targetId = input.getText().trim();
            
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

            connectBtn.setDisable(true);
            connectBtn.setText("Conectando...");

            new Thread(() -> {
                Platform.runLater(() -> {
                    connectBtn.setDisable(false);
                    connectBtn.setText("Conectar");
                });

                RemoteDesktopClient client = new RemoteDesktopClient(targetId, idLabel.getText());
                client.connectRelay(SERVIDOR_REMOTO_HOST, PORTA_REMOTA);
            }).start();
        });

        card.getChildren().addAll(title, input, connectBtn);
        return card;
    }

    private void mostrarAlerta(String title, String header, String content, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch();
    }
}