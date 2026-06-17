package com.sicad;

import javafx.application.Application;
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

public class Main extends Application {

    private BorderPane root;

    @Override
    public void start(Stage stage) {
        root = new BorderPane();
        root.getStyleClass().add("root");

        // 1. Sidebar (Left)
        VBox sidebar = createSidebar();
        root.setLeft(sidebar);

        // 2. Top Area (Header + Navigation)
        VBox topArea = new VBox();
        topArea.getChildren().addAll(createHeader(), createNavigationBar());
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
    }

    private VBox createSidebar() {
        VBox sidebar = new VBox(15);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPadding(new Insets(20, 0, 20, 0));
        sidebar.setPrefWidth(70);
        sidebar.setAlignment(Pos.TOP_CENTER);

        // Icons SVG Paths
        String homeIcon = "M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z";
        String linkIcon = "M3.9 12c0-1.71 1.39-3.1 3.1-3.1h4V7H7c-2.76 0-5 2.24-5 5s2.24 5 5 5h4v-1.9H7c-1.71 0-3.1-1.39-3.1-3.1zM8 13h8v-2H8v2zm9-6h-4v1.9h4c1.71 0 3.1 1.39 3.1 3.1s-1.39 3.1-3.1 3.1h-4V17h4c2.76 0 5-2.24 5-5s-2.24-5-5-5z";
        String starIcon = "M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z";
        String clockIcon = "M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zM12 20c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8zm.5-13H11v6l5.25 3.15.75-1.23-4.5-2.67z";
        String settingsIcon = "M19.14,12.94c0.04-0.3,0.06-0.61,0.06-0.94c0-0.32-0.02-0.64-0.06-0.94l2.03-1.58c0.18-0.14,0.23-0.41,0.12-0.61 l-1.92-3.32c-0.12-0.22-0.37-0.29-0.59-0.22l-2.39,0.96c-0.5-0.38-1.03-0.7-1.62-0.94L14.4,2.81c-0.04-0.24-0.24-0.41-0.48-0.41 h-3.84c-0.24,0-0.43,0.17-0.47,0.41L9.25,5.35C8.66,5.59,8.12,5.92,7.63,6.29L5.24,5.33c-0.22-0.08-0.47,0-0.59,0.22L2.73,8.87 C2.62,9.08,2.66,9.34,2.86,9.48l2.03,1.58C4.84,11.36,4.8,11.69,4.8,12s0.02,0.64,0.06,0.94l-2.03,1.58 c-0.18,0.14-0.23,0.41-0.12,0.61l1.92,3.32c0.12,0.22,0.37,0.29,0.59,0.22l2.39-0.96c0.5,0.38,1.03,0.7,1.62,0.94l0.36,2.54 c0.05,0.24,0.24,0.41,0.48,0.41h3.84c0.24,0,0.43-0.17,0.47-0.41l0.36-2.54c0.59-0.24,1.13-0.56,1.62-0.94l2.39,0.96 c0.22,0.08,0.47,0,0.59-0.22l1.92-3.32c0.12-0.22,0.07-0.49-0.12-0.61L19.14,12.94z M12,15.6c-1.98,0-3.6-1.62-3.6-3.6 s1.62-3.6,3.6-3.6s3.6,1.62,3.6,3.6S13.98,15.6,12,15.6z";

        sidebar.getChildren().addAll(
                createSidebarButton(homeIcon, true),
                createSidebarButton(linkIcon, false),
                createSidebarButton(starIcon, false),
                createSidebarButton(clockIcon, false),
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
        Circle statusDot = new Circle(4, Color.web("#10B981"));
        Label statusText = new Label("Online");
        statusText.getStyleClass().add("text-secondary");
        statusBox.getChildren().addAll(statusDot, statusText);

        header.getChildren().addAll(logo, titles, createSpacer(), themeSelector, statusBox);
        return header;
    }

    private HBox createNavigationBar() {
        HBox navBar = new HBox(10);
        navBar.getStyleClass().add("nav-bar");
        navBar.setAlignment(Pos.CENTER_LEFT);

        String[] tabs = {"INÍCIO", "FAVORITOS", "SESSÕES RECENTES", "DISPOSITIVOS", "CONVITES"};
        for (int i = 0; i < tabs.length; i++) {
            Label tab = new Label(tabs[i]);
            tab.getStyleClass().add("nav-tab");
            if (i == 0) tab.getStyleClass().add("active");
            navBar.getChildren().add(tab);
        }
        return navBar;
    }

    private VBox createDashboardContent() {
        VBox content = new VBox(30);
        content.setPadding(new Insets(40, 60, 40, 60));

        // 1. Connection Area (ID and Connect)
        HBox connectionArea = new HBox(40);
        connectionArea.setAlignment(Pos.TOP_CENTER);
        
        VBox myIdCard = createMyIdCard();
        VBox connectCard = createConnectCard();
        
        HBox.setHgrow(myIdCard, Priority.ALWAYS);
        HBox.setHgrow(connectCard, Priority.ALWAYS);
        
        connectionArea.getChildren().addAll(myIdCard, connectCard);

        // 2. Recent Sessions
        VBox recentsSection = new VBox(15);
        Label recentsTitle = new Label("Sessões Recentes");
        recentsTitle.getStyleClass().add("title");
        recentsTitle.setStyle("-fx-font-size: 18px;");

        FlowPane recentsGrid = new FlowPane(20, 20);
        recentsGrid.getChildren().addAll(
            createRecentCard("Notebook Casa", "EC102345", "Ontem", "desktop"),
            createRecentCard("PC Escritório", "AB987654", "Há 2 horas", "desktop"),
            createRecentCard("Macbook Pro", "XY123456", "12/06/2026", "desktop"),
            createRecentCard("Celular Pessoal", "ZW987654", "10/06/2026", "mobile")
        );
        recentsSection.getChildren().addAll(recentsTitle, recentsGrid);

        // 3. Info Panel
        VBox infoSection = new VBox(15);
        Label infoTitle = new Label("Estatísticas");
        infoTitle.getStyleClass().add("title");
        infoTitle.setStyle("-fx-font-size: 18px;");

        HBox statsGrid = new HBox(20);
        statsGrid.getChildren().addAll(
            createStatCard("Conexões Hoje", "12"),
            createStatCard("Dispositivos", "25"),
            createStatCard("Último Acesso", "Hoje 14:32")
        );
        infoSection.getChildren().addAll(infoTitle, statsGrid);

        content.getChildren().addAll(connectionArea, recentsSection, infoSection);
        return content;
    }

    private VBox createMyIdCard() {
        VBox card = new VBox(15);
        card.getStyleClass().add("card");
        card.setAlignment(Pos.CENTER);

        Label title = new Label("Seu ID");
        title.getStyleClass().add("subtitle");

        Label idLabel = new Label("EC103156-6DC");
        idLabel.getStyleClass().add("id-label");

        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER);
        Button copyBtn = new Button("Copiar");
        copyBtn.getStyleClass().add("btn-secondary");
        Button qrBtn = new Button("QR Code");
        qrBtn.getStyleClass().add("btn-secondary");
        actions.getChildren().addAll(copyBtn, qrBtn);

        card.getChildren().addAll(title, idLabel, actions);
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

        card.getChildren().addAll(title, input, connectBtn);
        return card;
    }

    private VBox createRecentCard(String name, String id, String time, String type) {
        VBox card = new VBox(8);
        card.getStyleClass().add("session-card");
        card.setPrefWidth(240);

        String monitorSvg = "M21 2H3c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h7v2H8v2h8v-2h-2v-2h7c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm0 14H3V4h18v12z";
        String phoneSvg = "M17 1.01L7 1c-1.1 0-2 .9-2 2v18c0 1.1.9 2 2 2h10c1.1 0 2-.9 2-2V3c0-1.1-.9-1.99-2-1.99zM17 19H7V5h10v14z";
        
        SVGPath icon = new SVGPath();
        icon.setContent(type.equals("mobile") ? phoneSvg : monitorSvg);
        icon.setFill(Color.web("#A9B4D0"));

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        Label nameLbl = new Label(name);
        nameLbl.getStyleClass().add("text-primary");
        nameLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 15px;");
        header.getChildren().addAll(icon, nameLbl);

        Label idLbl = new Label(id);
        idLbl.getStyleClass().add("text-primary");
        idLbl.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 14px;");

        Label timeLbl = new Label(time);
        timeLbl.getStyleClass().add("text-secondary");
        timeLbl.setStyle("-fx-font-size: 12px;");

        card.getChildren().addAll(header, idLbl, timeLbl);
        return card;
    }

    private VBox createStatCard(String title, String value) {
        VBox card = new VBox(5);
        card.getStyleClass().add("session-card"); // reusing simple card style
        card.setPrefWidth(200);
        card.setAlignment(Pos.CENTER);

        Label titleLbl = new Label(title);
        titleLbl.getStyleClass().add("text-secondary");

        Label valLbl = new Label(value);
        valLbl.getStyleClass().add("text-primary");
        valLbl.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");

        card.getChildren().addAll(titleLbl, valLbl);
        return card;
    }

    public static void main(String[] args) {
        launch();
    }
}

    private VBox createDashboardContent() {
        VBox content = new VBox(30);
        content.setPadding(new Insets(40, 60, 40, 60));

        // 1. Connection Area (ID and Connect)
        HBox connectionArea = new HBox(40);
        connectionArea.setAlignment(Pos.TOP_CENTER);
        
        VBox myIdCard = createMyIdCard();
        VBox connectCard = createConnectCard();
        
        HBox.setHgrow(myIdCard, Priority.ALWAYS);
        HBox.setHgrow(connectCard, Priority.ALWAYS);
        
        connectionArea.getChildren().addAll(myIdCard, connectCard);

        // 2. Recent Sessions
        VBox recentsSection = new VBox(15);
        Label recentsTitle = new Label("Sessões Recentes");
        recentsTitle.getStyleClass().add("title");
        recentsTitle.setStyle("-fx-font-size: 18px;");

        FlowPane recentsGrid = new FlowPane(20, 20);
        recentsGrid.getChildren().addAll(
            createRecentCard("Notebook Casa", "EC102345", "Ontem", "desktop"),
            createRecentCard("PC Escritório", "AB987654", "Há 2 horas", "desktop"),
            createRecentCard("Macbook Pro", "XY123456", "12/06/2026", "desktop"),
            createRecentCard("Celular Pessoal", "ZW987654", "10/06/2026", "mobile")
        );
        recentsSection.getChildren().addAll(recentsTitle, recentsGrid);

        // 3. Info Panel
        VBox infoSection = new VBox(15);
        Label infoTitle = new Label("Estatísticas");
        infoTitle.getStyleClass().add("title");
        infoTitle.setStyle("-fx-font-size: 18px;");

        HBox statsGrid = new HBox(20);
        statsGrid.getChildren().addAll(
            createStatCard("Conexões Hoje", "12"),
            createStatCard("Dispositivos", "25"),
            createStatCard("Último Acesso", "Hoje 14:32")
        );
        infoSection.getChildren().addAll(infoTitle, statsGrid);

        content.getChildren().addAll(connectionArea, recentsSection, infoSection);
        return content;
    }

    private VBox createMyIdCard() {
        VBox card = new VBox(15);
        card.getStyleClass().add("card");
        card.setAlignment(Pos.CENTER);

        Label title = new Label("Seu ID");
        title.getStyleClass().add("subtitle");

        Label idLabel = new Label("EC103156-6DC");
        idLabel.getStyleClass().add("id-label");

        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER);
        Button copyBtn = new Button("Copiar");
        copyBtn.getStyleClass().add("btn-secondary");
        Button qrBtn = new Button("QR Code");
        qrBtn.getStyleClass().add("btn-secondary");
        actions.getChildren().addAll(copyBtn, qrBtn);

        card.getChildren().addAll(title, idLabel, actions);
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

        card.getChildren().addAll(title, input, connectBtn);
        return card;
    }

    private VBox createRecentCard(String name, String id, String time, String type) {
        VBox card = new VBox(8);
        card.getStyleClass().add("session-card");
        card.setPrefWidth(240);

        String monitorSvg = "M21 2H3c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h7v2H8v2h8v-2h-2v-2h7c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm0 14H3V4h18v12z";
        String phoneSvg = "M17 1.01L7 1c-1.1 0-2 .9-2 2v18c0 1.1.9 2 2 2h10c1.1 0 2-.9 2-2V3c0-1.1-.9-1.99-2-1.99zM17 19H7V5h10v14z";
        
        SVGPath icon = new SVGPath();
        icon.setContent(type.equals("mobile") ? phoneSvg : monitorSvg);
        icon.setFill(Color.web("#A9B4D0"));

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        Label nameLbl = new Label(name);
        nameLbl.getStyleClass().add("text-primary");
        nameLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 15px;");
        header.getChildren().addAll(icon, nameLbl);

        Label idLbl = new Label(id);
        idLbl.getStyleClass().add("text-primary");
        idLbl.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 14px;");

        Label timeLbl = new Label(time);
        timeLbl.getStyleClass().add("text-secondary");
        timeLbl.setStyle("-fx-font-size: 12px;");

        card.getChildren().addAll(header, idLbl, timeLbl);
        return card;
    }

    private VBox createStatCard(String title, String value) {
        VBox card = new VBox(5);
        card.getStyleClass().add("session-card"); // reusing simple card style
        card.setPrefWidth(200);
        card.setAlignment(Pos.CENTER);

        Label titleLbl = new Label(title);
        titleLbl.getStyleClass().add("text-secondary");

        Label valLbl = new Label(value);
        valLbl.getStyleClass().add("text-primary");
        valLbl.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");

        card.getChildren().addAll(titleLbl, valLbl);
        return card;
    }

    public void atualizarMeuId(String id) {

        meuIdLabel.setText(id);
    }

    public void atualizarStatus(String status) {

        statusLabel.setText(status);
    }

    public static void main(String[] args) {
        launch();
    }
}