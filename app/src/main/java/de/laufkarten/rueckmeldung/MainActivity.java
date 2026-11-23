package de.laufkarten.rueckmeldung;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.URLUtil;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner;
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;

import org.json.JSONObject;

/**
 * Rückmelde-App: zeigt die Rückmelde-Seite des Laufkarten-Servers im Vollbild an
 * und stellt einen Barcode-Scanner (Google Code Scanner) für die Webseite bereit.
 *
 * Schnittstelle zur Webseite (window.LaufkartenApp):
 *   scan()           -> öffnet den Scanner, Ergebnis an window.onNativeScan(code)
 *   einstellungen()  -> Dialog zur Server-Adresse
 *   neuLaden()       -> Seite neu laden
 */
public class MainActivity extends Activity {

    private static final String PREFS = "einstellungen";
    private static final String KEY_SERVER = "server";
    private static final int STANDARD_PORT = 8080;

    private WebView webView;
    private SharedPreferences prefs;
    private DownloadManager downloadManager;
    private BroadcastReceiver downloadReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        webView = new WebView(this);
        setContentView(webView);
        vollbild();

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);          // Personalnummer bleibt gespeichert
        s.setDatabaseEnabled(true);
        s.setBuiltInZoomControls(false);
        s.setSupportMultipleWindows(false);    // target=_blank im selben Fenster
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        CookieManager.getInstance().setAcceptCookie(true);

        webView.addJavascriptInterface(new Bruecke(), "LaufkartenApp");
        webView.setWebViewClient(new Client());
        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) ->
                herunterladen(url, URLUtil.guessFileName(url, contentDisposition, mimeType), mimeType));

        downloadReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (id >= 0) oeffneDownload(id);
            }
        };
        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(downloadReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(downloadReceiver, filter);
        }

        String server = prefs.getString(KEY_SERVER, "");
        if (server.isEmpty()) {
            serverDialog(true);
        } else {
            webView.loadUrl(server + "/");
        }
    }

    @Override
    protected void onDestroy() {
        try { unregisterReceiver(downloadReceiver); } catch (Exception ignored) { }
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) vollbild();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    // ------------------------------------------------------------------ Vollbild
    @SuppressWarnings("deprecation")
    private void vollbild() {
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }
    }

    // ------------------------------------------------------------ Server-Adresse
    /** "srv01" -> "http://srv01:8080", "192.168.1.20" -> "http://192.168.1.20:8080" */
    static String normalisiere(String eingabe) {
        String a = eingabe.trim();
        while (a.endsWith("/")) a = a.substring(0, a.length() - 1);
        if (a.endsWith("/buero")) a = a.substring(0, a.length() - 6);
        if (!a.matches("(?i)^https?://.*")) a = "http://" + a;
        if (!a.matches("(?i)^https?://[^/]+:\\d+.*")) {
            int slash = a.indexOf('/', a.indexOf("//") + 2);
            a = slash < 0 ? a + ":" + STANDARD_PORT : a.substring(0, slash) + ":" + STANDARD_PORT + a.substring(slash);
        }
        return a;
    }

    private void serverDialog(boolean ersterStart) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (20 * getResources().getDisplayMetrics().density);
        box.setPadding(p, p / 2, p, 0);

        TextView hinweis = new TextView(this);
        hinweis.setText("Name oder IP-Adresse des Laufkarten-Servers, z. B. DATENCNC oder 192.168.1.20.\n"
                + "http:// und :" + STANDARD_PORT + " werden automatisch ergänzt.");
        box.addView(hinweis);

        EditText feld = new EditText(this);
        feld.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        feld.setSingleLine(true);
        feld.setText(prefs.getString(KEY_SERVER, ""));
        feld.setHint("DATENCNC");
        box.addView(feld);

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle("Server-Adresse")
                .setView(box)
                .setCancelable(!ersterStart)
                .setPositiveButton("Speichern", (d, w) -> {
                    String text = feld.getText().toString();
                    if (text.trim().isEmpty()) {
                        Toast.makeText(this, "Bitte eine Server-Adresse eingeben", Toast.LENGTH_LONG).show();
                        serverDialog(ersterStart);
                        return;
                    }
                    String server = normalisiere(text);
                    prefs.edit().putString(KEY_SERVER, server).apply();
                    webView.clearHistory();
                    webView.loadUrl(server + "/");
                    Toast.makeText(this, "Server: " + server, Toast.LENGTH_SHORT).show();
                });
        if (!ersterStart) b.setNegativeButton("Abbrechen", null);
        b.show();
    }

    // ------------------------------------------------------------------ Scanner
    private void scannen() {
        GmsBarcodeScannerOptions optionen = new GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_CODE_128, Barcode.FORMAT_CODE_39, Barcode.FORMAT_QR_CODE)
                .enableAutoZoom()
                .build();
        GmsBarcodeScanner scanner = GmsBarcodeScanning.getClient(this, optionen);
        scanner.startScan()
                .addOnSuccessListener(barcode -> anWebseite("onNativeScan", barcode.getRawValue()))
                .addOnFailureListener(e -> anWebseite("onNativeScanFehler",
                        "Scanner nicht verfügbar (Google Play-Dienste aktuell?): " + e.getMessage()));
    }

    private void anWebseite(String funktion, String wert) {
        if (wert == null) return;
        String js = "window." + funktion + " && window." + funktion + "(" + JSONObject.quote(wert) + ")";
        webView.post(() -> webView.evaluateJavascript(js, null));
    }

    // --------------------------------------------- Auftragsdaten (PDF/DXF/STEP)
    private void herunterladen(String url, String dateiname, String mimeType) {
        try {
            DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
            r.setTitle(dateiname);
            r.setDescription("Auftragsdaten");
            if (mimeType != null && !mimeType.isEmpty()) r.setMimeType(mimeType);
            r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            // App-eigener Download-Ordner: braucht auf keiner Android-Version eine Speicher-Berechtigung
            r.setDestinationInExternalFilesDir(MainActivity.this, Environment.DIRECTORY_DOWNLOADS, dateiname);
            String cookies = CookieManager.getInstance().getCookie(url);
            if (cookies != null) r.addRequestHeader("Cookie", cookies);
            downloadManager.enqueue(r);
            Toast.makeText(this, "Lade " + dateiname + " …", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Download nicht möglich: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void oeffneDownload(long id) {
        Uri uri = downloadManager.getUriForDownloadedFile(id);
        if (uri == null) {
            Toast.makeText(this, "Download fehlgeschlagen", Toast.LENGTH_LONG).show();
            return;
        }
        String mime = downloadManager.getMimeTypeForDownloadedFile(id);
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(uri, mime != null ? mime : "*/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            // keine App für diesen Dateityp registriert -> Auswahl aller Apps anbieten
            Intent alle = new Intent(Intent.ACTION_VIEW);
            alle.setDataAndType(uri, "*/*");
            alle.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(Intent.createChooser(alle, "Öffnen mit …"));
            } catch (ActivityNotFoundException e2) {
                Toast.makeText(this, "Keine App zum Öffnen gefunden. ",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private static String dateinameAusUrl(String url) {
        String pfad = Uri.parse(url).getQueryParameter("pfad");
        if (pfad != null && !pfad.isEmpty()) {
            int i = pfad.lastIndexOf('/');
            return i >= 0 ? pfad.substring(i + 1) : pfad;
        }
        return URLUtil.guessFileName(url, null, null);
    }

    private static String mimeAusName(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".pdf")) return "application/pdf";
        if (n.endsWith(".dxf")) return "image/vnd.dxf";
        if (n.endsWith(".step") || n.endsWith(".stp")) return "model/step";
        return "application/octet-stream";
    }

    // -------------------------------------------------------------- WebView
    private class Client extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();
            String server = prefs.getString(KEY_SERVER, "");
            if (url.contains("/datei?")) {                 // Auftragsdaten -> herunterladen und öffnen
                String name = dateinameAusUrl(url);
                herunterladen(url, name, mimeAusName(name));
                return true;
            }
            if (!server.isEmpty() && url.startsWith(server)) return false;   // eigene Seiten in der App
            try {                                          // fremde Links im Browser
                startActivity(new Intent(Intent.ACTION_VIEW, request.getUrl()));
            } catch (ActivityNotFoundException ignored) { }
            return true;
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (!request.isForMainFrame()) return;
            String server = prefs.getString(KEY_SERVER, "");
            String html = "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"
                    + "<style>body{font-family:sans-serif;background:#eef1f4;color:#1c2430;padding:24px}"
                    + "h2{margin-top:40px}button{display:block;width:100%;font-size:18px;padding:16px;margin:12px 0;"
                    + "border:0;border-radius:12px;background:#e8792b;color:#fff}button.s{background:#dfe4e9;color:#1c2430}"
                    + "code{background:#fff;padding:2px 6px;border-radius:4px}</style></head><body>"
                    + "<h2>Server nicht erreichbar</h2>"
                    + "<p>Adresse: <code>" + TextUtilsEsc(server) + "</code></p>"
                    + "<p>" + TextUtilsEsc(String.valueOf(error.getDescription())) + "</p>"
                    + "<p>Ist das Gerät im Firmen-WLAN? Läuft der Server?</p>"
                    + "<button onclick='LaufkartenApp.neuLaden()'>Erneut versuchen</button>"
                    + "<button class='s' onclick='LaufkartenApp.einstellungen()'>Server-Adresse ändern</button>"
                    + "</body></html>";
            view.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
        }
    }

    private static String TextUtilsEsc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Wird von der Webseite als window.LaufkartenApp aufgerufen. */
    private class Bruecke {
        @JavascriptInterface
        public void scan() {
            runOnUiThread(MainActivity.this::scannen);
        }

        @JavascriptInterface
        public void einstellungen() {
            runOnUiThread(() -> serverDialog(false));
        }

        @JavascriptInterface
        public void neuLaden() {
            runOnUiThread(() -> {
                String server = prefs.getString(KEY_SERVER, "");
                if (server.isEmpty()) serverDialog(true); else webView.loadUrl(server + "/");
            });
        }

        @JavascriptInterface
        public String version() {
            return "1";
        }
    }
}
