package com.casthor.ui;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.casthor.R;
import com.casthor.server.RtspServerService;
import com.casthor.utils.NetworkUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.pedro.library.view.OpenGlView;

public class MainActivity extends AppCompatActivity implements RtspServerService.LogListener, SurfaceHolder.Callback {

    private static final String APP_VERSION = "V1.1";
    private static final String KEY_BRIGHTNESS_DIM = "brightness_dim";

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
    private FloatingActionButton btnFullscreenToggle, btnExitFs, btnMute, btnBrightness;
    private boolean isDimmed = false;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            rtspService = ((RtspServerService.LocalBinder) service).getService();
            isBound = true;
            rtspService.addLogListener(MainActivity.this);
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
        isDimmed = prefs.getBoolean(KEY_BRIGHTNESS_DIM, false);

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
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.screenBrightness = isDimmed ? 0.05f : -1.0f;
            getWindow().setAttributes(lp);
            btnBrightness.setSupportImageTintList(ContextCompat.getColorStateList(this, isDimmed ? android.R.color.holo_orange_light : android.R.color.white));
            prefs.edit().putBoolean(KEY_BRIGHTNESS_DIM, isDimmed).apply();
        });

        if (isDimmed) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.screenBrightness = 0.05f;
            getWindow().setAttributes(lp);
        }

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

        ArrayAdapter<CharSequence> resAdapter = ArrayAdapter.createFromResource(this, R.array.resolutions, android.R.layout.simple_spinner_dropdown_item);
        spinnerResolution.setAdapter(resAdapter);
        ArrayAdapter<CharSequence> codecAdapter = ArrayAdapter.createFromResource(this, R.array.codecs, android.R.layout.simple_spinner_dropdown_item);
        spinnerCodec.setAdapter(codecAdapter);
        ArrayAdapter<String> fpsAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"15 FPS", "25 FPS", "30 FPS", "60 FPS"});
        spinnerFps.setAdapter(fpsAdapter);

        // Load saved selections BEFORE setting listener to avoid unwanted writes
        spinnerResolution.setSelection(prefs.getInt("last_resolution", 1));
        spinnerCodec.setSelection(prefs.getInt("last_codec", 0));
        spinnerFps.setSelection(prefs.getInt("last_fps", 2));
        etUser.setText(prefs.getString("last_user", ""));
        etPass.setText(prefs.getString("last_pass", ""));
        etPort.setText(prefs.getString("last_port", "8554"));

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

        btnStream.setOnClickListener(v -> toggleStream());
        btnStreamFs.setOnClickListener(v -> toggleStream());
        btnFullscreenToggle.setOnClickListener(v -> setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
        btnExitFs.setOnClickListener(v -> setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));

        findViewById(R.id.kofiButton).setOnClickListener(v -> {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://ko-fi.com/washaka81")));
        });

        if (hasPermissions()) {
            startRtspService();
        } else {
            requestPermissions();
        }
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }
        
        updateFullscreenMode(getResources().getConfiguration().orientation);
    }

    private String buildRtspUrl() {
        String ip = NetworkUtils.getIPAddress();
        String port = etPort.getText().toString().trim();
        String user = etUser.getText().toString().trim();
        String pass = etPass.getText().toString();

        StringBuilder url = new StringBuilder("rtsp://");
        if (!user.isEmpty() && !pass.isEmpty()) {
            url.append(user).append(":").append(pass).append("@");
        }
        url.append(ip).append(":").append(port);
        return url.toString();
    }

    private void updateMuteButtons(boolean muted) {
        int icon = muted ? android.R.drawable.stat_notify_call_mute : android.R.drawable.ic_btn_speak_now;
        int tint = muted ? android.R.color.holo_red_light : android.R.color.white;
        
        btnMute.setImageResource(icon);
        btnMute.setSupportImageTintList(ContextCompat.getColorStateList(this, tint));
    }

    private void toggleStream() {
        if (!isBound) {
            if (hasPermissions()) {
                startRtspService();
            }
            return;
        }
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
            try { port = Integer.parseInt(portStr); }
            catch (NumberFormatException e) { Log.w("CastThor", "Invalid port number, using default", e); }
            
            prefs.edit()
                .putString("last_user", user)
                .putString("last_pass", pass)
                .putString("last_port", String.valueOf(port))
                .apply();
            
            int width = 2560, height = 1440;
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

    private void enterLandscapeMode() {
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        if (appBarLayout != null) appBarLayout.setVisibility(View.GONE);
        if (controlsContainer != null) controlsContainer.setVisibility(View.GONE);
        if (portraitControls != null) portraitControls.setVisibility(View.GONE);
        btnFullscreenToggle.setVisibility(View.GONE);
        btnExitFs.setVisibility(View.VISIBLE);
        hudOverlay.setVisibility(View.VISIBLE);
        btnStreamFs.setVisibility(View.VISIBLE);
        
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            WindowManager.LayoutParams wlp = getWindow().getAttributes();
            wlp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(wlp);
        }
        
        if (mainContent != null) {
            mainContent.setPadding(0, 0, 0, 0);
            CoordinatorLayout.LayoutParams lp = (CoordinatorLayout.LayoutParams) mainContent.getLayoutParams();
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.setBehavior(null);
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
            
        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) surfaceView.getLayoutParams();
        params.dimensionRatio = null;
        params.width = 0;
        params.height = 0;
        params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
        params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
        params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
        params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
        surfaceView.setLayoutParams(params);
        
        surfaceView.setScaleX(1.0f); 
        surfaceView.setScaleY(1.0f);
        surfaceView.setTranslationX(0f);
        surfaceView.setTranslationY(0f);
    }

    private void enterPortraitMode() {
        if (getSupportActionBar() != null) getSupportActionBar().show();
        if (appBarLayout != null) appBarLayout.setVisibility(View.VISIBLE);
        if (controlsContainer != null) controlsContainer.setVisibility(View.VISIBLE);
        if (portraitControls != null) portraitControls.setVisibility(View.VISIBLE);
        btnFullscreenToggle.setVisibility(View.VISIBLE);
        btnExitFs.setVisibility(View.GONE);
        
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        
        if (mainContent != null) {
            CoordinatorLayout.LayoutParams lp = (CoordinatorLayout.LayoutParams) mainContent.getLayoutParams();
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.setBehavior(new com.google.android.material.appbar.AppBarLayout.ScrollingViewBehavior());
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
        
        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) surfaceView.getLayoutParams();
        params.dimensionRatio = null;
        params.width = 0;
        params.height = 0;
        surfaceView.setLayoutParams(params);
        
        surfaceView.setScaleX(1.0f);
        surfaceView.setScaleY(1.0f);
        surfaceView.setTranslationX(0f);
        surfaceView.setTranslationY(0f);
    }

    private void updateFullscreenMode(int orientation) {
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            enterLandscapeMode();
        } else {
            enterPortraitMode();
        }
        
        surfaceView.post(() -> {
            if (isBound && rtspService != null) rtspService.setView(surfaceView);
        });

        surfaceView.postDelayed(() -> {
            if (isBound && rtspService != null) {
                rtspService.setView(surfaceView);
            }
        }, 500);

        surfaceView.postDelayed(() -> {
            surfaceView.requestLayout();
            if (isBound && rtspService != null) {
                rtspService.setView(surfaceView);
            }
        }, 1000);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
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
    }

    @Override public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
    }

    private void updateUi(boolean running) {
        runOnUiThread(() -> {
            btnStream.setText(running ? "STOP STREAM" : "START STREAM");
            btnStream.setTextColor(Color.WHITE);
            btnStream.setIconResource(running ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
            btnStream.setIconTint(ContextCompat.getColorStateList(this, android.R.color.white));
            btnStream.setBackgroundTintList(ContextCompat.getColorStateList(this, running ? android.R.color.holo_orange_dark : android.R.color.holo_red_dark));
            
            btnStreamFs.setText(""); 
            btnStreamFs.setIconResource(running ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
            btnStreamFs.setBackgroundTintList(ContextCompat.getColorStateList(this, running ? android.R.color.holo_orange_dark : android.R.color.holo_red_dark));

            if (running) {
                int currentOrientation = getResources().getConfiguration().orientation;
                if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                } else {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                }
            } else {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            }

            spinnerResolution.setEnabled(!running);
            spinnerCodec.setEnabled(!running);
            spinnerFps.setEnabled(!running);
            etUser.setEnabled(!running);
            etPass.setEnabled(!running);
            etPort.setEnabled(!running);
            
            btnFullscreenToggle.setVisibility(running ? View.GONE : View.VISIBLE);
            btnExitFs.setVisibility((!running && getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) ? View.VISIBLE : View.GONE);
            
            if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                hudOverlay.setVisibility(View.VISIBLE);
                btnStreamFs.setVisibility(View.VISIBLE);
                updateHudUrls();
            } else {
                hudOverlay.setVisibility(running ? View.VISIBLE : View.GONE);
                btnStreamFs.setVisibility(View.GONE);
            }

            if (running) {
                updateHudUrls();
            }
        });
    }

    private void updateHudUrls() {
        String baseUrl = buildRtspUrl();
        hudUrl.setText("MAIN: " + baseUrl + "/stream");
        hudUrlSub.setText("SUB:  " + baseUrl + "/live");
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
    }

    private boolean hasPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
               ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, 1);
    }

    private void startRtspService() {
        Intent intent = new Intent(this, RtspServerService.class);
        startForegroundService(intent);
        bindService(intent, serviceConnection, BIND_AUTO_CREATE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startRtspService();
        }
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
