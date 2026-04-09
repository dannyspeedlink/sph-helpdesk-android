package ng.edu.uniport.sph.helpdesk;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JsResult;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

public class MainActivity extends Activity {

    private static final String HELPDESK_URL = "https://helpdesk.uphsph.edu.ng";
    private static final int FILE_CHOOSER_REQUEST = 100;

    private WebView webView;
    private ProgressBar progressBar;
    private LinearLayout offlineLayout;
    private ValueCallback<Uri[]> filePathCallback;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ── Root layout ───────────────────────────────────────
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#002464"));

        // ── Progress bar ──────────────────────────────────────
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(
            Color.parseColor("#E8A020")));
        progressBar.setBackgroundColor(Color.parseColor("#003B8E"));
        FrameLayout.LayoutParams pbParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, 6);
        pbParams.gravity = android.view.Gravity.TOP;
        root.addView(progressBar, pbParams);

        // ── WebView ───────────────────────────────────────────
        webView = new WebView(this);
        FrameLayout.LayoutParams wvParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT);
        root.addView(webView, wvParams);

        // ── Offline screen ────────────────────────────────────
        offlineLayout = buildOfflineLayout();
        root.addView(offlineLayout, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));
        offlineLayout.setVisibility(View.GONE);

        setContentView(root);

        // ── WebView settings ──────────────────────────────────
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setAllowFileAccess(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(false);

        // Enable cookies
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        // ── WebViewClient ─────────────────────────────────────
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                // Keep helpdesk URLs in-app; open external links in browser
                if (url.startsWith(HELPDESK_URL) || url.startsWith("javascript:")) {
                    return false;
                }
                if (url.startsWith("mailto:")) {
                    startActivity(new Intent(Intent.ACTION_SENDTO, Uri.parse(url)));
                    return true;
                }
                if (url.startsWith("tel:") || url.startsWith("whatsapp:")) {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    return true;
                }
                // Open other URLs in browser
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                progressBar.setVisibility(View.VISIBLE);
                offlineLayout.setVisibility(View.GONE);
                webView.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);
                CookieManager.getInstance().flush();

                // Inject FCM token registration on every page load
                // This ensures it fires after login when session is active
                String fcmToken = getSharedPreferences("sph_prefs", MODE_PRIVATE)
                    .getString("fcm_token", null);
                if (fcmToken != null && url.contains("helpdesk.uphsph.edu.ng")) {
                    String js = "javascript:(function(){"
                        + "fetch('/fcm_register.php',{"
                        + "method:'POST',"
                        + "headers:{'Content-Type':'application/json'},"
                        + "body:JSON.stringify({fcm_token:'" + fcmToken + "',device_info:'Android App'})"
                        + "}).catch(function(){});"
                        + "})();";
                    view.loadUrl(js);
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                                        WebResourceError error) {
                if (request.isForMainFrame()) {
                    webView.setVisibility(View.GONE);
                    offlineLayout.setVisibility(View.VISIBLE);
                }
            }
        });

        // ── WebChromeClient (progress + file upload) ──────────
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                if (newProgress == 100) progressBar.setVisibility(View.GONE);
            }

            @Override
            public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                result.confirm();
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
                new android.app.AlertDialog.Builder(MainActivity.this)
                    .setMessage(message)
                    .setPositiveButton("OK", (d, w) -> result.confirm())
                    .setNegativeButton("Cancel", (d, w) -> result.cancel())
                    .show();
                return true;
            }

            // File picker for ticket attachments
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                filePathCallback = callback;
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                String[] mimeTypes = {"image/*", "application/pdf",
                    "application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"};
                intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
                startActivityForResult(Intent.createChooser(intent, "Choose File"),
                    FILE_CHOOSER_REQUEST);
                return true;
            }
        });

        // ── Load the helpdesk ─────────────────────────────────
        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            // Check if launched from a notification with a specific URL
            String notifUrl = getIntent().getStringExtra("url");
            if (notifUrl != null && !notifUrl.isEmpty()) {
                webView.loadUrl(notifUrl);
            } else {
                webView.loadUrl(HELPDESK_URL);
            }
        }

        // ── Register FCM token with server after page loads ───
        String fcmToken = getSharedPreferences("sph_prefs", MODE_PRIVATE)
            .getString("fcm_token", null);
        if (fcmToken != null) {
            SPHFirebaseService.registerTokenWithServer(fcmToken);
        }
    }

    // ── Build offline screen ──────────────────────────────────
    private LinearLayout buildOfflineLayout() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(android.view.Gravity.CENTER);
        layout.setBackgroundColor(Color.parseColor("#002464"));
        layout.setPadding(64, 64, 64, 64);

        TextView icon = new TextView(this);
        icon.setText("📡");
        icon.setTextSize(56);
        icon.setGravity(android.view.Gravity.CENTER);

        TextView title = new TextView(this);
        title.setText("You're Offline");
        title.setTextColor(Color.parseColor("#FFD060"));
        title.setTextSize(24);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.setMargins(0, 24, 0, 12);
        title.setLayoutParams(titleParams);

        TextView msg = new TextView(this);
        msg.setText("Please check your internet connection and try again.");
        msg.setTextColor(Color.parseColor("#AABBDD"));
        msg.setTextSize(15);
        msg.setGravity(android.view.Gravity.CENTER);
        msg.setLineSpacing(8, 1);

        android.widget.Button retryBtn = new android.widget.Button(this);
        retryBtn.setText("Try Again");
        retryBtn.setBackgroundColor(Color.parseColor("#E8A020"));
        retryBtn.setTextColor(Color.parseColor("#002464"));
        retryBtn.setTextSize(15);
        retryBtn.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        btnParams.setMargins(0, 36, 0, 0);
        btnParams.gravity = android.view.Gravity.CENTER;
        retryBtn.setLayoutParams(btnParams);
        retryBtn.setOnClickListener(v -> {
            offlineLayout.setVisibility(View.GONE);
            webView.setVisibility(View.VISIBLE);
            webView.reload();
        });

        layout.addView(icon);
        layout.addView(title);
        layout.addView(msg);
        layout.addView(retryBtn);
        return layout;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CHOOSER_REQUEST) {
            Uri[] results = null;
            if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
                results = new Uri[]{data.getData()};
            }
            if (filePathCallback != null) {
                filePathCallback.onReceiveValue(results);
                filePathCallback = null;
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }
}
