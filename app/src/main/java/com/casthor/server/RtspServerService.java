package com.casthor.server;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.pedro.common.ConnectChecker;
import com.pedro.encoder.utils.gl.AspectRatioMode;
import com.pedro.library.view.OpenGlView;
import com.pedro.rtspserver.RtspServerCamera2;
import com.casthor.utils.NetworkUtils;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

public class RtspServerService extends Service implements ConnectChecker {

    private static final String TAG = "CastThor";
    private static final String CHANNEL_ID = "CastThorChannel";
    private static final int NOTIFICATION_ID = 1;

    private final IBinder binder = new LocalBinder();
    private RtspServerCamera2 rtspServer;
    private OpenGlView openGlView;
    private int port = 8554;
    private boolean isRunning = false;
    private int streamWidth = 1920;
    private int streamHeight = 1080;
    private int streamFps = 30;
    private String streamCodec = "H264";
    private String authUser = null;
    private String authPass = null;
    private boolean isAudioMuted = false;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<LogListener> logListeners = new CopyOnWriteArrayList<>();

    public interface LogListener {
        void onNewLog(String message, boolean isError);
        void onStatusChanged(boolean running);
        void onStatsUpdate(int clients, long bitrate);
    }

    public class LocalBinder extends Binder {
        public RtspServerService getService() { return RtspServerService.this; }
    }

    public void setStreamOptions(int width, int height, int fps, String codec) {
        this.streamWidth = width;
        this.streamHeight = height;
        this.streamFps = fps;
        this.streamCodec = codec;
    }

    public void setPort(int port) {
        this.port = port;
        if (rtspServer != null && !isRunning) {
            rtspServer = null; // Re-crear con nuevo puerto en el siguiente start
        }
    }

    public void setAuth(String user, String pass) {
        this.authUser = (user == null || user.isEmpty()) ? null : user;
        this.authPass = (pass == null || pass.isEmpty()) ? null : pass;
        if (rtspServer != null) {
            rtspServer.getStreamClient().setAuthorization(authUser, authPass);
        }
    }

    @Override
    public void onCreate() { 
        super.onCreate(); 
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification("CastThor Service Active"));
    }

    @Override public IBinder onBind(Intent intent) { return binder; }

    private long currentBitrate = 0;

    public void setView(OpenGlView view) {
        this.openGlView = view;
        if (view == null) return;
        
        try {
            if (rtspServer != null && (rtspServer.isOnPreview() || rtspServer.isStreaming())) {
                // Solo intentamos el reemplazo si el motor gráfico está inicializado
                if (rtspServer.getGlInterface() != null) {
                    rtspServer.replaceView(view);
                    updateGlRotation();
                }
            }
        } catch (Exception e) {
            // Logueamos el error técnico pero de forma más discreta si es un problema de timing
            addLog("Surface Sync: " + e.getMessage(), false);
        }
    }

    private void updateGlRotation() {
        if (rtspServer != null && rtspServer.getGlInterface() != null) {
            int orientation = getResources().getConfiguration().orientation;
            boolean isLandscape = (orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE);

            // 0 para Portrait (Vertical), 270 para Landscape (Horizontal)
            int cameraRotation = isLandscape ? 270 : 0;
            rtspServer.getGlInterface().setRotation(cameraRotation);

            if (openGlView != null) {
                // CORRECCIÓN DEFINITIVA DE PROPORCIÓN:
                if (isLandscape) {
                    rtspServer.getGlInterface().setEncoderSize(streamWidth, streamHeight);
                } else {
                    rtspServer.getGlInterface().setEncoderSize(streamHeight, streamWidth);
                }
                
                // Forzamos un re-cálculo del motor OpenGL
                openGlView.setAspectRatioMode(AspectRatioMode.Fill);
            }
        }
    }

    public void startServer() {
        if (isRunning) return;
        try {
            if (rtspServer == null) rtspServer = new RtspServerCamera2(openGlView, this, port);
            rtspServer.getStreamClient().setAuthorization(authUser, authPass);
            
            if (openGlView != null) {
                openGlView.setAspectRatioMode(AspectRatioMode.Fill);
            }
            
            updateGlRotation();

            // --- PERFIL DE SEGURIDAD PROFESIONAL ---
            // 1. Bitrate adaptativo según resolución
            int bitrate = 1000 * 1024;
            if (streamWidth >= 2560) bitrate = 5000 * 1024; // 2K Evidence
            else if (streamWidth >= 1920) bitrate = 3500 * 1024; // 1080p High
            else if (streamWidth >= 1280) bitrate = 2000 * 1024; // 720p 
            else if (streamWidth >= 854) bitrate = 1000 * 1024; // 480p Sub-stream

            if (rtspServer.isOnPreview()) {
                rtspServer.stopPreview();
            }
            
            if (streamCodec.equals("H265")) {
                rtspServer.setVideoCodec(com.pedro.common.VideoCodec.H265);
            } else {
                rtspServer.setVideoCodec(com.pedro.common.VideoCodec.H264);
            }

            // 2. OPTIMIZACIÓN GOP (I-Frame = FPS):
            int gopInterval = 1; 
            
            // 3. REINICIO DE CÁMARA PARA FORZAR FPS:
            // Detenemos cualquier captura previa para obligar al sensor a cambiar su tasa de refresco
            if (rtspServer.isOnPreview()) {
                rtspServer.stopPreview();
            }

            // 4. PREPARACIÓN DE VIDEO (Evidence Stream)
            // Forzamos al encoder y al sensor a usar 'streamFps'
            boolean isVideoPrepared = rtspServer.prepareVideo(streamWidth, streamHeight, streamFps, bitrate, gopInterval, 0);

            if (isVideoPrepared) {
                rtspServer.startPreview(); // Reinicia con el nuevo FPS
                rtspServer.prepareAudio(128 * 1024, 44100, true, true, true);
                rtspServer.startStream("stream");
                
                isRunning = true;
                notifyStatus(true);
                
                mainHandler.postDelayed(this::updateGlRotation, 200);
                
                String ip = NetworkUtils.getIPAddress();
                addLog("MAIN: rtsp://" + ip + ":" + port + "/stream", false);
                addLog(String.format(java.util.Locale.US, "PROFILE: %dp @ %d FPS | GOP: %ds", streamHeight, streamFps, gopInterval), false);
                addLog(String.format(java.util.Locale.US, "Codec: %s | Audio: 44.1kHz", streamCodec), false);
            } else {
                addLog("Stream Error: Resolución de video no soportada", true);
            }
        } catch (Exception e) { addLog("Error: " + e.getMessage(), true); }
    }

    public void stopServer() {
        if (rtspServer != null && rtspServer.isStreaming()) {
            rtspServer.stopStream();
        }
        isRunning = false;
        notifyStatus(false);
        addLog("Stream Stopped", false);
        
        // RE-ESTABILIZACIÓN POST-PARADA:
        // Aseguramos que al volver al modo preview, la imagen se mantenga centrada y derecha.
        mainHandler.postDelayed(this::updateGlRotation, 200);
    }

    public void setAudioMuted(boolean mute) {
        this.isAudioMuted = mute;
        if (rtspServer != null) {
            if (mute) rtspServer.disableAudio();
            else rtspServer.enableAudio();
            addLog(mute ? "Microphone MUTED" : "Microphone ON", false);
        }
    }

    public boolean isAudioMuted() { return isAudioMuted; }

    public void startPreview() {
        try {
            if (rtspServer == null) rtspServer = new RtspServerCamera2(openGlView, this, port);
            rtspServer.getStreamClient().setAuthorization(authUser, authPass);
            
            if (openGlView != null) {
                openGlView.setAspectRatioMode(AspectRatioMode.Fill);
            }
            
            updateGlRotation();

            if (!rtspServer.isOnPreview()) {
                // Configuración de vigilancia base: 1920x1080, 30 FPS (para mejor estabilidad de hardware), y keyframes cada 2 segundos
                if (rtspServer.prepareVideo(1920, 1080, 30, 2500 * 1024, 2, 0)) {
                    rtspServer.startPreview();
                    addLog("Preview Active @ 30fps", false);
                } else {
                    addLog("Preview Error: Resolución no soportada", true);
                }
            }
        } catch (Exception e) { addLog("Preview Error: " + e.getMessage(), true); }
    }

    @Override public void onConnectionStarted(String url) {}
    @Override public void onConnectionSuccess() { 
        addLog("VLC CONNECTED ✅", false); 
        updateStats();
    }
    @Override public void onConnectionFailed(String reason) { addLog("Failed: " + reason, true); }
    @Override public void onDisconnect() { 
        addLog("VLC Disconnected", false); 
        updateStats();
    }
    
    private void updateStats() {
        int clients = (rtspServer != null && rtspServer.getStreamClient() != null) ? rtspServer.getStreamClient().getNumClients() : 0;
        for (LogListener l : logListeners) l.onStatsUpdate(clients, currentBitrate);
    }

    @Override public void onNewBitrate(long bitrate) {
        currentBitrate = bitrate;
        updateStats();
    }
    @Override public void onAuthError() {}
    @Override public void onAuthSuccess() {}

    private void addLog(String m, boolean e) {
        String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        String msg = "[" + time + "] " + m;
        mainHandler.post(() -> { for (LogListener l : logListeners) l.onNewLog(msg, e); });
    }

    public void addLogListener(LogListener l) { logListeners.add(l); }
    public void removeLogListener(LogListener l) { logListeners.remove(l); }
    private void notifyStatus(boolean r) { mainHandler.post(() -> { for (LogListener l : logListeners) l.onStatusChanged(r); }); }

    @Override
    public void onDestroy() {
        stopServer();
        if (rtspServer != null) {
            if (rtspServer.isOnPreview()) rtspServer.stopPreview();
            rtspServer = null;
        }
        mainHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "CasThor RTSP", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private Notification createNotification(String text) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("CasThor RTSP")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .build();
    }

    public boolean isRunning() { return isRunning; }
}