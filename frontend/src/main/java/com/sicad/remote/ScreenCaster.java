package com.sicad.remote;

import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

public class ScreenCaster implements Runnable {
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

    /** Cria o transmissor e carrega os limites de captura e compressao configurados. */
    public ScreenCaster(DataOutputStream saida, Robot robo) {
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
                    + Math.round(escalaTransmissao * 100) + "%");
        } catch (Exception e) {
            System.out.println("Erro ao carregar configuracoes no transmissor de tela: " + e.getMessage());
        }
    }

    /** Interrompe a captura e acorda a transmissao caso ela esteja aguardando um quadro. */
    public void pararTransmissao() {
        this.emExecucao = false;
        synchronized (monitorQuadro) {
            monitorQuadro.notifyAll();
        }
    }

    /** Captura a tela na taxa configurada e conserva somente o quadro mais recente. */
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

    /** Codifica e envia o quadro mais novo disponivel, descartando capturas substituidas. */
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
                if (quadrosSaoSimilares(quadroRedimensionado, ultimoQuadroEnviado)) {
                    continue;
                }

                enviarDimensoesSeAlteradas(captura.getWidth(), captura.getHeight());
                byte[] dadosImagem = codificarJpeg(escritor, parametros, quadroRedimensionado,
                        qualidadeCompressao);
                synchronized (saida) {
                    saida.writeInt(dadosImagem.length);
                    saida.write(dadosImagem);
                    saida.flush();
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

    /** Informa ao cliente a resolucao real usada para converter coordenadas do mouse. */
    private void enviarDimensoesSeAlteradas(int largura, int altura) throws Exception {
        if (largura == larguraTelaInformada && altura == alturaTelaInformada) {
            return;
        }
        saida.writeInt(-3);
        saida.writeInt(largura);
        saida.writeInt(altura);
        larguraTelaInformada = largura;
        alturaTelaInformada = altura;
    }

    /** Codifica uma imagem em JPEG usando a qualidade solicitada pelo usuario, sem reducao progressiva. */
    private byte[] codificarJpeg(ImageWriter escritor, ImageWriteParam parametros,
            BufferedImage quadro, float qualidade) throws Exception {
        parametros.setCompressionQuality(qualidade);
        ByteArrayOutputStream fluxoDados = new ByteArrayOutputStream(256 * 1024);
        try (ImageOutputStream fluxoImagem = ImageIO.createImageOutputStream(fluxoDados)) {
            escritor.setOutput(fluxoImagem);
            escritor.write(null, new IIOImage(quadro, null, null), parametros);
        }
        return fluxoDados.toByteArray();
    }

    /** Envia a resposta do medidor de latencia pelo canal binario de controle. */
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

    /** Envia o texto da area de transferencia pelo canal binario de controle. */
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

    /** Aguarda uma captura e retira atomicamente apenas a versao mais recente. */
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

    /** Compara amostras de dois quadros com passo 4 para detectar mudancas rapidamente. */
    private boolean quadrosSaoSimilares(BufferedImage primeiro, BufferedImage segundo) {
        if (primeiro == null || segundo == null) {
            return false;
        }
        if (primeiro.getWidth() != segundo.getWidth() || primeiro.getHeight() != segundo.getHeight()) {
            return false;
        }

        int largura = primeiro.getWidth();
        int altura = primeiro.getHeight();
        int passo = 4;

        for (int y = 0; y < altura; y += passo) {
            for (int x = 0; x < largura; x += passo) {
                if (primeiro.getRGB(x, y) != segundo.getRGB(x, y)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Reduz a resolucao do quadro antes da codificacao JPEG para limitar o trafego. */
    private BufferedImage redimensionarImagem(BufferedImage original, double escala) {
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
}
