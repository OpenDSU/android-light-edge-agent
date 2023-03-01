package eu.pharmaledger.epi;

import static eu.pharmaledger.epi.AppManager.WEBSERVER_RELATIVE_PATH;
import static eu.pharmaledger.epi.FileService.base58Encode;

import android.content.Context;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.KeyEvent;
import android.webkit.WebView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getCanonicalName();

    private static final int NODE_PORT = 80;

    private final FileService fileService = new FileService();

    private WebView myWebView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AppManager appManager = new AppManager(MainActivity.this, getApplicationContext(), getResources());
        ChromeService chromeService = new ChromeService(getPackageManager(), MainActivity.this);

        setContentView(R.layout.activity_main);

        String mainDSUPath = base58Encode(getResources().getString(R.string.main_dsu_path));
        File file = new File(getFilesDir(), mainDSUPath);
        if (!file.exists()) {
            Log.i(TAG,"MainDSU file doesn't exist: " + mainDSUPath);
            // create versionless DSU for mainDSU
            try (FileOutputStream outputStream = openFileOutput(mainDSUPath, Context.MODE_PRIVATE)) {
                String assetFilePath = MessageFormat.format("{0}/environment.json", WEBSERVER_RELATIVE_PATH);
                String environmentJsonContent = fileService.getAssetFileContent(getAssets(), assetFilePath);
                Log.i(TAG,"Loaded environment json: " + environmentJsonContent);

                byte[] environmentJsonBytes = environmentJsonContent.getBytes(StandardCharsets.UTF_8);
                String environmentJsonBase64 = Base64.encodeToString(environmentJsonBytes, Base64.NO_WRAP);

                String initialContent = String.format(
                        "{\"folders\":{},\"files\":{\"environment.json\":{\"content\":\"%s\"}},\"mounts\":[]}",
                        environmentJsonBase64);
                Log.i(TAG,"Storing MainDSU versionlessdsu content: " + initialContent);
                outputStream.write(initialContent.getBytes());
            } catch (Exception e) {
                Log.e(TAG, "Failed to generate versionlessDSU for mainDSU:" + e.getMessage(), e);
                e.printStackTrace();
            }
        }

        if (!chromeService.isChromeVersionOK()) {
            chromeService.showWarning();
        } else {
            Log.i(TAG, "Free port is" + NODE_PORT);

            String mainUrl = appManager.getMainUrl(NODE_PORT);

            myWebView = findViewById(R.id.myWebView);
            appManager.initialiseWebView(myWebView, NODE_PORT, mainUrl, getAssets(), getResources(), this);

            Log.i(TAG, "Running onCreate(...)");

            loadPage(mainUrl);
        }
    }

    /**
     * Loads the page of the web app
     *
     * @param mainUrl
     */
    void loadPage(String mainUrl) {
        runOnUiThread(
                new Runnable() {
                    @Override
                    public void run() {
                        myWebView.loadUrl(mainUrl);
                    }
                }
        );
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        final WebView myWebView = findViewById(R.id.myWebView);

        if ((keyCode == KeyEvent.KEYCODE_BACK) && myWebView.canGoBack()) {
            myWebView.goBack();
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }
}
