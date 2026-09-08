package labs.victimok.kartochka;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.webkit.WebViewAssetLoader;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

/**
 * Фото карта — WebView + SQLite.
 * Photo sources: camera / gallery / file manager via native dialog.
 */
public class MainActivity extends AppCompatActivity {
    private WebView webView;
    private ProgressBar progress;
    private CardDb db;

    private String pendingCardId;
    private Uri cameraUri;
    private File cameraFile;

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
        registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (granted) launchCamera();
            else Toast.makeText(this, "Нужен доступ к камере", Toast.LENGTH_SHORT).show();
        });

    private final ActivityResultLauncher<Intent> cameraLauncher =
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && cameraFile != null && cameraFile.exists()) {
                ingestFile(cameraFile, "image/jpeg");
            } else {
                cleanupCameraTemp();
            }
        });

    private final ActivityResultLauncher<Intent> galleryLauncher =
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null && result.getData().getData() != null) {
                ingestUri(result.getData().getData());
            }
        });

    private final ActivityResultLauncher<Intent> filesLauncher =
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null && result.getData().getData() != null) {
                ingestUri(result.getData().getData());
            }
        });

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        db = new CardDb(this);
        webView = findViewById(R.id.webview);
        progress = findViewById(R.id.progress);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setOffscreenPreRaster(true);
        s.setUserAgentString(s.getUserAgentString() + " FotoKartaAndroid/1.0.1");

        // Fully offline — never hit the real network
        s.setBlockNetworkLoads(true);
        s.setBlockNetworkImage(true);

        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webView.setBackgroundColor(0xFF070B12);
        webView.addJavascriptInterface(new NativeApi(), "KartochkaNative");

        final WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
            .build();

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri url = request.getUrl();
                if (url != null && "appassets.androidplatform.net".equals(url.getHost())
                    && url.getPath() != null && url.getPath().startsWith("/photo/")) {
                    String name = url.getLastPathSegment();
                    File f = db.photoFile(name);
                    if (f != null && f.exists()) {
                        try {
                            InputStream in = new FileInputStream(f);
                            String mime = "image/jpeg";
                            String n = name.toLowerCase();
                            if (n.endsWith(".png")) mime = "image/png";
                            else if (n.endsWith(".webp")) mime = "image/webp";
                            else if (n.endsWith(".gif")) mime = "image/gif";
                            return new WebResourceResponse(mime, null, in);
                        } catch (Exception ignored) {}
                    }
                }
                return assetLoader.shouldInterceptRequest(url);
            }


            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request != null ? request.getUrl() : null;
                return u == null || !"appassets.androidplatform.net".equals(u.getHost());
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                progress.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progress.setVisibility(View.GONE);
            }
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView == null) {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    return;
                }
                webView.evaluateJavascript(
                    "(function(){try{return !!(window.__fotoCloseCard&&window.__fotoCloseCard());}catch(e){return false;}})()",
                    value -> {
                        if ("true".equals(value)) return;
                        if (webView != null && webView.canGoBack()) {
                            webView.goBack();
                            return;
                        }
                        setEnabled(false);
                        getOnBackPressedDispatcher().onBackPressed();
                    }
                );
            }
        });

        webView.loadUrl("https://appassets.androidplatform.net/assets/www/index.html");
    }

    /** Called from JS — shows camera / gallery / files menu. */
    void showPhotoSourceDialog(String cardId) {
        runOnUiThread(() -> {
            pendingCardId = cardId;
            CharSequence[] items = new CharSequence[]{
                "📷 Камера",
                "🖼 Галерея",
                "📁 Файловый менеджер"
            };
            new AlertDialog.Builder(this)
                .setTitle("Добавить фото")
                .setItems(items, (d, which) -> {
                    if (which == 0) requestCamera();
                    else if (which == 1) launchGallery();
                    else launchFiles();
                })
                .setNegativeButton("Отмена", null)
                .show();
        });
    }

    private void requestCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            launchCamera();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void launchCamera() {
        try {
            File dir = new File(getCacheDir(), "camera");
            if (!dir.exists()) dir.mkdirs();
            cameraFile = new File(dir, "capture_" + System.currentTimeMillis() + ".jpg");
            cameraUri = FileProvider.getUriForFile(
                this, getPackageName() + ".fileprovider", cameraFile);
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            cameraLauncher.launch(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Камера недоступна: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void launchGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        galleryLauncher.launch(Intent.createChooser(intent, "Галерея"));
    }

    private void launchFiles() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
            "image/jpeg", "image/png", "image/webp", "image/gif"
        });
        filesLauncher.launch(Intent.createChooser(intent, "Файлы"));
    }

    private void ingestUri(Uri uri) {
        if (pendingCardId == null || uri == null) return;
        try {
            String mime = getContentResolver().getType(uri);
            if (mime == null) mime = "image/jpeg";
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) throw new IllegalStateException("не открыть файл");
                byte[] data = readAll(in);
                String b64 = Base64.encodeToString(data, Base64.NO_WRAP);
                JSONObject card = db.addPhoto(pendingCardId, mime, b64);
                notifyWeb(card);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Не удалось добавить фото: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void ingestFile(File file, String mime) {
        if (pendingCardId == null || file == null || !file.exists()) {
            cleanupCameraTemp();
            return;
        }
        try {
            try (InputStream in = new FileInputStream(file)) {
                byte[] data = readAll(in);
                String b64 = Base64.encodeToString(data, Base64.NO_WRAP);
                JSONObject card = db.addPhoto(pendingCardId, mime, b64);
                notifyWeb(card);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Не удалось сохранить снимок: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            cleanupCameraTemp();
        }
    }

    private void notifyWeb(JSONObject card) {
        String json = card.toString().replace("\\", "\\\\").replace("'", "\\'");
        webView.post(() -> webView.evaluateJavascript(
            "window.onNativePhotoAdded && window.onNativePhotoAdded(" + card.toString() + ")",
            null
        ));
    }

    private void cleanupCameraTemp() {
        if (cameraFile != null && cameraFile.exists()) {
            // keep file until copied; delete after ingest
            //noinspection ResultOfMethodCallIgnored
            cameraFile.delete();
        }
        cameraFile = null;
        cameraUri = null;
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) >= 0) bos.write(buf, 0, n);
        return bos.toByteArray();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        if (db != null) {
            db.close();
            db = null;
        }
        super.onDestroy();
    }

    /** JS bridge — CRUD + pickPhoto dialog. */
    public class NativeApi {
        private final CardBridge cards = new CardBridge(db);

        @JavascriptInterface
        public String listCards() { return cards.listCards(); }

        @JavascriptInterface
        public String getCard(String id) { return cards.getCard(id); }

        @JavascriptInterface
        public String createCard(String name, String description) {
            return cards.createCard(name, description);
        }

        @JavascriptInterface
        public String updateCard(String id, String name, String description) {
            return cards.updateCard(id, name, description);
        }

        @JavascriptInterface
        public String deleteCard(String id) { return cards.deleteCard(id); }

        @JavascriptInterface
        public String addPhoto(String id, String mime, String base64) {
            return cards.addPhoto(id, mime, base64);
        }

        @JavascriptInterface
        public String removePhoto(String id, int index) {
            return cards.removePhoto(id, index);
        }

        @JavascriptInterface
        public int maxPhotos() { return cards.maxPhotos(); }

        @JavascriptInterface
        public void pickPhoto(String cardId) {
            showPhotoSourceDialog(cardId);
        }
    }
}
