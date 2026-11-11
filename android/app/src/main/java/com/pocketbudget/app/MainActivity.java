package com.pocketbudget.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Pocket Budget: shows the budget web app full screen.
 * The app marks itself with "PocketBudgetApp" in its user agent, which is what lets
 * this phone create and edit its own budget. Anyone else only gets the view-only page.
 */
public class MainActivity extends Activity {

    private WebView web;
    private String appUrl;
    private int insetTop = 0, insetBottom = 0;
    private boolean darkBars = false;
    private boolean navHidden = false;

    // Save page: Export writes a file where you choose, Import reads one you pick.
    private static final int REQ_EXPORT = 41, REQ_IMPORT = 42;
    private static final int MAX_IMPORT_BYTES = 20 * 1024 * 1024;
    private String pendingExport;

    private static final String OFFLINE_PAGE =
        "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head>" +
        "<body style='margin:0;font-family:sans-serif;background:#EEF1F4;color:#15212C;display:flex;" +
        "align-items:center;justify-content:center;height:100vh;text-align:center;padding:24px;box-sizing:border-box'>" +
        "<div><h2 style='margin:0 0 8px'>No internet connection</h2>" +
        "<p style='color:#5D6B78;margin:0 0 20px'>Pocket Budget needs the internet to save and sync your budget.</p>" +
        "<button onclick='Android.retry()' style='background:#1F5F8B;color:#fff;border:0;border-radius:10px;" +
        "padding:12px 22px;font-size:16px;font-weight:bold'>Try again</button></div></body></html>";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        appUrl = getString(R.string.app_url);

        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#EEF1F4"));
        setContentView(web);
        // After setContentView, so the window theme can't put the white layer back.
        goEdgeToEdge();

        // Tell the page how tall the status bar and the bottom button bar are,
        // so it can draw behind them but keep its content clear of them.
        web.setOnApplyWindowInsetsListener((v, insets) -> {
            insetTop = insets.getSystemWindowInsetTop();
            insetBottom = insets.getSystemWindowInsetBottom();
            pushInsets();
            return insets;
        });

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setUserAgentString(s.getUserAgentString() + " PocketBudgetApp/1.0");
        CookieManager.getInstance().setAcceptCookie(true);

        web.addJavascriptInterface(new Bridge(), "Android");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                Uri home = Uri.parse(appUrl);
                // Stay inside the app for our own site; open everything else in the phone's browser.
                if (uri.getHost() != null && uri.getHost().equals(home.getHost())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (Exception ignored) { }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                pushInsets();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    view.loadDataWithBaseURL(null, OFFLINE_PAGE, "text/html", "utf-8", null);
                }
            }
        });

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
        } else {
            web.loadUrl(appUrl);
        }
    }

    /** Let the page show through a clear status bar and a clear bottom button bar. */
    @SuppressWarnings("deprecation")
    private void goEdgeToEdge() {
        Window w = getWindow();
        w.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
            | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 28) w.setNavigationBarDividerColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 29) {
            w.setNavigationBarContrastEnforced(false);
            w.setStatusBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false);
        } else {
            w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
        applyBarIcons();
    }

    /** Dark icons on the light theme, light icons on the dark theme, so the buttons stay visible. */
    @SuppressWarnings("deprecation")
    private void applyBarIcons() {
        Window w = getWindow();
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = w.getInsetsController();
            if (c == null) return;
            int light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            c.setSystemBarsAppearance(darkBars ? 0 : light, light);
        } else {
            View d = w.getDecorView();
            int flags = d.getSystemUiVisibility();
            int light = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            d.setSystemUiVisibility(darkBars ? (flags & ~light) : (flags | light));
        }
    }

    /** Slide the bottom button bar away while scrolling, and back at the top. */
    @SuppressWarnings("deprecation")
    private void applyNavHidden() {
        Window w = getWindow();
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = w.getInsetsController();
            if (c == null) return;
            c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            if (navHidden) c.hide(WindowInsets.Type.navigationBars());
            else c.show(WindowInsets.Type.navigationBars());
        } else {
            View d = w.getDecorView();
            int flags = d.getSystemUiVisibility();
            int hide = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            d.setSystemUiVisibility(navHidden ? (flags | hide) : (flags & ~hide));
        }
    }

    private void pushInsets() {
        if (web == null) return;
        float density = getResources().getDisplayMetrics().density;
        int top = Math.round(insetTop / density), bottom = Math.round(insetBottom / density);
        web.evaluateJavascript("(function(){var r=document.documentElement;if(!r)return;"
            + "r.style.setProperty('--sat','" + top + "px');"
            + "r.style.setProperty('--sab','" + bottom + "px');})()", null);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        goEdgeToEdge();
        applyNavHidden();
        // Wake the live connection back up so saved changes reach viewers right away.
        if (web != null) web.evaluateJavascript("window.reconnect&&window.reconnect()", null);
    }

    @Override
    protected void onPause() {
        super.onPause();
        CookieManager.getInstance().flush();
    }

    /** Answers from Android's "Save to" and file-picker screens. */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Uri uri = (resultCode == RESULT_OK && data != null) ? data.getData() : null;
        if (requestCode == REQ_EXPORT) {
            String text = pendingExport;
            pendingExport = null;
            String result = "cancel";
            if (uri != null && text != null) {
                try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
                    out.write(text.getBytes(StandardCharsets.UTF_8));
                    result = "ok";
                } catch (Exception e) {
                    result = "error";
                }
            }
            web.evaluateJavascript("window.backupSaved&&window.backupSaved('" + result + "')", null);
        } else if (requestCode == REQ_IMPORT) {
            String text = null;
            if (uri != null) {
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    ByteArrayOutputStream buf = new ByteArrayOutputStream();
                    byte[] chunk = new byte[16384];
                    int n;
                    while ((n = in.read(chunk)) > 0) {
                        buf.write(chunk, 0, n);
                        if (buf.size() > MAX_IMPORT_BYTES) throw new Exception("too big");
                    }
                    text = buf.toString("UTF-8");
                } catch (Exception e) {
                    text = "";  // the page reports it isn't a save file
                }
            }
            String arg = text == null ? "null" : JSONObject.quote(text);
            web.evaluateJavascript("window.backupPicked&&window.backupPicked(" + arg + ")", null);
        }
    }

    /** Functions the web page can call as window.Android.* */
    private class Bridge {
        /** Export: opens Android's "Save to" screen so you choose the folder and file name. */
        @JavascriptInterface
        public void saveFile(String name, String text) {
            runOnUiThread(() -> {
                pendingExport = text;
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("application/json");
                i.putExtra(Intent.EXTRA_TITLE, name);
                try {
                    startActivityForResult(i, REQ_EXPORT);
                } catch (Exception e) {
                    pendingExport = null;
                    web.evaluateJavascript("window.backupSaved&&window.backupSaved('error')", null);
                }
            });
        }

        /** Import: opens the file picker; the file's text goes back to window.backupPicked. */
        @JavascriptInterface
        public void openFile() {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");  // some file apps don't label .json files, so don't filter by type
                try {
                    startActivityForResult(i, REQ_IMPORT);
                } catch (Exception ignored) { }
            });
        }

        @JavascriptInterface
        public void share(String text) {
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_TEXT, text);
            runOnUiThread(() -> startActivity(Intent.createChooser(send, "Share your budget")));
        }

        @JavascriptInterface
        public void setDarkBars(boolean dark) {
            runOnUiThread(() -> { darkBars = dark; applyBarIcons(); });
            BudgetWidget.setDark(getApplicationContext(), dark);
        }

        @JavascriptInterface
        public void setNavHidden(boolean hidden) {
            runOnUiThread(() -> {
                if (navHidden == hidden) return;
                navHidden = hidden;
                applyNavHidden();
            });
        }

        /** The app sends this month's numbers so the home-screen widget can show them. */
        @JavascriptInterface
        public void updateWidget(String json) {
            BudgetWidget.saveAndRefresh(getApplicationContext(), json);
        }

        @JavascriptInterface
        public void retry() {
            runOnUiThread(() -> web.loadUrl(appUrl));
        }
    }
}
