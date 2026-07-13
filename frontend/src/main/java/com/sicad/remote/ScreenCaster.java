package com.sicad.remote;

import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/**
 * Transmissor de tela otimizado com dirty rectangles, FPS dinamico,
 * bitrate adaptativo e pipeline de captura/envio desacoplado.
 */
public class ScreenCaster implements Runnable {
    private final DataOutputStream saida;
    private final Robot robo;
    private volatile boolean emExecucao = true;

    // Configuracoes do usuario
    private float qualidadeMaxima = 0.85f;
    private int fpsMaximo = 30;
    private double escalaTransmissao = 0.85;

    // Buffers reutilizaveis (evita GC pressure)
    private BufferedImage quadroRedimensionado;
    private int[] pixelsAtuais;
    private int[] pixelsAnteriores;
    private final ByteArrayOutputStream bufferJpeg = new ByteArrayOutputStream(256 * 1024);

    // Dirty rectangles
    private static final int TAMANHO_BLOCO = 64;
    private int blocosLargura;
    private int blocosAltura;
    private boolean[] blocosSujos;

    // FPS dinamico
    private static final int FPS_PARADO = 2;
    private static final int FPS_LEVE = 15;
    private static final int FPS_MEDIO = 25;

    // Bitrate adaptativo
    private float qualidadeAtual;
    private static final float QUALIDADE_MINIMA = 0.40f;

    // Dimensoes informadas ao cliente
    private int larguraTelaInformada;
    private int alturaTelaInformada;

    // Metricas
    private long tempoCaptura;
    private long tempoCodificacao;
    private long tempoEnvio;
    private int fpsReal;
    private long bytesEnviados;
    private int percentualSujo;

    // Pipeline: fila de 1 slot para desacoplar captura de envio
    private final Object monitorQuadro = new Object();
    private byte[] quadroPendente;
    private int tipoPendente; // 0 = frame completo, -4 = dirty rect
    private int dirtyX, dirtyY, dirtyW, dirtyH;

    public ScreenCaster(DataOutputStream saida, Robot robo) {
        this.saida = saida;
        this.robo = robo;
        this.qualidadeAtual = 0.75f;

        try {
            Properties cfg = com.sicad.GerenciadorConfiguracoes.carregarConfiguracoes();
            this.fpsMaximo = Math.max(1, Math.min(60,
                    Integer.parseInt(cfg.getProperty("caster.fps", "30"))));
            float q = Float.parseFloat(cfg.getProperty("caster.quality", "0.85"));
            this.qualidadeMaxima = Math.max(0.1f, Math.min(0.95f, q));
            this.qualidadeAtual = Math.min(qualidadeMaxima, 0.75f);
            double esc = Double.parseDouble(cfg.getProperty("caster.scale", "0.85"));
            this.escalaTransmissao = Math.max(0.35, Math.min(1.0, esc));
        } catch (Exception e) {
            System.out.println("Erro ao carregar configuracoes no transmissor: " + e.getMessage());
        }
    }

    public void pararTransmissao() {
        this.emExecucao = false;
        synchronized (monitorQuadro) {
            monitorQuadro.notifyAll();
        }
    }

    @Override
    public void run() {
        // Iniciar thread de envio separada
        Thread threadEnvio = new Thread(this::loopEnvio, "screen-caster-envio");
        threadEnvio.setDaemon(true);
        threadEnvio.start();

        Rectangle areaTela = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        ImageWriter escritor = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam parametros = escritor.getDefaultWriteParam();
        parametros.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);

        int frameCount = 0;
        long ultimoLogMs = System.currentTimeMillis();

        try {
            while (emExecucao) {
                long inicioCaptura = System.nanoTime();

                // 1. Capturar tela
                BufferedImage captura = robo.createScreenCapture(areaTela);

                // 2. Informar dimensoes reais ao cliente (uma vez ou quando mudar)
                enviarDimensoesSeAlteradas(captura.getWidth(), captura.getHeight());

                // 3. Redimensionar reutilizando buffer
                BufferedImage quadro = redimensionarReutilizando(captura);
                tempoCaptura = (System.nanoTime() - inicioCaptura) / 1_000_000L;

                // 4. Extrair pixels para comparacao direta
                int[] pixelsNovos = extrairPixels(quadro);
                int largura = quadro.getWidth();
                int altura = quadro.getHeight();

                // 5. Inicializar grid de blocos se necessario
                inicializarBlocos(largura, altura);

                // 6. Detectar blocos sujos (dirty rectangles)
                int blocosSujosCount = detectarBlocosSujos(pixelsNovos, largura, altura);
                int totalBlocos = blocosLargura * blocosAltura;
                percentualSujo = totalBlocos > 0 ? (blocosSujosCount * 100) / totalBlocos : 0;

                // 7. FPS dinamico baseado em atividade
                int fpsAlvo = calcularFpsDinamico(percentualSujo);
                int intervaloMs = 1000 / fpsAlvo;

                if (blocosSujosCount == 0) {
                    // Tela parada — esperar mais
                    aguardarMs(inicioCaptura, 1000 / FPS_PARADO);
                    continue;
                }

                // 8. Codificar e enviar
                long inicioCodificacao = System.nanoTime();

                if (percentualSujo > 55 || pixelsAnteriores == null) {
                    // Frame completo (muita coisa mudou ou primeiro frame)
                    byte[] jpeg = codificarJpeg(escritor, parametros, quadro, qualidadeAtual);
                    tempoCodificacao = (System.nanoTime() - inicioCodificacao) / 1_000_000L;
                    enfileirarFrameCompleto(jpeg);
                } else {
                    // Dirty rectangle: bounding box dos blocos sujos
                    Rectangle boundingBox = calcularBoundingBox(largura, altura);
                    if (boundingBox != null && boundingBox.width > 0 && boundingBox.height > 0) {
                        BufferedImage regiao = quadro.getSubimage(
                                boundingBox.x, boundingBox.y, boundingBox.width, boundingBox.height);
                        byte[] jpeg = codificarJpeg(escritor, parametros, regiao, qualidadeAtual);
                        tempoCodificacao = (System.nanoTime() - inicioCodificacao) / 1_000_000L;
                        enfileirarDirtyRect(jpeg, boundingBox);
                    }
                }

                // 9. Guardar pixels para proxima comparacao
                if (pixelsAnteriores == null || pixelsAnteriores.length != pixelsNovos.length) {
                    pixelsAnteriores = new int[pixelsNovos.length];
                }
                System.arraycopy(pixelsNovos, 0, pixelsAnteriores, 0, pixelsNovos.length);

                // 10. Bitrate adaptativo
                ajustarQualidade();

                // 11. Metricas
                frameCount++;
                long agora = System.currentTimeMillis();
                if (agora - ultimoLogMs >= 2000) {
                    fpsReal = (int) (frameCount * 1000L / (agora - ultimoLogMs));
                    System.out.printf("[ScreenCaster] FPS=%d captura=%dms cod=%dms envio=%dms " +
                                    "qualidade=%.0f%% sujo=%d%% bitrate=%.0fKB/s%n",
                            fpsReal, tempoCaptura, tempoCodificacao, tempoEnvio,
                            qualidadeAtual * 100, percentualSujo,
                            bytesEnviados / 1024.0 / ((agora - ultimoLogMs) / 1000.0));
                    
                    enviarMetricas(saida, (int) tempoCaptura, (int) tempoCodificacao, (int) tempoEnvio, fpsReal, percentualSujo, qualidadeAtual);
                    
                    frameCount = 0;
                    bytesEnviados = 0;
                    ultimoLogMs = agora;
                }

                // 12. Controle de frame rate
                aguardarMs(inicioCaptura, intervaloMs);
            }
        } catch (Exception e) {
            if (emExecucao) {
                System.out.println("Transmissao de tela encerrada: " + e.getMessage());
            }
        } finally {
            escritor.dispose();
            pararTransmissao();
        }
    }

    /** Thread separada que envia os quadros codificados sem bloquear a captura. */
    private void loopEnvio() {
        try {
            while (emExecucao) {
                byte[] dados;
                int tipo;
                int rx, ry, rw, rh;

                synchronized (monitorQuadro) {
                    while (emExecucao && quadroPendente == null) {
                        monitorQuadro.wait();
                    }
                    if (!emExecucao) break;
                    dados = quadroPendente;
                    tipo = tipoPendente;
                    rx = dirtyX;
                    ry = dirtyY;
                    rw = dirtyW;
                    rh = dirtyH;
                    quadroPendente = null;
                }

                long inicioEnvio = System.nanoTime();
                synchronized (saida) {
                    if (tipo == -4) {
                        // Dirty rectangle: header especial
                        saida.writeInt(-4);
                        saida.writeInt(rx);
                        saida.writeInt(ry);
                        saida.writeInt(rw);
                        saida.writeInt(rh);
                        saida.writeInt(dados.length);
                        saida.write(dados);
                    } else {
                        // Frame completo
                        saida.writeInt(dados.length);
                        saida.write(dados);
                    }
                    saida.flush();
                }
                tempoEnvio = (System.nanoTime() - inicioEnvio) / 1_000_000L;
                bytesEnviados += dados.length;

            }
        } catch (Exception e) {
            if (emExecucao) {
                System.out.println("Thread de envio encerrada: " + e.getMessage());
                pararTransmissao();
            }
        }
    }

    private void enfileirarFrameCompleto(byte[] jpeg) {
        synchronized (monitorQuadro) {
            quadroPendente = jpeg;
            tipoPendente = 0;
            monitorQuadro.notify();
        }
    }

    private void enfileirarDirtyRect(byte[] jpeg, Rectangle rect) {
        synchronized (monitorQuadro) {
            quadroPendente = jpeg;
            tipoPendente = -4;
            dirtyX = rect.x;
            dirtyY = rect.y;
            dirtyW = rect.width;
            dirtyH = rect.height;
            monitorQuadro.notify();
        }
    }

    // ==================== Dirty Rectangles ====================

    private void inicializarBlocos(int largura, int altura) {
        int bw = (largura + TAMANHO_BLOCO - 1) / TAMANHO_BLOCO;
        int bh = (altura + TAMANHO_BLOCO - 1) / TAMANHO_BLOCO;
        if (bw != blocosLargura || bh != blocosAltura) {
            blocosLargura = bw;
            blocosAltura = bh;
            blocosSujos = new boolean[bw * bh];
        }
    }

    /** Compara blocos de 64x64 via acesso direto ao array de pixels. */
    private int detectarBlocosSujos(int[] pixelsNovos, int largura, int altura) {
        if (pixelsAnteriores == null || pixelsAnteriores.length != pixelsNovos.length) {
            // Primeiro frame ou mudanca de resolucao: tudo sujo
            if (blocosSujos != null) {
                java.util.Arrays.fill(blocosSujos, true);
            }
            return blocosLargura * blocosAltura;
        }

        int count = 0;
        for (int by = 0; by < blocosAltura; by++) {
            for (int bx = 0; bx < blocosLargura; bx++) {
                int idx = by * blocosLargura + bx;
                blocosSujos[idx] = blocoMudou(pixelsNovos, bx, by, largura, altura);
                if (blocosSujos[idx]) count++;
            }
        }
        return count;
    }

    /** Verifica se um bloco de 64x64 mudou, amostrando a cada 4 pixels. */
    private boolean blocoMudou(int[] pixelsNovos, int bx, int by, int largura, int altura) {
        int startX = bx * TAMANHO_BLOCO;
        int startY = by * TAMANHO_BLOCO;
        int endX = Math.min(startX + TAMANHO_BLOCO, largura);
        int endY = Math.min(startY + TAMANHO_BLOCO, altura);

        // Amostragem: checar 1 a cada 4 pixels do bloco
        for (int y = startY; y < endY; y += 4) {
            int rowOffset = y * largura;
            for (int x = startX; x < endX; x += 4) {
                int pos = rowOffset + x;
                if (pixelsNovos[pos] != pixelsAnteriores[pos]) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Calcula o bounding box minimo que contém todos os blocos sujos. */
    private Rectangle calcularBoundingBox(int larguraImg, int alturaImg) {
        int minBx = blocosLargura, minBy = blocosAltura;
        int maxBx = -1, maxBy = -1;

        for (int by = 0; by < blocosAltura; by++) {
            for (int bx = 0; bx < blocosLargura; bx++) {
                if (blocosSujos[by * blocosLargura + bx]) {
                    minBx = Math.min(minBx, bx);
                    minBy = Math.min(minBy, by);
                    maxBx = Math.max(maxBx, bx);
                    maxBy = Math.max(maxBy, by);
                }
            }
        }

        if (maxBx < 0) return null;

        int x = minBx * TAMANHO_BLOCO;
        int y = minBy * TAMANHO_BLOCO;
        int w = Math.min((maxBx + 1) * TAMANHO_BLOCO, larguraImg) - x;
        int h = Math.min((maxBy + 1) * TAMANHO_BLOCO, alturaImg) - y;
        return new Rectangle(x, y, w, h);
    }

    // ==================== FPS Dinamico ====================

    private int calcularFpsDinamico(int percentualSujo) {
        if (percentualSujo == 0) return FPS_PARADO;
        if (percentualSujo < 10) return FPS_LEVE;
        if (percentualSujo < 40) return FPS_MEDIO;
        return fpsMaximo;
    }

    // ==================== Bitrate Adaptativo ====================

    private void ajustarQualidade() {
        if (tempoEnvio > 100) {
            // Rede lenta: reduzir qualidade
            qualidadeAtual = Math.max(QUALIDADE_MINIMA, qualidadeAtual - 0.05f);
        } else if (tempoEnvio < 30 && qualidadeAtual < qualidadeMaxima) {
            // Rede rapida: aumentar qualidade gradualmente
            qualidadeAtual = Math.min(qualidadeMaxima, qualidadeAtual + 0.02f);
        }
    }

    // ==================== Utilitarios ====================

    private int[] extrairPixels(BufferedImage img) {
        if (img.getType() == BufferedImage.TYPE_INT_RGB || img.getType() == BufferedImage.TYPE_INT_ARGB) {
            return ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
        }
        // Fallback: converter para TYPE_INT_RGB
        BufferedImage convertida = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = convertida.createGraphics();
        g.drawImage(img, 0, 0, null);
        g.dispose();
        return ((DataBufferInt) convertida.getRaster().getDataBuffer()).getData();
    }

    private BufferedImage redimensionarReutilizando(BufferedImage original) {
        int w = (int) (original.getWidth() * escalaTransmissao);
        int h = (int) (original.getHeight() * escalaTransmissao);

        if (quadroRedimensionado == null || quadroRedimensionado.getWidth() != w
                || quadroRedimensionado.getHeight() != h) {
            quadroRedimensionado = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        }

        java.awt.Graphics2D g = quadroRedimensionado.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(original, 0, 0, w, h, null);
        g.dispose();
        return quadroRedimensionado;
    }

    private byte[] codificarJpeg(ImageWriter escritor, ImageWriteParam parametros,
            BufferedImage quadro, float qualidade) throws Exception {
        parametros.setCompressionQuality(qualidade);
        bufferJpeg.reset();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(bufferJpeg)) {
            escritor.setOutput(ios);
            escritor.write(null, new IIOImage(quadro, null, null), parametros);
        }
        return bufferJpeg.toByteArray();
    }

    private void enviarDimensoesSeAlteradas(int largura, int altura) throws Exception {
        if (largura == larguraTelaInformada && altura == alturaTelaInformada) {
            return;
        }
        synchronized (saida) {
            saida.writeInt(-3);
            saida.writeInt(largura);
            saida.writeInt(altura);
            saida.flush();
        }
        larguraTelaInformada = largura;
        alturaTelaInformada = altura;
    }

    private void aguardarMs(long inicioNanos, int intervaloMs) throws InterruptedException {
        long duracaoMs = (System.nanoTime() - inicioNanos) / 1_000_000L;
        long esperaMs = intervaloMs - duracaoMs;
        if (esperaMs > 1) {
            Thread.sleep(esperaMs);
        }
    }

    // ==================== Metodos estaticos para Ping/Clipboard ====================

    public static void enviarMetricas(DataOutputStream saida, int tempoCaptura, int tempoCodificacao, int tempoEnvio, int fpsReal, int percentualSujo, float qualidade) {
        if (saida == null) return;
        try {
            synchronized (saida) {
                saida.writeInt(-5);
                saida.writeInt(tempoCaptura);
                saida.writeInt(tempoCodificacao);
                saida.writeInt(tempoEnvio);
                saida.writeInt(fpsReal);
                saida.writeInt(percentualSujo);
                saida.writeFloat(qualidade);
                saida.flush();
            }
        } catch (Exception e) {
            System.out.println("Erro ao enviar metricas: " + e.getMessage());
        }
    }

    public static void enviarPong(DataOutputStream saida, long instanteOriginal) {
        if (saida == null) return;
        try {
            synchronized (saida) {
                saida.writeInt(-1);
                saida.writeLong(instanteOriginal);
                saida.flush();
            }
        } catch (Exception e) {
            System.out.println("Erro ao enviar resposta de latencia: " + e.getMessage());
        }
    }

    public static void enviarClipboard(DataOutputStream saida, String texto) {
        if (saida == null) return;
        try {
            synchronized (saida) {
                byte[] dadosTexto = texto.getBytes(StandardCharsets.UTF_8);
                saida.writeInt(-2);
                saida.writeInt(dadosTexto.length);
                saida.write(dadosTexto);
                saida.flush();
            }
        } catch (Exception e) {
            System.out.println("Erro ao enviar area de transferencia: " + e.getMessage());
        }
    }

    // ==================== Acessores de metricas ====================

    public int getFpsReal() { return fpsReal; }
    public long getTempoCaptura() { return tempoCaptura; }
    public long getTempoCodificacao() { return tempoCodificacao; }
    public long getTempoEnvio() { return tempoEnvio; }
    public float getQualidadeAtual() { return qualidadeAtual; }
    public int getPercentualSujo() { return percentualSujo; }
}
