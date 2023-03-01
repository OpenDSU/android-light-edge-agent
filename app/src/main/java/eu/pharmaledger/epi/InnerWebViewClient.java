package eu.pharmaledger.epi;

import static eu.pharmaledger.epi.AppManager.WEBSERVER_RELATIVE_PATH;
import static eu.pharmaledger.epi.FileService.base58Encode;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;

import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.stream.Collectors;

import eu.pharmaledger.epi.requestinspector.RequestInspectorWebViewClient;
import eu.pharmaledger.epi.requestinspector.WebViewRequest;

public class InnerWebViewClient extends RequestInspectorWebViewClient {
    private static final String TAG = InnerWebViewClient.class.getCanonicalName();
    private static final InputStream EMPTY_STREAM = new ByteArrayInputStream(new byte[0]);
    private final int port;
    private final String mainUrl;
    private final AssetManager assetManager;
    private final Resources resources;
    private final ContextWrapper contextWrapper;

    public InnerWebViewClient(WebView webView, int port, String mainUrl, AssetManager assetManager, Resources resources, ContextWrapper contextWrapper) {
        super(webView);
        this.port = port;
        this.mainUrl = mainUrl;
        this.assetManager = assetManager;
        this.resources = resources;
        this.contextWrapper = contextWrapper;
    }

    private static String getMimeType(String url) {
        String type = null;
        String extension = MimeTypeMap.getFileExtensionFromUrl(url);
        if (extension != null) {
            extension = extension.toLowerCase(Locale.getDefault());
            // some extensions are missing from MimeTypeMap
            if (extension.equals("js")) {
                return "text/javascript";
            }

            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        }
        return type;
    }

    @Nullable
    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebViewRequest request) {
        URI url;
        try {
            url = new URL(request.getUrl()).toURI();
        } catch (Exception e) {
            e.printStackTrace();
            return super.shouldInterceptRequest(view, request);
        }

        String requestedPath = url.getPath();
        if (requestedPath != null && requestedPath.equalsIgnoreCase("/getSSIForMainDSU")) {
            String mainDSUSSI = resources.getString(R.string.main_dsu_ssi);
            InputStream inputStream = new ByteArrayInputStream(mainDSUSSI.getBytes(StandardCharsets.UTF_8));
            return new WebResourceResponse("text/javascript", "UTF-8", inputStream);
        }
        if (requestedPath != null && requestedPath.equalsIgnoreCase("/bdns")) {
            String assetFilePath = MessageFormat.format("{0}/external-volume/config/bdns.hosts", WEBSERVER_RELATIVE_PATH);
            try {
                InputStream fileInputStream = assetManager.open(assetFilePath);
                String bdnsContent = new BufferedReader(
                        new InputStreamReader(fileInputStream, StandardCharsets.UTF_8))
                        .lines()
                        .collect(Collectors.joining("\n"));
                bdnsContent = bdnsContent.replaceAll("\\$ORIGIN", (new URL(request.getUrl())).toURI().resolve("/").toString());
                InputStream inputStream = new ByteArrayInputStream(bdnsContent.getBytes(StandardCharsets.UTF_8));

                return new WebResourceResponse("application/json", "UTF-8", inputStream);
            } catch (Exception e) {
                // catch any error and let the request past to apihub
                e.printStackTrace();
            }
        }
        if (requestedPath != null && requestedPath.toLowerCase().startsWith("/versionlessdsu")) {
            String anchorId = requestedPath.substring(requestedPath.indexOf("/", 1) + 1);
            anchorId = base58Encode(anchorId);
            if (request.getMethod().equals("GET")) {
                try {
                    return new WebResourceResponse("text/plain", "UTF-8", contextWrapper.openFileInput(anchorId));
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
            } else if (request.getMethod().equals("PUT")) {
                try (FileOutputStream outputStream = contextWrapper.openFileOutput(anchorId, Context.MODE_PRIVATE)) {
                    outputStream.write(request.getBody().getBytes());
                    return new WebResourceResponse("text/plain", "UTF-8", EMPTY_STREAM);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        if (request.getUrl().equalsIgnoreCase(mainUrl) || isLocalStaticFileRequest(url, request.getMethod())) {
            String filePath = url.getPath();

            if (isRequestToMainIndex(request.getUrl(), filePath)) {
                try {
                    InputStream inputStream = assetManager.open(WEBSERVER_RELATIVE_PATH + "/app/index.html");
                    return new WebResourceResponse(getMimeType("index.html"), "UTF-8", inputStream);
                } catch (Exception e) {
                    // catch any error and let the request past to apihub
                    e.printStackTrace();
                }
            }

            try {
                String assetFilePath = MessageFormat.format("{0}/app{1}", WEBSERVER_RELATIVE_PATH, filePath);
                InputStream inputStream = assetManager.open(assetFilePath);
                return new WebResourceResponse(getMimeType(filePath), "UTF-8", inputStream);
            } catch (Exception e) {
                // catch any error and let the request past to apihub
                Log.e(TAG, "Failed to read file: " + filePath, e);
                e.printStackTrace();
            }
        }

        return super.shouldInterceptRequest(view, request);
    }

    private boolean isRequestToMainIndex(String url, String filePath) {
        return url.equalsIgnoreCase(mainUrl) || filePath.equals("/") || filePath.equalsIgnoreCase("/index.html");
    }

    private boolean isLocalStaticFileRequest(URI url, String method) {
        String scheme = url.getScheme().trim();
        return method.equalsIgnoreCase("GET")
                && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                && url.getHost().equalsIgnoreCase("localhost")
                && (url.getPort() == port || port == 80 && url.getPort() == -1);
    }
}