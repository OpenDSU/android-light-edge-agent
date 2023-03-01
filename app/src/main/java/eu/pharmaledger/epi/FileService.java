package eu.pharmaledger.epi;

import static eu.pharmaledger.epi.AppManager.NODEJS_PROJECT_FOLDER_NAME;
import static eu.pharmaledger.epi.AppManager.WEBSERVER_FOLDER_NAME;
import static eu.pharmaledger.epi.AppManager.WEBSERVER_RELATIVE_PATH;

import android.content.res.AssetManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class FileService {
    private static final String TAG = FileService.class.getCanonicalName();
    private static final char[] ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray();

    /**
     * Encodes the given bytes in base58. No checksum is appended.
     */
    public static String base58Encode(String stringToEncode) {
        byte[] input = stringToEncode.getBytes();
        if (input.length == 0) {
            return "";
        }
        input = copyOfRange(input, 0, input.length);
        // Count leading zeroes.
        int zeroCount = 0;
        while (zeroCount < input.length && input[zeroCount] == 0) {
            ++zeroCount;
        }
        // The actual encoding.
        byte[] temp = new byte[input.length * 2];
        int j = temp.length;

        int startAt = zeroCount;
        while (startAt < input.length) {
            byte mod = divmod58(input, startAt);
            if (input[startAt] == 0) {
                ++startAt;
            }
            temp[--j] = (byte) ALPHABET[mod];
        }

        // Strip extra '1' if there are some after decoding.
        while (j < temp.length && temp[j] == ALPHABET[0]) {
            ++j;
        }
        // Add as many leading '1' as there were leading zeros.
        while (--zeroCount >= 0) {
            temp[--j] = (byte) ALPHABET[0];
        }

        byte[] output = copyOfRange(temp, j, temp.length);
        return new String(output, StandardCharsets.US_ASCII);
    }

    private static byte divmod58(byte[] number, int startAt) {
        int remainder = 0;
        for (int i = startAt; i < number.length; i++) {
            int digit256 = (int) number[i] & 0xFF;
            int temp = remainder * 256 + digit256;

            number[i] = (byte) (temp / 58);

            remainder = temp % 58;
        }

        return (byte) remainder;
    }

    private static byte[] copyOfRange(byte[] source, int from, int to) {
        byte[] range = new byte[to - from];
        System.arraycopy(source, from, range, 0, range.length);

        return range;
    }

    public void copyApplicationAssets(AssetManager assets, String nodeJsFolderPath) {
        String webserverFolderPath = nodeJsFolderPath + "/" + WEBSERVER_FOLDER_NAME;

        try {
            String[] rootFiles = assets.list(NODEJS_PROJECT_FOLDER_NAME);
            for (String rootFile : rootFiles) {
                if (!rootFile.equalsIgnoreCase(WEBSERVER_FOLDER_NAME)) {
                    copyAssetFolder(assets, NODEJS_PROJECT_FOLDER_NAME + "/" + rootFile, nodeJsFolderPath + "/" + rootFile);
                }
            }

            new File(webserverFolderPath).mkdirs();
            String[] apihubRootFiles = assets.list(WEBSERVER_RELATIVE_PATH);
            for (String apihubRootFile : apihubRootFiles) {
                if (!apihubRootFile.equalsIgnoreCase("app")) {
                    String relativeFilePath = WEBSERVER_RELATIVE_PATH + "/" + apihubRootFile;
                    String[] folderFiles = assets.list(relativeFilePath);
                    if (folderFiles.length != 0) {
                        // current entry is a folder
                        new File(webserverFolderPath + "/" + apihubRootFile).mkdirs();
                    }

                    copyAssetFolder(assets, relativeFilePath, webserverFolderPath + "/" + apihubRootFile);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to copy assets folder", e);
            throw new RuntimeException(e);
        }
    }

    public String getAssetFileContent(AssetManager assets, String filePath) {
        StringBuilder sb = new StringBuilder();
        String str;
        try (InputStream is = assets.open(filePath); BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            while ((str = br.readLine()) != null) {
                sb.append(str);
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private boolean copyAssetFolder(AssetManager assetManager, String fromAssetPath, String toPath) {
        Log.d(TAG, "copyAssetFolder(): Copy asset from " + fromAssetPath + " to " + toPath);
        try {
            String[] files = assetManager.list(fromAssetPath);
            boolean res = true;

            if (files.length == 0) {
                //If it's a file, it won't have any assets "inside" it.
                res &= copyAsset(assetManager,
                        fromAssetPath,
                        toPath);
            } else {
                new File(toPath).mkdirs();
                for (String file : files)
                    res &= copyAssetFolder(assetManager,
                            fromAssetPath + "/" + file,
                            toPath + "/" + file);
            }
            return res;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean copyAsset(AssetManager assetManager, String fromAssetPath, String toPath) {
        try {
            File destFile = new File(toPath);
            destFile.createNewFile();
            InputStream in = assetManager.open(fromAssetPath);
            OutputStream out = new FileOutputStream(toPath);
            copyFile(in, out);
            in.close();
            out.flush();
            out.close();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }
}
