package org.fdroid.fdroid.installer;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;

import org.fdroid.fdroid.Utils;
import org.fdroid.fdroid.data.Apk;
import org.fdroid.fdroid.data.App;
import org.fdroid.fdroid.net.Downloader;
import org.fdroid.fdroid.net.DownloaderService;

import java.io.File;
import java.util.HashMap;

/**
 * Manages the whole process when a background update triggers an install or the user
 * requests an APK to be installed.  It handles checking whether the APK is cached,
 * downloading it, putting up and maintaining a {@link Notification}, and more.
 * <p>
 * Data is sent via {@link Intent}s so that Android handles the message queuing
 * and {@link Service} lifecycle for us, although it adds one layer of redirection
 * between the static method to send the {@code Intent} and the method to
 * actually process it.
 * <p>
 * The full URL for the APK file to download is also used as the unique ID to
 * represent the download itself throughout F-Droid.  This follows the model
 * of {@link Intent#setData(Uri)}, where the core data of an {@code Intent} is
 * a {@code Uri}.  The full download URL is guaranteed to be unique since it
 * points to files on a filesystem, where there cannot be multiple files with
 * the same name.  This provides a unique ID beyond just {@code packageName}
 * and {@code versionCode} since there could be different copies of the same
 * APK on different servers, signed by different keys, or even different builds.
 * <p><ul>
 * <li>for a {@link Uri} ID, use {@code Uri}, {@link Intent#getData()}
 * <li>for a {@code String} ID, use {@code urlString}, {@link Uri#toString()}, or
 * {@link Intent#getDataString()}
 * <li>for an {@code int} ID, use {@link String#hashCode()}
 * </ul></p>
 */
public class InstallManagerService extends Service {
    public static final String TAG = "InstallManagerService";

    private static final String ACTION_INSTALL = "org.fdroid.fdroid.InstallManagerService.action.INSTALL";

    /**
     * The collection of APKs that are actively going through this whole process.
     */
    private static final HashMap<String, Apk> ACTIVE_APKS = new HashMap<String, Apk>(3);

    private LocalBroadcastManager localBroadcastManager;

    /**
     * This service does not use binding, so no need to implement this method
     */
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Utils.debugLog(TAG, "creating Service");
        localBroadcastManager = LocalBroadcastManager.getInstance(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Utils.debugLog(TAG, "onStartCommand " + intent);
        String urlString = intent.getDataString();
        Apk apk = ACTIVE_APKS.get(urlString);
        File apkFilePath = Utils.getApkDownloadPath(this, intent.getData());
        long apkFileSize = apkFilePath.length();
        if (!apkFilePath.exists() || apkFileSize < apk.size) {
            Utils.debugLog(TAG, "download " + urlString + " " + apkFilePath);
            DownloaderService.queue(this, apk.packageName, urlString);
        } else if (apkFileSize == apk.size) {
            Utils.debugLog(TAG, "skip download, we have it, straight to install " + urlString + " " + apkFilePath);
            sendBroadcast(intent.getData(), Downloader.ACTION_STARTED, apkFilePath);
            sendBroadcast(intent.getData(), Downloader.ACTION_COMPLETE, apkFilePath);
        } else {
            Utils.debugLog(TAG, " delete and download again " + urlString + " " + apkFilePath);
            apkFilePath.delete();
            DownloaderService.queue(this, apk.packageName, urlString);
        }
        return START_REDELIVER_INTENT; // if killed before completion, retry Intent
    }

    private void sendBroadcast(Uri uri, String action, File file) {
        Intent intent = new Intent(action);
        intent.setData(uri);
        intent.putExtra(Downloader.EXTRA_DOWNLOAD_PATH, file.getAbsolutePath());
        localBroadcastManager.sendBroadcast(intent);
    }

    /**
     * Install an APK, checking the cache and downloading if necessary before starting the process.
     * All notifications are sent as an {@link Intent} via local broadcasts to be received by
     *
     * @param context this app's {@link Context}
     */
    public static void queue(Context context, App app, Apk apk) {
        String urlString = apk.getUrl();
        Utils.debugLog(TAG, "queue " + app.packageName + " " + apk.versionCode + " from " + urlString);
        ACTIVE_APKS.put(urlString, apk);
        Intent intent = new Intent(context, InstallManagerService.class);
        intent.setAction(ACTION_INSTALL);
        intent.setData(Uri.parse(urlString));
        context.startService(intent);
    }
}
