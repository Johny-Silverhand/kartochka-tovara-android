package labs.victimok.kartochka;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.webkit.WebViewAssetLoader;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

/**
 * Offline WebView shell + SQLite via CardBridge.
 * Performance: hardware layer, DOM/cache, wide viewport for auto density scaling.
 */
public class MainActivity extends AppCompatActivity {
    private WebView webView;
    private ProgressBar progress;
    private CardDb db;

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
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setOffscreenPreRaster(true);
        s.setGeolocationEnabled(false);
        s.setUserAgentString(s.getUserAgentString() + " KartochkaAndroid/1.0");

        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webView.setBackgroundColor(0xFF070B12);
        webView.addJavascriptInterface(new CardBridge(db), "KartochkaNative");

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
                if (webView != null && webView.canGoBack()) webView.goBack();
                else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        webView.loadUrl("https://appassets.androidplatform.net/assets/www/index.html");
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
}
