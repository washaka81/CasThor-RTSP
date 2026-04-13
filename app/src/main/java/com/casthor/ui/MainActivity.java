package com.casthor.ui;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.view.SurfaceHolder;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.AdapterView;
import android.widget.EditText;
import com.google.android.material.textfield.TextInputEditText;
import android.content.SharedPreferences;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.casthor.R;
import com.casthor.server.RtspServerService;
import com.casthor.utils.NetworkUtils;
import com.google.android.material.button.MaterialButton;
import com.pedro.library.view.OpenGlView;

public class MainActivity extends AppCompatActivity implements RtspServerService.LogListener, SurfaceHolder.Callback {

    private static final String APP_VERSION = "V1.1";
    private RtspServerService rtspService;
    private boolean isBound = false;
    private OpenGlView surfaceView;
    private View hudOverlay;
    private View appBarLayout;
    private View controlsContainer;
    private View mainContent;
    private View portraitControls;
    private TextView logsText, hudUrl, hudUrlSub;
    private ScrollView logsScrollView;
    private MaterialButton btnStream, btnStreamFs;
    private Spinner spinnerResolution, spinnerCodec, spinnerFps;
    private TextInputEditText etUser, etPass, etPort;
    private SharedPreferences prefs;
    private com.google.android.material.floatingactionbutton.FloatingActionButton btnFullscreenToggle, btnExitFs, btnMute, btnBrightness;
    private boolean isDimmed = false;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            rtspService = ((RtspServerService.LocalBinder) service).getService();
            isBound = true;
            rtspService.addLogListener(MainActivity.this);
            // Si la superficie ya está lista, la seteamos de inmediato
            if (surfaceView.getHolder().getSurface().isValid()) {
                rtspService.setView(surfaceView);
                if (hasPermissions()) rtspService.startPreview();
            }
            updateUi(rtspService.isRunning());
        }
        @Override public void onServiceDisconnected(ComponentName name) { isBound = false; }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("CastThorPrefs", MODE_PRIVATE);

        if (getSupportActionBar() != null) getSupportActionBar().setTitle("CasThor RTSP");
        
        appBarLayout = findViewById(R.id.appBarLayout);
        controlsContainer = findViewById(R.id.controlsContainer);
        mainContent = findViewById(R.id.mainContent);
        portraitControls = findViewById(R.id.portraitControls);
        surfaceView = findViewById(R.id.surfaceView);
        surfaceView.getHolder().addCallback(this);

        hudOverlay = findViewById(R.id.hudOverlay);
        logsText = findViewById(R.id.logsText);
        logsText.setText("> CasThor RTSP " + APP_VERSION + " Booting...");
        logsScrollView = findViewById(R.id.logsScrollView);
        hudUrl = findViewById(R.id.hudUrl);
        hudUrlSub = findViewById(R.id.hudUrlSub);
        btnStream = findViewById(R.id.btnStream);
        btnStreamFs = findViewById(R.id.btnStreamFs);
        btnExitFs = findViewById(R.id.btnExitFs);
        btnMute = findViewById(R.id.btnMute);
        btnBrightness = findViewById(R.id.btnBrightness);
        btnFullscreenToggle = findViewById(R.id.btnFullscreenToggle);

        btnBrightness.setOnClickListener(v -> {
            isDimmed = !isDimmed;
            android.view.WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.screenBrightness = isDimmed ? 0.05f : -1.0f; // 5% brillo o restaurar sistema
            getWindow().setAttributes(lp);
            btnBrightness.setSupportImageTintList(ContextCompat.getColorStateList(this, isDimmed ? android.R.color.holo_orange_light : android.R.color.white));
        });

        btnMute.setOnClickListener(v -> {
            if (isBound && rtspService != null) {
                boolean isMuted = rtspService.isAudioMuted();
                rtspService.setAudioMuted(!isMuted);
                updateMuteButtons(!isMuted);
            }
        });

        spinnerResolution = findViewById(R.id.spinnerResolution);
        spinnerCodec = findViewById(R.id.spinnerCodec);
        spinnerFps = findViewById(R.id.spinnerFps);
        etUser = findViewById(R.id.etUser);
        etPass = findViewById(R.id.etPass);
        etPort = findViewById(R.id.etPort);

        // Setup Spinners
        ArrayAdapter<String> resAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"1440p", "1080p", "720p", "480p"});
        spinnerResolution.setAdapter(resAdapter);
        ArrayAdapter<String> codecAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"H264", "H265"});
        spinnerCodec.setAdapter(codecAdapter);
        ArrayAdapter<String> fpsAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"15 FPS", "25 FPS", "30 FPS", "60 FPS"});
        spinnerFps.setAdapter(fpsAdapter);

        // Load saved selections
        spinnerResolution.setSelection(prefs.getInt("last_resolution", 1)); // Default 1080p
        spinnerCodec.setSelection(prefs.getInt("last_codec", 0)); // Default H264
        spinnerFps.setSelection(prefs.getInt("last_fps", 2)); // Default 30 FPS (ahora es índice 2)
        etUser.setText(prefs.getString("last_user", ""));
        etPass.setText(prefs.getString("last_pass", ""));
        etPort.setText(prefs.getString("last_port", "8554"));

        // Save selections on change
        AdapterView.OnItemSelectedListener saveListener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String key = null;
                if (parent.getId() == R.id.spinnerResolution) key = "last_resolution";
                else if (parent.getId() == R.id.spinnerCodec) key = "last_codec";
                else if (parent.getId() == R.id.spinnerFps) key = "last_fps";
                if (key != null) prefs.edit().putInt(key, position).apply();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        };
        spinnerResolution.setOnItemSelectedListener(saveListener);
        spinnerCodec.setOnItemSelectedListener(saveListener);
        spinnerFps.setOnItemSelectedListener(saveListener);

        if (savedInstanceState != null) {
            boolean wasRunning = savedInstanceState.getBoolean("isStreaming", false);
            updateUi(wasRunning);
        }

        btnStream.setOnClickListener(v -> {
            toggleStream();
        });

        btnStreamFs.setOnClickListener(v -> {
            toggleStream();
        });

        btnFullscreenToggle.setOnClickListener(v -> {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        });

        btnExitFs.setOnClickListener(v -> {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        });

        findViewById(R.id.kofiButton).setOnClickListener(v -> {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://ko-fi.com/washaka81")));
        });

        Intent intent = new Intent(this, RtspServerService.class);
        startForegroundService(intent);
        bindService(intent, serviceConnection, BIND_AUTO_CREATE);
        
        // Configuración para permitir dibujo en la zona del Notch/Cámara (Moto G85)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            android.view.WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }
        
        updateFullscreenMode(getResources().getConfiguration().orientation);
    }

    private void updateMuteButtons(boolean muted) {
        int icon = muted ? android.R.drawable.stat_notify_call_mute : android.R.drawable.ic_btn_speak_now;
        int tint = muted ? android.R.color.holo_red_light : android.R.color.white;
        
        btnMute.setImageResource(icon);
        btnMute.setSupportImageTintList(ContextCompat.getColorStateList(this, tint));
    }

    private void toggleStream() {
        if (!isBound) return;
        if (rtspService.isRunning()) {
            rtspService.stopServer();
        } else if (hasPermissions()) {
            int resSelection = spinnerResolution.getSelectedItemPosition();
            int codecSelection = spinnerCodec.getSelectedItemPosition();
            int fpsSelection = spinnerFps.getSelectedItemPosition();
            String user = etUser.getText().toString().trim();
            String pass = etPass.getText().toString();
            String portStr = etPort.getText().toString().trim();
            int port = 8554;
            try { port = Integer.parseInt(portStr); } catch (Exception e) {}
            
            // Save auth and port
            prefs.edit()
                .putString("last_user", user)
                .putString("last_pass", pass)
                .putString("last_port", String.valueOf(port))
                .apply();
            
            int width = 2560, height = 1440; // 1440p default for index 0
            if (resSelection == 1) { width = 1920; height = 1080; }
            else if (resSelection == 2) { width = 1280; height = 720; }
            else if (resSelection == 3) { width = 854; height = 480; }

            int fps = 30;
            if (fpsSelection == 0) fps = 15;
            else if (fpsSelection == 1) fps = 25;
            else if (fpsSelection == 2) fps = 30;
            else if (fpsSelection == 3) fps = 60;
            
            rtspService.setAuth(user, pass);
            rtspService.setPort(port);
            rtspService.setStreamOptions(width, height, fps, codecSelection == 1 ? "H265" : "H264");
            rtspService.startServer();
        } else {
            requestPermissions();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (isBound && rtspService != null) {
            outState.putBoolean("isStreaming", rtspService.isRunning());
        }
    }

    private void updateFullscreenMode(int orientation) {
        if (orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
            // Activar Fullscreen Extremo
            if (getSupportActionBar() != null) getSupportActionBar().hide();
            if (appBarLayout != null) appBarLayout.setVisibility(View.GONE);
            if (controlsContainer != null) controlsContainer.setVisibility(View.GONE);
            if (portraitControls != null) portraitControls.setVisibility(View.GONE);
            btnFullscreenToggle.setVisibility(View.GONE);
            btnExitFs.setVisibility(View.VISIBLE);
            hudOverlay.setVisibility(View.VISIBLE);
            btnStreamFs.setVisibility(View.VISIBLE);
            
            // Eliminar límites de ventana y padding
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
            
            // Re-aplicar modo Cutout para asegurar inmersión tras rotación
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                android.view.WindowManager.LayoutParams wlp = getWindow().getAttributes();
                wlp.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                getWindow().setAttributes(wlp);
            }
            
            if (mainContent != null) {
                mainContent.setPadding(0, 0, 0, 0);
                androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams lp = 
                    (androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams) mainContent.getLayoutParams();
                lp.height = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
                lp.width = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
                lp.setBehavior(null); // ELIMINAR COMPORTAMIENTO: Evita que el AppBar reserve espacio (franja negra)
                mainContent.setLayoutParams(lp);
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                getWindow().setDecorFitsSystemWindows(false);
                android.view.WindowInsetsController controller = getWindow().getInsetsController();
                if (controller != null) {
                    controller.hide(android.view.WindowInsets.Type.statusBars() | android.view.WindowInsets.Type.navigationBars());
                    controller.setSystemBarsBehavior(android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                }
            } else {
                getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN);
            }
                
            // Llenar pantalla TOTAL ignorando Notch y Safe Areas
            androidx.constraintlayout.widget.ConstraintLayout.LayoutParams params = 
                (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) surfaceView.getLayoutParams();
            params.dimensionRatio = null;
            params.width = 0; // Match Constraint
            params.height = 0; // Match Constraint
            params.topToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID;
            params.bottomToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID;
            params.startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID;
            params.endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID;
            surfaceView.setLayoutParams(params);
            
            // Reset de transformaciones para un calce perfecto y limpio
            surfaceView.setScaleX(1.0f); 
            surfaceView.setScaleY(1.0f);
            surfaceView.setTranslationX(0f);
            surfaceView.setTranslationY(0f); 
        } else {
            // Modo Vertical (Portrait)
            if (getSupportActionBar() != null) getSupportActionBar().show();
            if (appBarLayout != null) appBarLayout.setVisibility(View.VISIBLE);
            if (controlsContainer != null) controlsContainer.setVisibility(View.VISIBLE);
            if (portraitControls != null) portraitControls.setVisibility(View.VISIBLE);
            btnFullscreenToggle.setVisibility(View.VISIBLE);
            btnExitFs.setVisibility(View.GONE);
            
            // Restaurar Ventana
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
            
            if (mainContent != null) {
                androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams lp = 
                    (androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams) mainContent.getLayoutParams();
                lp.height = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
                lp.width = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
                lp.setBehavior(new com.google.android.material.appbar.AppBarLayout.ScrollingViewBehavior()); // RESTAURAR COMPORTAMIENTO
                mainContent.setLayoutParams(lp);
                
                int p = (int) (12 * getResources().getDisplayMetrics().density);
                mainContent.setPadding(p, p, p, p);
            }
            
            if (isBound && rtspService != null) {
                hudOverlay.setVisibility(rtspService.isRunning() ? View.VISIBLE : View.GONE);
            }
            btnStreamFs.setVisibility(View.GONE);
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                getWindow().setDecorFitsSystemWindows(true);
                android.view.WindowInsetsController controller = getWindow().getInsetsController();
                if (controller != null) {
                    controller.show(android.view.WindowInsets.Type.statusBars() | android.view.WindowInsets.Type.navigationBars());
                }
            } else {
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            }
            
            // Maximizar preview en vertical
            androidx.constraintlayout.widget.ConstraintLayout.LayoutParams params = 
                (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) surfaceView.getLayoutParams();
            params.dimensionRatio = null;
            params.width = 0;
            params.height = 0;
            surfaceView.setLayoutParams(params);
            
            surfaceView.setScaleX(1.0f);
            surfaceView.setScaleY(1.0f);
            surfaceView.setTranslationX(0f);
            surfaceView.setTranslationY(0f);
        }
        
        // RE-ESTABILIZACIÓN EN CASCADA:
        // Realizamos múltiples refrescos para asegurar que el motor OpenGL "calce" con las nuevas dimensiones
        
        // 1. Refresco cuando el layout esté listo (evita el error de replaceView)
        surfaceView.post(() -> {
            if (isBound && rtspService != null) rtspService.setView(surfaceView);
        });

        // 2. Refresco a los 500ms (cuando la animación de Android suele terminar)
        surfaceView.postDelayed(() -> {
            if (isBound && rtspService != null) {
                rtspService.setView(surfaceView);
            }
        }, 500);

        // 3. Refresco definitivo a los 1000ms (Estabilización absoluta del sensor)
        surfaceView.postDelayed(() -> {
            surfaceView.requestLayout();
            if (isBound && rtspService != null) {
                rtspService.setView(surfaceView);
            }
        }, 1000);
    }

    @Override
    public void onConfigurationChanged(@NonNull android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateFullscreenMode(newConfig.orientation);
    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        if (isBound && rtspService != null) {
            rtspService.setView(surfaceView);
            if (hasPermissions()) rtspService.startPreview();
        }
    }

    @Override public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
        // Nada que hacer aquí, pero el método es obligatorio.
    }

    @Override public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        // No llamamos a setView(null) para evitar crashes y cortes de video durante la rotación.
        // El OpenGlView gestiona su propio ciclo de vida de superficie.
    }

    private void updateUi(boolean running) {
        runOnUiThread(() -> {
            btnStream.setText(running ? "STOP STREAM" : "START STREAM");
            btnStream.setTextColor(Color.WHITE); // Texto blanco forzado
            btnStream.setIconResource(running ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
            btnStream.setIconTint(ContextCompat.getColorStateList(this, android.R.color.white));
            btnStream.setBackgroundTintList(ContextCompat.getColorStateList(this, running ? android.R.color.holo_orange_dark : android.R.color.holo_red_dark));
            
            // Sincronizar botón de Fullscreen (Circular, sin texto, icono blanco)
            btnStreamFs.setText(""); 
            btnStreamFs.setIconResource(running ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
            btnStreamFs.setBackgroundTintList(ContextCompat.getColorStateList(this, running ? android.R.color.holo_orange_dark : android.R.color.holo_red_dark));

            // Bloqueo de rotación durante el stream
            if (running) {
                int currentOrientation = getResources().getConfiguration().orientation;
                if (currentOrientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                } else {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                }
            } else {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            }

            // Bloqueo total de funciones
            spinnerResolution.setEnabled(!running);
            spinnerCodec.setEnabled(!running);
            spinnerFps.setEnabled(!running);
            etUser.setEnabled(!running);
            etPass.setEnabled(!running);
            etPort.setEnabled(!running);
            
            // Ocultamos botones de fullscreen durante el stream para evitar accidentes
            btnFullscreenToggle.setVisibility(running ? View.GONE : View.VISIBLE);
            btnExitFs.setVisibility((!running && getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) ? View.VISIBLE : View.GONE);
            
            // El HUD permanece visible en Landscape (Fullscreen) siempre. 
            // En Portrait solo se muestra si está corriendo el stream.
            if (getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                hudOverlay.setVisibility(View.VISIBLE);
                btnStreamFs.setVisibility(View.VISIBLE);
                
                // Actualizar URL de info siempre en landscape para evitar "localhost"
                String ip = NetworkUtils.getIPAddress();
                String port = etPort.getText().toString().trim();
                String user = etUser.getText().toString().trim();
                String pass = etPass.getText().toString();
                
                StringBuilder baseUrl = new StringBuilder("rtsp://");
                if (!user.isEmpty() && !pass.isEmpty()) {
                    baseUrl.append(user).append(":").append(pass).append("@");
                }
                baseUrl.append(ip).append(":").append(port);
                
                hudUrl.setText("MAIN: " + baseUrl.toString() + "/stream");
                hudUrlSub.setText("SUB:  " + baseUrl.toString() + "/live");
            } else {
                hudOverlay.setVisibility(running ? View.VISIBLE : View.GONE);
                btnStreamFs.setVisibility(View.GONE);
            }

            if (running) {
                String user = etUser.getText().toString().trim();
                String pass = etPass.getText().toString();
                String port = etPort.getText().toString().trim();
                String ip = NetworkUtils.getIPAddress();
                
                StringBuilder baseUrl = new StringBuilder("rtsp://");
                if (!user.isEmpty() && !pass.isEmpty()) {
                    baseUrl.append(user).append(":").append(pass).append("@");
                }
                baseUrl.append(ip).append(":").append(port);
                
                hudUrl.setText("MAIN: " + baseUrl.toString() + "/stream");
                hudUrlSub.setText("SUB:  " + baseUrl.toString() + "/live");
            }
        });
    }
    @Override
    public void onNewLog(String message, boolean isError) {
        runOnUiThread(() -> {
            logsText.append("\n" + message);
            logsScrollView.post(() -> logsScrollView.fullScroll(View.FOCUS_DOWN));
        });
    }

    @Override public void onStatusChanged(boolean running) { updateUi(running); }
    @Override public void onStatsUpdate(int clients, long bitrate) {
        // Estadísticas eliminadas
    }

    private boolean hasPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
               ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, 1);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isBound) {
            rtspService.removeLogListener(this);
            unbindService(serviceConnection);
        }
    }
}