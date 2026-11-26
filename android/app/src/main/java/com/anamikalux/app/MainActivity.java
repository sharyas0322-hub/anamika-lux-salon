package com.anamikalux.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int LOCATION_PERMISSION = 4101;
    private static final int FILE_CHOOSER = 4102;
    private static final int INSTALL_PERMISSION = 4103;
    private static final String UPDATE_URL = "https://github.com/sharyas0322-hub/anamika-lux-salon/releases/latest/download/update.json";
    private WebView webView;
    private GeolocationPermissions.Callback pendingGeoCallback;
    private String pendingGeoOrigin;
    private ValueCallback<Uri[]> fileCallback;
    private final ExecutorService updateExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private DownloadManager downloadManager;
    private long activeDownloadId = -1;
    private Uri pendingInstallUri;
    private ProgressDialog downloadProgress;
    private boolean receiverRegistered = false;

    private final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction()) &&
                intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) == activeDownloadId) {
                mainHandler.removeCallbacks(progressUpdater);
                if (downloadProgress != null) downloadProgress.dismiss();
                DownloadManager.Query query = new DownloadManager.Query().setFilterById(activeDownloadId);
                try (Cursor cursor = downloadManager.query(query)) {
                    if (cursor != null && cursor.moveToFirst() &&
                        cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL) {
                        Uri apkUri = downloadManager.getUriForDownloadedFile(activeDownloadId);
                        if (apkUri != null) installDownloadedApk(apkUri);
                        else Toast.makeText(MainActivity.this, "APK download could not be opened.", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(MainActivity.this, "Update download failed. Please try again.", Toast.LENGTH_LONG).show();
                    }
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Unable to verify downloaded update.", Toast.LENGTH_LONG).show();
                }
            }
        }
    };

    private final Runnable progressUpdater = new Runnable() {
        @Override public void run() {
            if (activeDownloadId < 0 || downloadManager == null || downloadProgress == null) return;
            DownloadManager.Query query = new DownloadManager.Query().setFilterById(activeDownloadId);
            try (Cursor cursor = downloadManager.query(query)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                    long downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                    long total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                    if (total > 0) {
                        downloadProgress.setIndeterminate(false);
                        downloadProgress.setMax(100);
                        downloadProgress.setProgress((int) (downloaded * 100 / total));
                        downloadProgress.setMessage("Downloading update… " + (downloaded * 100 / total) + "%");
                    }
                    if (status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PENDING) {
                        mainHandler.postDelayed(this, 500);
                    }
                }
            } catch (Exception ignored) { }
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(android.graphics.Color.rgb(5, 5, 7));
        getWindow().setNavigationBarColor(android.graphics.Color.rgb(5, 5, 7));
        webView = new WebView(this);
        setContentView(webView);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setGeolocationEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        webView.setOverScrollMode(WebView.OVER_SCROLL_NEVER);

        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrl(request.getUrl());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrl(Uri.parse(url));
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    callback.invoke(origin, true, false);
                } else {
                    pendingGeoOrigin = origin;
                    pendingGeoCallback = callback;
                    requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_PERMISSION);
                }
            }
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent intent = params.createIntent();
                try { startActivityForResult(intent, FILE_CHOOSER); }
                catch (ActivityNotFoundException e) { fileCallback = null; Toast.makeText(MainActivity.this, "No file picker found", Toast.LENGTH_SHORT).show(); return false; }
                return true;
            }
        });

        if (savedInstanceState != null) webView.restoreState(savedInstanceState);
        else webView.loadUrl("https://anamika-lux-salon.onrender.com/portal.html");

        downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(downloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(downloadReceiver, filter);
        receiverRegistered = true;
        checkForAppUpdate();
    }

    private void checkForAppUpdate() {
        updateExecutor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(UPDATE_URL).openConnection();
                connection.setConnectTimeout(7000);
                connection.setReadTimeout(7000);
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Cache-Control", "no-cache");
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) return;
                StringBuilder body = new StringBuilder();
                try (InputStream input = connection.getInputStream();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(input))) {
                    String line;
                    while ((line = reader.readLine()) != null) body.append(line);
                }
                JSONObject update = new JSONObject(body.toString());
                int latestCode = update.optInt("versionCode", 0);
                String latestName = update.optString("versionName", "");
                String apkUrl = update.optString("apkUrl", "");
                String notes = update.optString("releaseNotes", "Performance improvements and bug fixes.");
                int installedCode = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
                if (latestCode <= installedCode || apkUrl.isEmpty()) return;
                runOnUiThread(() -> showUpdateDialog(latestName, notes, apkUrl));
            } catch (Exception ignored) {
                // Update checks are best-effort; the app still opens when offline.
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void showUpdateDialog(String version, String notes, String apkUrl) {
        if (isFinishing() || isDestroyed()) return;
        new AlertDialog.Builder(this)
            .setTitle("🎉 Anamika Lux update available")
            .setMessage("Discover the new version " + version + "\n\n" + notes +
                "\n\nDownload the latest version now for the best experience.")
            .setPositiveButton("Update Now", (dialog, which) -> downloadUpdate(apkUrl))
            .setNegativeButton("Not now", (dialog, which) -> dialog.dismiss())
            .setCancelable(true)
            .show();
    }

    private void downloadUpdate(String apkUrl) {
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(apkUrl));
            request.setTitle("Anamika Lux update");
            request.setDescription("Downloading the latest app version…");
            request.setMimeType("application/vnd.android.package-archive");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, "anamika-lux-update.apk");
            activeDownloadId = downloadManager.enqueue(request);
            downloadProgress = new ProgressDialog(this);
            downloadProgress.setTitle("Anamika Lux");
            downloadProgress.setMessage("Preparing update download…");
            downloadProgress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            downloadProgress.setIndeterminate(true);
            downloadProgress.setCancelable(false);
            downloadProgress.show();
            mainHandler.post(progressUpdater);
        } catch (Exception e) {
            Toast.makeText(this, "Could not start update download. Please try again.", Toast.LENGTH_LONG).show();
        }
    }

    private void installDownloadedApk(Uri apkUri) {
        pendingInstallUri = apkUri;
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(this)
                .setTitle("Allow app updates")
                .setMessage("Allow Anamika Lux to open the Android installer so you can update the app.")
                .setPositiveButton("Continue", (dialog, which) -> {
                    Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName()));
                    startActivityForResult(settings, INSTALL_PERMISSION);
                })
                .setNegativeButton("Cancel", null)
                .show();
            return;
        }
        launchInstaller(apkUri);
    }

    private void launchInstaller(Uri apkUri) {
        try {
            Intent install = new Intent(Intent.ACTION_VIEW);
            install.setDataAndType(apkUri, "application/vnd.android.package-archive");
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(install);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Android installer is not available on this device.", Toast.LENGTH_LONG).show();
        }
    }

    private boolean handleUrl(Uri uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme();
        String host = uri.getHost() == null ? "" : uri.getHost();
        if (scheme.equals("https") && (host.equals("anamika-lux-salon.onrender.com") || host.equals("www.google.com") ||
            host.equals("fonts.googleapis.com") || host.equals("fonts.gstatic.com") ||
            host.equals("cdnjs.cloudflare.com") || host.equals("cdn.jsdelivr.net"))) return false;
        if (scheme.equals("http") || scheme.equals("https") || scheme.equals("tel") ||
            scheme.equals("mailto") || scheme.equals("sms") || scheme.equals("geo") ||
            scheme.equals("market") || scheme.equals("whatsapp")) {
            try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
            catch (ActivityNotFoundException e) { Toast.makeText(this, "App not available to open this link", Toast.LENGTH_SHORT).show(); }
            return true;
        }
        return true;
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION && pendingGeoCallback != null) {
            boolean granted = false;
            for (int result : grantResults) if (result == PackageManager.PERMISSION_GRANTED) granted = true;
            pendingGeoCallback.invoke(pendingGeoOrigin, granted, false);
            pendingGeoCallback = null;
            pendingGeoOrigin = null;
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER && fileCallback != null) {
            Uri[] results = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            fileCallback.onReceiveValue(results);
            fileCallback = null;
        } else if (requestCode == INSTALL_PERMISSION && pendingInstallUri != null) {
            if (Build.VERSION.SDK_INT < 26 || getPackageManager().canRequestPackageInstalls()) launchInstaller(pendingInstallUri);
            else Toast.makeText(this, "Update installation permission was not enabled.", Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override protected void onPause() {
        if (webView != null) webView.onPause();
        CookieManager.getInstance().flush();
        super.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
    }

    @Override protected void onDestroy() {
        updateExecutor.shutdownNow();
        mainHandler.removeCallbacks(progressUpdater);
        if (receiverRegistered) unregisterReceiver(downloadReceiver);
        if (webView != null) { webView.stopLoading(); webView.destroy(); }
        super.onDestroy();
    }
}
