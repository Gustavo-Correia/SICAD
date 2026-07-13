package com.sicad.remote;

import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

public class TransmissorTela implements Runnable {
    private static final int TAMANHO_TILE = 64;

    private final DataOutputStream saida;
    private final Robot robo;
    private final Object monitorQuadro = new Object();
    private volatile boolean emExecucao = true;
    private BufferedImage quadroPendente;
    private BufferedImage ultimoQuadroEnviado;
    private float qualidadeCompressao = 0.85f;
    private int quadrosPorSegundo = 15;
    private int intervaloCapturaMs = 1000 / quadrosPorSegundo;
    private double escalaTransmissao = 0.85;
    private int larguraTelaInformada;
    private int alturaTelaInformada;

    public TransmissorTela(DataOutputStream saida, Robot robo) {
        this.saida = saida;
        this.robo = robo;

        try {
            Properties configuracoes = com.sicad.GerenciadorConfiguracoes.carregarConfiguracoes();
            this.quadrosPorSegundo = Math.max(1, Math.min(60,
                    Integer.parseInt(configuracoes.getProperty("caster.fps", "15"))));
            this.intervaloCapturaMs = 1000 / this.quadrosPorSegundo;
            float qualidadeConfigurada = Float.parseFloat(configuracoes.getProperty("caster.quality", "0.85"));
            this.qualidadeCompressao = Math.max(0.1f, Math.min(0.95f, qualidadeConfigurada));
            double escalaConfigurada = Double.parseDouble(configuracoes.getProperty("caster.scale", "0.85"));
            this.escalaTransmissao = Math.max(0.35, Math.min(1.0, escalaConfigurada));
            System.out.println("Video configurado: " + quadrosPorSegundo + " FPS, qualidade "
                    + Math.round(qualidadeCompressao * 100) + "%, resolucao "
                    + Math.round(escalaTransmissao * 100) + "%, tiles " + TAMANHO_TILE + "x" + TAMANHO_TILE);
        } catch (Exception e) {
            System.out.println("Erro ao carregar configuracoes no transmissor de tela: " + e.getMessage());
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
        Rectangle areaTela = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        Thread tarefaTransmissao = new Thread(this::transmitirQuadros, "transmissao-quadros");
        tarefaTransmissao.start();

        try {
            while (emExecucao) {
                long inicioCaptura = System.nanoTime();
                BufferedImage captura = robo.createScreenCapture(areaTela);

                synchronized (monitorQuadro) {
                    quadroPendente = captura;
                    monitorQuadro.notify();
                }

                long duracaoMs = (System.nanoTime() - inicioCaptura) / 1_000_000L;
                long esperaMs = intervaloCapturaMs - duracaoMs;
                if (esperaMs > 0) {
                    Thread.sleep(esperaMs);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            if (emExecucao) {
                System.out.println("Captura de tela encerrada: " + e.getMessage());
            }
        } finally {
            pararTransmissao();
            try {
                tarefaTransmissao.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void transmitirQuadros() {
        ImageWriter escritor = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam parametros = escritor.getDefaultWriteParam();
        parametros.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);

        try {
            while (emExecucao) {
                BufferedImage captura = aguardarQuadroMaisRecente();
                if (captura == null) {
                    continue;
                }

                BufferedImage quadroRedimensionado = redimensionarImagem(captura, escalaTransmissao);
                int largura = quadroRedimensionado.getWidth();
                int altura = quadroRedimensionado.getHeight();

                enviarDimensoesSeAlteradas(largura, altura);

                int cols = (int) Math.ceil((double) largura / TAMANHO_TILE);
                int rows = (int) Math.ceil((double) altura / TAMANHO_TILE);

                if (ultimoQuadroEnviado == null
                        || ultimoQuadroEnviado.getWidth() != largura
                        || ultimoQuadroEnviado.getHeight() != altura) {
                    enviarQuadroCompleto(escritor, parametros, quadroRedimensionado, cols, rows);
                    ultimoQuadroEnviado = quadroRedimensionado;
                    continue;
                }

                int[] pixelsAtual = quadroRedimensionado.getRGB(0, 0, largura, altura, null, 0, largura);
                int[] pixelsAnterior = ultimoQuadroEnviado.getRGB(0, 0, largura, altura, null, 0, largura);

                List<TileData> tilesAlterados = new ArrayList<>();

                for (int row = 0; row < rows; row++) {
                    for (int col = 0; col < cols; col++) {
                        int tileX = col * TAMANHO_TILE;
                        int tileY = row * TAMANHO_TILE;
                        int tileW = Math.min(TAMANHO_TILE, largura - tileX);
                        int tileH = Math.min(TAMANHO_TILE, altura - tileY);

                        if (!tileAlterado(pixelsAtual, pixelsAnterior, largura, tileX, tileY, tileW, tileH)) {
                            continue;
                        }

                        BufferedImage tileImg = quadroRedimensionado.getSubimage(tileX, tileY, tileW, tileH);
                        byte[] jpeg = codificarJpegTile(escritor, parametros, tileImg);
                        tilesAlterados.add(new TileData(col, row, tileW, tileH, jpeg));
                    }
                }

                if (!tilesAlterados.isEmpty()) {
                    enviarTiles(tilesAlterados);
                }

                ultimoQuadroEnviado = quadroRedimensionado;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            if (emExecucao) {
                System.out.println("Transmissao de tela encerrada: " + e.getMessage());
            }
        } finally {
            escritor.dispose();
            pararTransmissao();
        }
    }

    private void enviarQuadroCompleto(ImageWriter escritor, ImageWriteParam parametros,
            BufferedImage quadro, int cols, int rows) throws Exception {
        List<TileData> todosTiles = new ArrayList<>();
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int tileX = col * TAMANHO_TILE;
                int tileY = row * TAMANHO_TILE;
                int tileW = Math.min(TAMANHO_TILE, quadro.getWidth() - tileX);
                int tileH = Math.min(TAMANHO_TILE, quadro.getHeight() - tileY);
                BufferedImage tileImg = quadro.getSubimage(tileX, tileY, tileW, tileH);
                byte[] jpeg = codificarJpegTile(escritor, parametros, tileImg);
                todosTiles.add(new TileData(col, row, tileW, tileH, jpeg));
            }
        }
        enviarTiles(todosTiles);
    }

    private void enviarTiles(List<TileData> tiles) throws Exception {
        synchronized (saida) {
            saida.writeInt(-4);
            saida.writeInt(tiles.size());
            for (TileData tile : tiles) {
                saida.writeInt(tile.col);
                saida.writeInt(tile.row);
                saida.writeInt(tile.w);
                saida.writeInt(tile.h);
                saida.writeInt(tile.jpeg.length);
                saida.write(tile.jpeg);
            }
            saida.flush();
        }
    }

    private boolean tileAlterado(int[] pixelsAtual, int[] pixelsAnterior, int largura,
            int tileX, int tileY, int tileW, int tileH) {
        for (int y = 0; y < tileH; y++) {
            int idxBase = (tileY + y) * largura + tileX;
            for (int x = 0; x < tileW; x++) {
                if (pixelsAtual[idxBase + x] != pixelsAnterior[idxBase + x]) {
                    return true;
                }
            }
        }
        return false;
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

    private byte[] codificarJpegTile(ImageWriter escritor, ImageWriteParam parametros,
            BufferedImage tile) throws Exception {
        parametros.setCompressionQuality(qualidadeCompressao);
        ByteArrayOutputStream fluxoDados = new ByteArrayOutputStream(16 * 1024);
        try (ImageOutputStream fluxoImagem = ImageIO.createImageOutputStream(fluxoDados)) {
            escritor.setOutput(fluxoImagem);
            escritor.write(null, new IIOImage(tile, null, null), parametros);
        }
        return fluxoDados.toByteArray();
    }

    public static void enviarPong(DataOutputStream saida, long instanteOriginal) {
        if (saida == null) {
            return;
        }
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
        if (saida == null) {
            return;
        }
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

    private BufferedImage aguardarQuadroMaisRecente() throws InterruptedException {
        synchronized (monitorQuadro) {
            while (emExecucao && quadroPendente == null) {
                monitorQuadro.wait();
            }
            BufferedImage quadro = quadroPendente;
            quadroPendente = null;
            return quadro;
        }
    }

    private BufferedImage redimensionarImagem(BufferedImage original, double escala) {
        if (escala >= 1.0) {
            BufferedImage copia = new BufferedImage(original.getWidth(), original.getHeight(), BufferedImage.TYPE_INT_RGB);
            copia.getGraphics().drawImage(original, 0, 0, null);
            return copia;
        }
        int largura = (int) (original.getWidth() * escala);
        int altura = (int) (original.getHeight() * escala);
        BufferedImage redimensionada = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D graficos = redimensionada.createGraphics();
        graficos.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graficos.drawImage(original, 0, 0, largura, altura, null);
        graficos.dispose();
        return redimensionada;
    }

    private static class TileData {
        final int col;
        final int row;
        final int w;
        final int h;
        final byte[] jpeg;

        TileData(int col, int row, int w, int h, byte[] jpeg) {
            this.col = col;
            this.row = row;
            this.w = w;
            this.h = h;
            this.jpeg = jpeg;
        }
    }
}
