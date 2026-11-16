package gt.madretierra.mdemt;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Base64;
import android.util.JsonReader;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * MDE MT · Malezas y Madurantes (Ingenio Madre Tierra)
 * Abre la portada https://cobocuburjosue-glitch.github.io/MDE-MT/ ; Dashboard Drones,
 * Calibración de boquillas y Catálogo de equipo se abren dentro de ella.
 * Atiende los dos puentes que ya usan esas apps:
 *   window.AppAndroid  (Vuelos Drones: guardarArchivo, imprimir)
 *   window.AndroidApp  (Catálogo de equipo: guardarPdf)
 */
public class MainActivity extends Activity {

    private static final String HOST = "cobocuburjosue-glitch.github.io";
    private static final String URL_APP = "https://" + HOST + "/MDE-MT/";
    private static final String URL_VERSION = URL_APP + "descargas/version.json";
    private static final int REQ_ARCHIVO = 41, REQ_GPS = 42;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private WebView web, webImpresion;
    private ValueCallback<Uri[]> esperaArchivo;
    private Uri uriCamara;
    private String geoOrigen;
    private GeolocationPermissions.Callback geoCallback;
    private File apkPendiente;
    private AlertDialog dlgDescarga;

    @Override
    protected void onCreate(Bundle estado) {
        super.onCreate(estado);
        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#04080a"));
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setGeolocationEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setUserAgentString(s.getUserAgentString() + " MDEMTApp/" + versionNombre());

        Puente p = new Puente();
        web.addJavascriptInterface(p, "AppAndroid");   // Vuelos Drones
        web.addJavascriptInterface(p, "AndroidApp");   // Catálogo de equipo

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) { return abrirFuera(r.getUrl()); }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r.isForMainFrame()) {
                    v.loadDataWithBaseURL(null,
                        "<html><body style='background:#04080a;color:#fff;font-family:sans-serif;display:flex;align-items:center;justify-content:center;height:100vh;margin:0;text-align:center'>"
                        + "<div><h2>Sin conexión</h2><p style='color:#93aca6'>Revise su internet e intente de nuevo.</p>"
                        + "<button onclick=\"location.href='" + URL_APP + "'\" style='background:#2de2a6;color:#03140f;border:0;border-radius:10px;padding:14px 26px;font-size:16px;font-weight:600'>Reintentar</button></div></body></html>",
                        "text/html", "utf-8", null);
                }
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (esperaArchivo != null) esperaArchivo.onReceiveValue(null);
                esperaArchivo = cb;
                boolean imagen = false;
                for (String t : params.getAcceptTypes()) if (t != null && t.startsWith("image")) imagen = true;
                return imagen ? elegirFoto() : elegirExcel();
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origen, GeolocationPermissions.Callback cb) {
                if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    geoOrigen = origen; geoCallback = cb;
                    requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_GPS);
                } else cb.invoke(origen, true, false);
            }
        });

        web.setDownloadListener((url, agente, disp, tipo, largo) -> { if (url.startsWith("http")) abrirFuera(Uri.parse(url)); });

        if (estado != null) web.restoreState(estado);
        else web.loadUrl(URL_APP);

        buscarActualizacion();
    }

    /** Las páginas de las apps (github.io) se quedan adentro; Drive, correo, etc. se abren afuera. */
    private boolean abrirFuera(Uri u) {
        String esq = u.getScheme();
        if (("https".equals(esq) || "http".equals(esq)) && HOST.equals(u.getHost())) return false;
        try { startActivity(new Intent(Intent.ACTION_VIEW, u)); }
        catch (Exception e) { Toast.makeText(this, "No hay una app para abrir ese enlace.", Toast.LENGTH_SHORT).show(); }
        return true;
    }

    /* ======================= archivos (fotos y Excel) ======================= */

    private boolean elegirFoto() {
        Intent[] extras = new Intent[0];
        uriCamara = null;
        try {
            File dir = new File(getCacheDir(), "fotos");
            if (!dir.exists()) dir.mkdirs();
            File f = File.createTempFile("foto_", ".jpg", dir);
            uriCamara = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
            Intent cam = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            cam.putExtra(MediaStore.EXTRA_OUTPUT, uriCamara);
            cam.setClipData(ClipData.newRawUri("", uriCamara));
            cam.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            extras = new Intent[]{cam};
        } catch (Exception ignored) {}
        Intent gal = new Intent(Intent.ACTION_GET_CONTENT);
        gal.addCategory(Intent.CATEGORY_OPENABLE);
        gal.setType("image/*");
        Intent sel = Intent.createChooser(gal, "Tomar o elegir foto");
        sel.putExtra(Intent.EXTRA_INITIAL_INTENTS, extras);
        return lanzar(sel);
    }

    private boolean elegirExcel() {
        uriCamara = null;
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-excel",
            "text/csv", "text/comma-separated-values", "application/octet-stream"});
        return lanzar(Intent.createChooser(i, "Elegir archivo Excel"));
    }

    private boolean lanzar(Intent i) {
        try { startActivityForResult(i, REQ_ARCHIVO); return true; }
        catch (Exception e) { if (esperaArchivo != null) esperaArchivo.onReceiveValue(null); esperaArchivo = null; return false; }
    }

    @Override
    protected void onActivityResult(int pedido, int res, Intent datos) {
        super.onActivityResult(pedido, res, datos);
        if (pedido != REQ_ARCHIVO || esperaArchivo == null) return;
        Uri[] r = null;
        if (res == RESULT_OK) {
            if (datos != null && datos.getData() != null) r = new Uri[]{datos.getData()};
            else if (uriCamara != null) r = new Uri[]{uriCamara};
        }
        esperaArchivo.onReceiveValue(r);
        esperaArchivo = null;
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (code == REQ_GPS && geoCallback != null) {
            geoCallback.invoke(geoOrigen, res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED, false);
            geoCallback = null;
        }
    }

    /* ======================= puente con las apps ======================= */

    private class Puente {
        @JavascriptInterface public boolean esApp() { return true; }

        /** Vuelos Drones: guarda el Excel en Descargas. */
        @JavascriptInterface
        public void guardarArchivo(String base64, String nombre, String tipo) { ui.post(() -> guardarEnDescargas(base64, nombre, tipo)); }

        /** Catálogo de equipo: guarda el PDF y ofrece abrirlo o compartirlo. */
        @JavascriptInterface
        public void guardarPdf(String base64, String nombre) { ui.post(() -> compartirPdf(base64, nombre)); }

        /** Vuelos Drones: imprime / guarda como PDF el reporte de la app que está abierta. */
        @JavascriptInterface
        public void imprimir(String titulo) { ui.post(() -> imprimirModulo(titulo)); }
    }

    private void guardarEnDescargas(String base64, String nombre, String tipo) {
        try {
            byte[] datos = Base64.decode(base64, Base64.DEFAULT);
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Downloads.DISPLAY_NAME, nombre);
                cv.put(MediaStore.Downloads.MIME_TYPE, tipo);
                cv.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                try (OutputStream o = getContentResolver().openOutputStream(uri)) { o.write(datos); }
                Toast.makeText(this, "Guardado en Descargas: " + nombre, Toast.LENGTH_LONG).show();
            } else {
                File f = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), nombre);
                try (FileOutputStream o = new FileOutputStream(f)) { o.write(datos); }
                Toast.makeText(this, "Guardado en " + f.getAbsolutePath(), Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo guardar: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void compartirPdf(String base64, String nombre) {
        try {
            File dir = new File(getCacheDir(), "reportes");
            if (!dir.exists()) dir.mkdirs();
            File f = new File(dir, nombre.replaceAll("[\\\\/:*?\"<>|]", "-"));
            try (FileOutputStream out = new FileOutputStream(f)) { out.write(Base64.decode(base64, Base64.DEFAULT)); }
            Uri u = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
            Intent ver = new Intent(Intent.ACTION_VIEW).setDataAndType(u, "application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Intent comp = new Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, u).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Intent elegir = Intent.createChooser(comp, "Guardar o enviar reporte");
            elegir.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{ver});
            startActivity(elegir);
        } catch (Exception e) {
            new AlertDialog.Builder(this).setTitle("No se pudo guardar el PDF").setMessage(String.valueOf(e.getMessage())).setPositiveButton("OK", null).show();
        }
    }

    /**
     * Las apps están dentro de la portada (iframe). Para imprimir solo el reporte se copia
     * la página de la app visible (las gráficas se pasan a imagen) a un WebView aparte y se imprime ese.
     */
    private static final String JS_COPIA =
        "(function(){try{" +
        "var f=[].slice.call(document.querySelectorAll('#marcos iframe')).filter(function(x){return x.style.visibility!=='hidden';})[0];" +
        "var d=f?f.contentDocument:document;" +
        "var cs=d.querySelectorAll('canvas'),urls=[];for(var i=0;i<cs.length;i++){try{urls.push(cs[i].toDataURL('image/png'));}catch(e){urls.push('');}}" +
        "var c=d.documentElement.cloneNode(true);" +
        "[].slice.call(c.querySelectorAll('script')).forEach(function(s){s.remove();});" +
        "var cc=c.querySelectorAll('canvas');for(var j=0;j<cc.length;j++){if(!urls[j])continue;var im=d.createElement('img');im.src=urls[j];im.style.cssText=cc[j].style.cssText;im.style.maxWidth='100%';im.className=cc[j].className;cc[j].parentNode.replaceChild(im,cc[j]);}" +
        "[].slice.call(c.querySelectorAll('dialog[open]')).forEach(function(x){x.setAttribute('open','');});" +
        "return JSON.stringify({url:(f?f.contentWindow.location.href:location.href),html:'<!DOCTYPE html>'+c.outerHTML});" +
        "}catch(e){return '';}})()";

    private void imprimirModulo(String titulo) {
        web.evaluateJavascript(JS_COPIA, valor -> {
            try {
                JsonReader jr = new JsonReader(new StringReader(valor));
                jr.setLenient(true);
                String crudo = jr.nextString();
                if (crudo == null || crudo.isEmpty()) { imprimirWeb(web, titulo); return; }
                JSONObject j = new JSONObject(crudo);
                if (webImpresion != null) webImpresion.destroy();
                webImpresion = new WebView(this);
                webImpresion.getSettings().setJavaScriptEnabled(false);
                webImpresion.setWebViewClient(new WebViewClient() {
                    @Override public void onPageFinished(WebView v, String u) { ui.postDelayed(() -> imprimirWeb(v, titulo), 400); }
                });
                webImpresion.loadDataWithBaseURL(j.optString("url", URL_APP), j.getString("html"), "text/html", "utf-8", null);
            } catch (Exception e) {
                imprimirWeb(web, titulo);
            }
        });
    }

    private void imprimirWeb(WebView v, String titulo) {
        try {
            String t = (titulo == null || titulo.isEmpty()) ? "Reporte MDE MT" : titulo;
            PrintManager pm = (PrintManager) getSystemService(PRINT_SERVICE);
            pm.print(t, v.createPrintDocumentAdapter(t), new PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build());
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo abrir la impresión: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /* ======================= actualización automática ======================= */

    private String versionNombre() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception e) { return "1"; }
    }

    private long versionInstalada() {
        try {
            android.content.pm.PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            return Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode;
        } catch (Exception e) { return 0; }
    }

    private void buscarActualizacion() {
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(URL_VERSION + "?t=" + System.currentTimeMillis()).openConnection();
                c.setConnectTimeout(8000); c.setReadTimeout(8000); c.setUseCaches(false);
                if (c.getResponseCode() != 200) return;
                StringBuilder sb = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream()))) { String l; while ((l = br.readLine()) != null) sb.append(l); }
                JSONObject j = new JSONObject(sb.toString());
                long nueva = j.optLong("versionCode", 0);
                String nombre = j.optString("versionName", ""), url = j.optString("url", URL_APP + "descargas/mde-mt.apk");
                if (nueva > versionInstalada()) ui.post(() -> ofrecerActualizacion(nombre, url));
            } catch (Exception ignored) {}
        }).start();
    }

    private void ofrecerActualizacion(String nombre, String url) {
        if (isFinishing()) return;
        new AlertDialog.Builder(this).setTitle("Nueva versión disponible")
            .setMessage("Hay una nueva versión de MDE MT (" + nombre + ").\n\n¿Desea descargarla e instalarla ahora?")
            .setPositiveButton("Actualizar", (d, w) -> descargarApk(url)).setNegativeButton("Más tarde", null).show();
    }

    private void descargarApk(String url) {
        dlgDescarga = new AlertDialog.Builder(this).setTitle("Descargando actualización").setMessage("0 %").setCancelable(false).show();
        new Thread(() -> {
            try {
                File dir = new File(getCacheDir(), "actualizaciones");
                if (!dir.exists()) dir.mkdirs();
                File f = new File(dir, "mde-mt.apk");
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setInstanceFollowRedirects(true); c.setConnectTimeout(15000); c.setReadTimeout(30000);
                long total = c.getContentLengthLong(), leido = 0; int ultimo = -1;
                try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(f)) {
                    byte[] buf = new byte[16384]; int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n); leido += n;
                        if (total > 0) { int pct = (int) (leido * 100 / total); if (pct != ultimo) { ultimo = pct; ui.post(() -> { if (dlgDescarga != null) dlgDescarga.setMessage(pct + " %"); }); } }
                    }
                }
                ui.post(() -> { cerrarDlg(); apkPendiente = f; instalarApk(); });
            } catch (Exception e) {
                ui.post(() -> { cerrarDlg(); new AlertDialog.Builder(this).setTitle("No se pudo descargar").setMessage("Revise su conexión e intente más tarde.").setPositiveButton("OK", null).show(); });
            }
        }).start();
    }

    private void cerrarDlg() { if (dlgDescarga != null) { try { dlgDescarga.dismiss(); } catch (Exception ignored) {} dlgDescarga = null; } }

    private void instalarApk() {
        if (apkPendiente == null) return;
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(this).setTitle("Permiso para instalar")
                .setMessage("Para instalar la actualización, active \"Permitir de esta fuente\" para MDE MT y luego regrese.")
                .setPositiveButton("Ir a ajustes", (d, w) -> startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()))))
                .setNegativeButton("Cancelar", (d, w) -> apkPendiente = null).show();
            return;
        }
        Uri u = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", apkPendiente);
        Intent i = new Intent(Intent.ACTION_VIEW).setDataAndType(u, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        apkPendiente = null;
        try { startActivity(i); } catch (Exception ignored) {}
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (apkPendiente != null && (Build.VERSION.SDK_INT < 26 || getPackageManager().canRequestPackageInstalls())) instalarApk();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); web.saveState(out); }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
}
