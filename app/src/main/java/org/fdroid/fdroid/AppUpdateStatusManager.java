package org.fdroid.fdroid;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.content.LocalBroadcastManager;

import org.fdroid.fdroid.data.Apk;
import org.fdroid.fdroid.data.App;
import org.fdroid.fdroid.data.AppProvider;
import org.fdroid.fdroid.installer.ErrorDialogActivity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Manages the state of APKs that are being installed or that have updates available.
 * <p>
 * The full URL for the APK file to download is used as the unique ID to
 * represent the status of the APK throughout F-Droid. The full download URL is guaranteed
 * to be unique since it points to files on a filesystem, where there cannot be multiple files with
 * the same name.  This provides a unique ID beyond just {@code packageName}
 * and {@code versionCode} since there could be different copies of the same
 * APK on different servers, signed by different keys, or even different builds.
 */
public final class AppUpdateStatusManager {

    /**
     * Broadcast when:
     *  * The user clears the list of installed apps from notification manager.
     *  * The user clears the list of apps available to update from the notification manager.
     *  * A repo update is completed and a bunch of new apps are ready to be updated.
     *  * F-Droid is opened, and it finds a bunch of .apk files downloaded and ready to install.
     */
    public static final String BROADCAST_APPSTATUS_LIST_CHANGED = "org.fdroid.fdroid.installer.appstatus.listchange";

    /**
     * Broadcast when an app begins the download/install process (either manually or via an automatic download).
     */
    public static final String BROADCAST_APPSTATUS_ADDED = "org.fdroid.fdroid.installer.appstatus.appchange.add";

    /**
     * When the {@link AppUpdateStatus#status} of an app changes or the download progress for an app advances.
     */
    public static final String BROADCAST_APPSTATUS_CHANGED = "org.fdroid.fdroid.installer.appstatus.appchange.change";

    /**
     * Broadcast when:
     *  * The associated app has the {@link Status#Installed} status, and the user either visits
     *    that apps details page or clears the individual notification for the app.
     *  * The download for an app is cancelled.
     */
    public static final String BROADCAST_APPSTATUS_REMOVED = "org.fdroid.fdroid.installer.appstatus.appchange.remove";

    public static final String EXTRA_APK_URL = "urlstring";

    public static final String EXTRA_REASON_FOR_CHANGE = "reason";

    public static final String REASON_READY_TO_INSTALL = "readytoinstall";
    public static final String REASON_UPDATES_AVAILABLE = "updatesavailable";
    public static final String REASON_CLEAR_ALL_UPDATES = "clearallupdates";
    public static final String REASON_CLEAR_ALL_INSTALLED = "clearallinstalled";

    /**
     * If this is present and true, then the broadcast has been sent in response to the {@link AppUpdateStatus#status}
     * changing. In comparison, if it is just the download progress of an app then this should not be true.
     */
    public static final String EXTRA_IS_STATUS_UPDATE = "isstatusupdate";

    private static final String LOGTAG = "AppUpdateStatusManager";

    public enum Status {
        Unknown,
        UpdateAvailable,
        Downloading,
        ReadyToInstall,
        Installing,
        Installed,
        InstallError
    }

    public static AppUpdateStatusManager getInstance(Context context) {
        if (instance == null) {
            instance = new AppUpdateStatusManager(context.getApplicationContext());
        }
        return instance;
    }

    private static AppUpdateStatusManager instance;

    public class AppUpdateStatus {
        public final App app;
        public final Apk apk;
        public Status status;
        public PendingIntent intent;
        public int progressCurrent;
        public int progressMax;
        public String errorText;

        AppUpdateStatus(App app, Apk apk, Status status, PendingIntent intent) {
            this.app = app;
            this.apk = apk;
            this.status = status;
            this.intent = intent;
        }

        public String getUniqueKey() {
            return apk.getUrl();
        }
    }

    private final Context context;
    private final LocalBroadcastManager localBroadcastManager;
    private final HashMap<String, AppUpdateStatus> appMapping = new HashMap<>();
    private boolean isBatchUpdating;

    private AppUpdateStatusManager(Context context) {
        this.context = context;
        localBroadcastManager = LocalBroadcastManager.getInstance(context.getApplicationContext());
    }

    @Nullable
    public AppUpdateStatus get(String key) {
        synchronized (appMapping) {
            return appMapping.get(key);
        }
    }

    public Collection<AppUpdateStatus> getAll() {
        synchronized (appMapping) {
            return appMapping.values();
        }
    }

    /**
     * Get all entries associated with a package name. There may be several.
     * @param packageName Package name of the app
     * @return A list of entries, or an empty list
     */
    public Collection<AppUpdateStatus> getByPackageName(String packageName) {
        ArrayList<AppUpdateStatus> returnValues = new ArrayList<>();
        synchronized (appMapping) {
            for (AppUpdateStatus entry : appMapping.values()) {
                if (entry.apk.packageName.equalsIgnoreCase(packageName)) {
                    returnValues.add(entry);
                }
            }
        }
        return returnValues;
    }

    private void updateApkInternal(@NonNull AppUpdateStatus entry, @NonNull Status status, PendingIntent intent) {
        Utils.debugLog(LOGTAG, "Update APK " + entry.apk.apkName + " state to " + status.name());
        boolean isStatusUpdate = entry.status != status;
        entry.status = status;
        entry.intent = intent;
        // If intent not set, see if we need to create a default intent
        if (entry.intent == null) {
            entry.intent = getContentIntent(entry);
        }
        notifyChange(entry, isStatusUpdate);
    }

    private void addApkInternal(@NonNull Apk apk, @NonNull Status status, PendingIntent intent) {
        Utils.debugLog(LOGTAG, "Add APK " + apk.apkName + " with state " + status.name());
        AppUpdateStatus entry = createAppEntry(apk, status, intent);
        // If intent not set, see if we need to create a default intent
        if (entry.intent == null) {
            entry.intent = getContentIntent(entry);
        }
        appMapping.put(entry.getUniqueKey(), entry);
        notifyAdd(entry);
    }

    private void notifyChange(String reason) {
        if (!isBatchUpdating) {
            Intent intent = new Intent(BROADCAST_APPSTATUS_LIST_CHANGED);
            intent.putExtra(EXTRA_REASON_FOR_CHANGE, reason);
            localBroadcastManager.sendBroadcast(intent);
        }
    }

    private void notifyAdd(AppUpdateStatus entry) {
        if (!isBatchUpdating) {
            Intent broadcastIntent = new Intent(BROADCAST_APPSTATUS_ADDED);
            broadcastIntent.putExtra(EXTRA_APK_URL, entry.getUniqueKey());
            localBroadcastManager.sendBroadcast(broadcastIntent);
        }
    }

    private void notifyChange(AppUpdateStatus entry, boolean isStatusUpdate) {
        if (!isBatchUpdating) {
            Intent broadcastIntent = new Intent(BROADCAST_APPSTATUS_CHANGED);
            broadcastIntent.putExtra(EXTRA_APK_URL, entry.getUniqueKey());
            broadcastIntent.putExtra(EXTRA_IS_STATUS_UPDATE, isStatusUpdate);
            localBroadcastManager.sendBroadcast(broadcastIntent);
        }
    }

    private void notifyRemove(AppUpdateStatus entry) {
        if (!isBatchUpdating) {
            Intent broadcastIntent = new Intent(BROADCAST_APPSTATUS_REMOVED);
            broadcastIntent.putExtra(EXTRA_APK_URL, entry.getUniqueKey());
            localBroadcastManager.sendBroadcast(broadcastIntent);
        }
    }

    private AppUpdateStatus createAppEntry(Apk apk, Status status, PendingIntent intent) {
        synchronized (appMapping) {
            ContentResolver resolver = context.getContentResolver();
            App app = AppProvider.Helper.findSpecificApp(resolver, apk.packageName, apk.repo);
            AppUpdateStatus ret = new AppUpdateStatus(app, apk, status, intent);
            appMapping.put(apk.getUrl(), ret);
            return ret;
        }
    }

    public void addApks(List<Apk> apksToUpdate, Status status) {
        startBatchUpdates();
        for (Apk apk : apksToUpdate) {
            addApk(apk, status, null);
        }
        endBatchUpdates(status);
    }

    /**
     * Add an Apk to the AppUpdateStatusManager manager (or update it if we already know about it).
     * @param apk The apk to add.
     * @param status The current status of the app
     * @param pendingIntent Action when notification is clicked. Can be null for default action(s)
     */
    public void addApk(Apk apk, @NonNull Status status, @Nullable PendingIntent pendingIntent) {
        if (apk == null) {
            return;
        }

        synchronized (appMapping) {
            AppUpdateStatus entry = appMapping.get(apk.getUrl());
            if (entry != null) {
                updateApkInternal(entry, status, pendingIntent);
            } else {
                addApkInternal(apk, status, pendingIntent);
            }
        }
    }

    /**
     * @param pendingIntent Action when notification is clicked. Can be null for default action(s)
     */
    public void updateApk(String key, @NonNull Status status, @Nullable PendingIntent pendingIntent) {
        synchronized (appMapping) {
            AppUpdateStatus entry = appMapping.get(key);
            if (entry != null) {
                updateApkInternal(entry, status, pendingIntent);
            }
        }
    }

    @Nullable
    public Apk getApk(String key) {
        synchronized (appMapping) {
            AppUpdateStatus entry = appMapping.get(key);
            if (entry != null) {
                return entry.apk;
            }
            return null;
        }
    }

    public void removeApk(String key) {
        synchronized (appMapping) {
            AppUpdateStatus entry = appMapping.get(key);
            if (entry != null) {
                Utils.debugLog(LOGTAG, "Remove APK " + entry.apk.apkName);
                appMapping.remove(entry.apk.getUrl());
                notifyRemove(entry);
            }
        }
    }

    public void refreshApk(String key) {
        synchronized (appMapping) {
            AppUpdateStatus entry = appMapping.get(key);
            if (entry != null) {
                Utils.debugLog(LOGTAG, "Refresh APK " + entry.apk.apkName);
                notifyChange(entry, true);
            }
        }
    }

    public void updateApkProgress(String key, int max, int current) {
        synchronized (appMapping) {
            AppUpdateStatus entry = appMapping.get(key);
            if (entry != null) {
                entry.progressMax = max;
                entry.progressCurrent = current;
                notifyChange(entry, false);
            }
        }
    }

    public void setApkError(Apk apk, String errorText) {
        synchronized (appMapping) {
            AppUpdateStatus entry = appMapping.get(apk.getUrl());
            if (entry == null) {
                entry = createAppEntry(apk, Status.InstallError, null);
            }
            entry.status = Status.InstallError;
            entry.errorText = errorText;
            entry.intent = getAppErrorIntent(entry);
            notifyChange(entry, false);
        }
    }

    private void startBatchUpdates() {
        synchronized (appMapping) {
            isBatchUpdating = true;
        }
    }

    private void endBatchUpdates(Status status) {
        synchronized (appMapping) {
            isBatchUpdating = false;

            String reason = null;
            if (status == Status.ReadyToInstall) {
                reason = REASON_READY_TO_INSTALL;
            } else if (status == Status.UpdateAvailable) {
                reason = REASON_UPDATES_AVAILABLE;
            }
            notifyChange(reason);
        }
    }

    void clearAllUpdates() {
        synchronized (appMapping) {
            for (Iterator<Map.Entry<String, AppUpdateStatus>> it = appMapping.entrySet().iterator(); it.hasNext();) {
                Map.Entry<String, AppUpdateStatus> entry = it.next();
                if (entry.getValue().status != Status.Installed) {
                    it.remove();
                }
            }
            notifyChange(REASON_CLEAR_ALL_UPDATES);
        }
    }

    void clearAllInstalled() {
        synchronized (appMapping) {
            for (Iterator<Map.Entry<String, AppUpdateStatus>> it = appMapping.entrySet().iterator(); it.hasNext();) {
                Map.Entry<String, AppUpdateStatus> entry = it.next();
                if (entry.getValue().status == Status.Installed) {
                    it.remove();
                }
            }
            notifyChange(REASON_CLEAR_ALL_INSTALLED);
        }
    }

    private PendingIntent getContentIntent(AppUpdateStatus entry) {
        switch (entry.status) {
            case UpdateAvailable:
            case ReadyToInstall:
                // Make sure we have an intent to install the app. If not set, we create an intent
                // to open up the app details page for the app. From there, the user can hit "install"
                return getAppDetailsIntent(entry.apk);

            case InstallError:
                return getAppErrorIntent(entry);

            case Installed:
                PackageManager pm = context.getPackageManager();
                Intent intentObject = pm.getLaunchIntentForPackage(entry.app.packageName);
                if (intentObject != null) {
                    return PendingIntent.getActivity(context, 0, intentObject, 0);
                } else {
                    // Could not get launch intent, maybe not launchable, e.g. a keyboard
                    return getAppDetailsIntent(entry.apk);
                }
        }
        return null;
    }

    /**
     * Get a {@link PendingIntent} for a {@link Notification} to send when it
     * is clicked.  {@link AppDetails} handles {@code Intent}s that are missing
     * or bad {@link AppDetails#EXTRA_APPID}, so it does not need to be checked
     * here.
     */
    private PendingIntent getAppDetailsIntent(Apk apk) {
        Intent notifyIntent = new Intent(context, AppDetails.class)
                .putExtra(AppDetails.EXTRA_APPID, apk.packageName);

        return TaskStackBuilder.create(context)
                .addParentStack(AppDetails.class)
                .addNextIntent(notifyIntent)
                .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private PendingIntent getAppErrorIntent(AppUpdateStatus entry) {
        String title = String.format(context.getString(R.string.install_error_notify_title), entry.app.name);

        Intent errorDialogIntent = new Intent(context, ErrorDialogActivity.class)
                .putExtra(ErrorDialogActivity.EXTRA_TITLE, title)
                .putExtra(ErrorDialogActivity.EXTRA_MESSAGE, entry.errorText);

        return PendingIntent.getActivity(
                context,
                0,
                errorDialogIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
