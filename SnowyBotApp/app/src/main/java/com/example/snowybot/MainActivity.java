package com.example.snowybot;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private TextView txtConsoleLog;
    private ScrollView scrollConsole;

    // JavaScript interface bridge to forward console messages directly to native Android view
    public class AndroidConsoleBridge {
        @JavascriptInterface
        public void log(String message) {
            appendConsoleLog(message);
        }

        @JavascriptInterface
        public void error(String message) {
            appendConsoleLog("[ERROR] " + message);
        }

        @JavascriptInterface
        public void warn(String message) {
            appendConsoleLog("[WARN] " + message);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.web_view);
        Button btnRunScript = findViewById(R.id.btn_run_script);
        txtConsoleLog = findViewById(R.id.txt_console_log);
        scrollConsole = findViewById(R.id.scroll_console);

        // Configure WebView settings
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        webSettings.setMediaPlaybackRequiresUserGesture(false);

        // Add JavaScript interface for direct console logging
        webView.addJavascriptInterface(new AndroidConsoleBridge(), "AndroidConsole");

        // Restrict navigation to just-dice.com only
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                return handleUrlLoading(url);
            }

            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrlLoading(url);
            }

            private boolean handleUrlLoading(String url) {
                if (isAllowedUrl(url)) {
                    return false; // Allow WebView to load
                } else {
                    Toast.makeText(MainActivity.this, "Access restricted to just-dice.com", Toast.LENGTH_SHORT).show();
                    appendConsoleLog("[WARNING] Navigation blocked: " + url);
                    return true; // Block navigation
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                appendConsoleLog("[System] Page loaded: " + url);
                injectConsoleBridgeOverride();
                injectKeepAliveAudio();
            }
        });

        // Capture standard WebChromeClient console.log messages as a fallback
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                String msg = String.format("[%s] %s",
                        consoleMessage.messageLevel(),
                        consoleMessage.message());
                appendConsoleLog(msg);
                return true;
            }
        });

        // Top right button click listener to inject and run snowybot.js in the browser context
        btnRunScript.setOnClickListener(v -> {
            String script = loadAssetScript("snowybot.js");
            if (script != null) {
                appendConsoleLog("[System] Injecting snowybot.js into browser...");
                try {
                    injectKeepAliveAudio();

                    String consoleOverride =
                            "(function() {" +
                            "  if (window.AndroidConsole) {" +
                            "    var _l = console.log, _e = console.error, _w = console.warn;" +
                            "    console.log = function() {" +
                            "      var m = Array.prototype.slice.call(arguments).map(function(a){ return typeof a==='object'?JSON.stringify(a):a; }).join(' ');" +
                            "      window.AndroidConsole.log(m);" +
                            "      if (_l) try { _l.apply(console, arguments); } catch(x){}" +
                            "    };" +
                            "    console.error = function() {" +
                            "      var m = Array.prototype.slice.call(arguments).map(function(a){ return typeof a==='object'?JSON.stringify(a):a; }).join(' ');" +
                            "      window.AndroidConsole.error(m);" +
                            "      if (_e) try { _e.apply(console, arguments); } catch(x){}" +
                            "    };" +
                            "    console.warn = function() {" +
                            "      var m = Array.prototype.slice.call(arguments).map(function(a){ return typeof a==='object'?JSON.stringify(a):a; }).join(' ');" +
                            "      window.AndroidConsole.warn(m);" +
                            "      if (_w) try { _w.apply(console, arguments); } catch(x){}" +
                            "    };" +
                            "  }" +
                            "})();\n";

                    String fullScript = consoleOverride + script;
                    webView.evaluateJavascript(fullScript, value ->
                        appendConsoleLog("[System] Triggered script in browser context.")
                    );
                } catch (Exception e) {
                    appendConsoleLog("[ERROR] Failed to inject script: " + e.getMessage());
                }
            } else {
                appendConsoleLog("[ERROR] Failed to load snowybot.js from assets.");
            }
        });

        // Prompt for notification permissions and request battery optimization exclusion
        requestRequiredPermissions();

        // Start background foreground service to keep CPU alive
        startBotForegroundService();

        // Restore state if available, else load just-dice.com
        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            appendConsoleLog("[System] Loading https://just-dice.com ...");
            webView.loadUrl("https://just-dice.com");
        }
    }

    private void startBotForegroundService() {
        try {
            Intent serviceIntent = new Intent(this, SnowyBotService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        } catch (Exception e) {
            appendConsoleLog("[WARNING] Could not start foreground service: " + e.getMessage());
        }
    }

    private void requestRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
            try {
                @SuppressLint("BatteryLife")
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Exception e) {
                // Ignore if device does not support this settings activity
            }
        }
    }

    private void injectConsoleBridgeOverride() {
        String bridgeJs =
                "(function() {" +
                "  if (window.AndroidConsole) {" +
                "    var _l = console.log, _e = console.error, _w = console.warn;" +
                "    console.log = function() {" +
                "      var m = Array.prototype.slice.call(arguments).map(function(a){ return typeof a==='object'?JSON.stringify(a):a; }).join(' ');" +
                "      window.AndroidConsole.log(m);" +
                "      if (_l) try { _l.apply(console, arguments); } catch(x){}" +
                "    };" +
                "    console.error = function() {" +
                "      var m = Array.prototype.slice.call(arguments).map(function(a){ return typeof a==='object'?JSON.stringify(a):a; }).join(' ');" +
                "      window.AndroidConsole.error(m);" +
                "      if (_e) try { _e.apply(console, arguments); } catch(x){}" +
                "    };" +
                "    console.warn = function() {" +
                "      var m = Array.prototype.slice.call(arguments).map(function(a){ return typeof a==='object'?JSON.stringify(a):a; }).join(' ');" +
                "      window.AndroidConsole.warn(m);" +
                "      if (_w) try { _w.apply(console, arguments); } catch(x){}" +
                "    };" +
                "  }" +
                "})();";
        webView.evaluateJavascript(bridgeJs, null);
    }

    private void injectKeepAliveAudio() {
        String silentAudioJs =
                "(function() {" +
                "  var audio = document.getElementById('snowybot-silent-audio');" +
                "  if (!audio) {" +
                "    audio = document.createElement('audio');" +
                "    audio.id = 'snowybot-silent-audio';" +
                "    audio.loop = true;" +
                "    audio.src = 'data:audio/wav;base64,UklGRiQAAABXQVZFZm10IBAAAAABAAEARKwAAIhYAQACABAAZGF0YQAAAAA=';" +
                "    audio.addEventListener('pause', function() {" +
                "      setTimeout(function() { if (audio.paused) audio.play().catch(function(e){}); }, 1000);" +
                "    });" +
                "    (document.body || document.documentElement).appendChild(audio);" +
                "  }" +
                "  audio.play().catch(function(e){ console.log('[System] Silent audio autoplay handled'); });" +
                "})();";
        webView.evaluateJavascript(silentAudioJs, null);
    }

    private boolean isAllowedUrl(String url) {
        if (url == null) return false;
        Uri uri = Uri.parse(url);
        String host = uri.getHost();
        if (host == null) return false;
        return host.equalsIgnoreCase("just-dice.com") || host.toLowerCase().endsWith(".just-dice.com");
    }

    private void appendConsoleLog(final String text) {
        runOnUiThread(() -> {
            txtConsoleLog.append(text + "\n");
            scrollConsole.post(() -> scrollConsole.fullScroll(ScrollView.FOCUS_DOWN));
        });
    }

    private String loadAssetScript(String filename) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(getAssets().open(filename)))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Configuration change handled without destroying activity/WebView
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (webView != null) {
            webView.saveState(outState);
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
