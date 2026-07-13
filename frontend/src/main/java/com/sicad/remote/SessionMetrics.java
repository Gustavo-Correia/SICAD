package com.sicad.remote;

public class SessionMetrics {
    private int hostCaptureTimeMs;
    private int hostEncodeTimeMs;
    private int hostSendTimeMs;
    private int hostFps;
    private int hostDirtyPercent;
    private float hostQuality;

    private long rttMs;
    private int clientFps;

    public synchronized void updateHostMetrics(int capture, int encode, int send, int fps, int dirty, float quality) {
        this.hostCaptureTimeMs = capture;
        this.hostEncodeTimeMs = encode;
        this.hostSendTimeMs = send;
        this.hostFps = fps;
        this.hostDirtyPercent = dirty;
        this.hostQuality = quality;
    }

    public synchronized void updateRtt(long rtt) {
        this.rttMs = rtt;
    }

    public synchronized void updateClientFps(int fps) {
        this.clientFps = fps;
    }

    public synchronized String getFormattedTooltipText() {
        return String.format(
            "--- MÉTRICAS DA SESSÃO ---\n" +
            "Latência (RTT): %d ms\n" +
            "FPS Remoto (Host): %d\n" +
            "FPS Local (Client): %d\n" +
            "Captura Host: %d ms\n" +
            "Codificação Host: %d ms\n" +
            "Envio Host: %d ms\n" +
            "Qualidade JPEG: %.0f%%\n" +
            "Área Suja (Dirty): %d%%",
            rttMs,
            hostFps,
            clientFps,
            hostCaptureTimeMs,
            hostEncodeTimeMs,
            hostSendTimeMs,
            hostQuality * 100,
            hostDirtyPercent
        );
    }
}
