package es.straker.app;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Bundle;
import android.view.WindowInsets;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;

public final class MainActivity extends Activity {
    private static final String HOST = "straker.invalid";
    private WebView web;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setDecorFitsSystemWindows(false);
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Color.rgb(21, 22, 20));
        frame.setOnApplyWindowInsetsListener((view, insets) -> {
            Insets padding = insets.getInsets(WindowInsets.Type.systemBars()
                    | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
            view.setPadding(padding.left, padding.top, padding.right, padding.bottom);
            return WindowInsets.CONSUMED;
        });
        web = new WebView(this);
        web.setBackgroundColor(Color.rgb(21, 22, 20));
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return true;
            }

            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String path = uri.getPath();
                if ("https".equals(uri.getScheme()) && HOST.equals(uri.getHost())
                        && path != null && path.matches("/(?:fonts/)?[A-Za-z0-9_-]+\\.(html|css|js|ttf)")) {
                    try {
                        InputStream stream = getAssets().open(path.substring(1));
                        String mime = path.endsWith(".js") ? "application/javascript"
                                : path.endsWith(".css") ? "text/css"
                                : path.endsWith(".ttf") ? "font/ttf" : "text/html";
                        return new WebResourceResponse(mime, path.endsWith(".ttf") ? null : "UTF-8", stream);
                    } catch (IOException ignored) { /* Asset missing; return a local 404. */ }
                }
                return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found",
                        Collections.emptyMap(), new ByteArrayInputStream(new byte[0]));
            }
        });
        frame.addView(web);
        setContentView(frame);
        web.loadUrl("https://" + HOST + "/index.html");
    }

    @Override public void onBackPressed() {
        web.evaluateJavascript("Boolean(window.strakerBack && window.strakerBack())", handled -> {
            if (!"true".equals(handled)) finish();
        });
    }

    @Override protected void onDestroy() {
        ((FrameLayout) web.getParent()).removeView(web);
        web.destroy();
        super.onDestroy();
    }
}
