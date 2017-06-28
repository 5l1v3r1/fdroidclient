package org.fdroid.fdroid.views.apps;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Outline;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.util.Pair;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;

import org.fdroid.fdroid.AppDetails2;
import org.fdroid.fdroid.AppUpdateStatusManager;
import org.fdroid.fdroid.R;
import org.fdroid.fdroid.Utils;
import org.fdroid.fdroid.data.Apk;
import org.fdroid.fdroid.data.ApkProvider;
import org.fdroid.fdroid.data.App;
import org.fdroid.fdroid.installer.ApkCache;
import org.fdroid.fdroid.installer.InstallManagerService;
import org.fdroid.fdroid.installer.Installer;
import org.fdroid.fdroid.installer.InstallerFactory;

import java.io.File;
import java.util.Iterator;

// TODO: Support cancelling of downloads by tapping the install button a second time.
@SuppressWarnings("LineLength")
public class AppListItemController extends RecyclerView.ViewHolder {

    private static final String TAG = "AppListItemController";

    private final Activity activity;

    @NonNull
    private final ImageView icon;

    @NonNull
    private final TextView name;

    @Nullable
    private final ImageView installButton;

    @Nullable
    private final TextView status;

    @Nullable
    private final TextView appInstallStatus;

    @Nullable
    private final TextView installedVersion;

    @Nullable
    private final TextView ignoredStatus;

    @Nullable
    private final ProgressBar progressBar;

    @Nullable
    private final ImageButton cancelButton;

    /**
     * Will operate as the "Download is complete, click to (install|update)" button, as well as the
     * "Installed successfully, click to run" button.
     */
    @Nullable
    private final Button actionButton;

    private final DisplayImageOptions displayImageOptions;

    @Nullable
    private App currentApp;

    @Nullable
    private AppUpdateStatusManager.AppUpdateStatus currentStatus;

    @TargetApi(21)
    public AppListItemController(final Activity activity, View itemView) {
        super(itemView);
        this.activity = activity;

        installButton = (ImageView) itemView.findViewById(R.id.install);
        if (installButton != null) {
            installButton.setOnClickListener(onActionClicked);

            if (Build.VERSION.SDK_INT >= 21) {
                installButton.setOutlineProvider(new ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, Outline outline) {
                        float density = activity.getResources().getDisplayMetrics().density;

                        // TODO: This is a bit hacky/hardcoded/too-specific to the particular icons we're using.
                        // This is because the default "download & install" and "downloaded & ready to install"
                        // icons are smaller than the "downloading progress" button. Hence, we can't just use
                        // the width/height of the view to calculate the outline size.
                        int xPadding = (int) (8 * density);
                        int yPadding = (int) (9 * density);
                        outline.setOval(xPadding, yPadding, installButton.getWidth() - xPadding, installButton.getHeight() - yPadding);
                    }
                });
            }
        }

        icon = (ImageView) itemView.findViewById(R.id.icon);
        name = (TextView) itemView.findViewById(R.id.app_name);
        status = (TextView) itemView.findViewById(R.id.status);
        appInstallStatus = (TextView) itemView.findViewById(R.id.app_install_status);
        installedVersion = (TextView) itemView.findViewById(R.id.installed_version);
        ignoredStatus = (TextView) itemView.findViewById(R.id.ignored_status);
        progressBar = (ProgressBar) itemView.findViewById(R.id.progress_bar);
        cancelButton = (ImageButton) itemView.findViewById(R.id.cancel_button);
        actionButton = (Button) itemView.findViewById(R.id.action_button);

        if (actionButton != null) {
            actionButton.setOnClickListener(onActionClicked);
        }

        if (cancelButton != null) {
            cancelButton.setOnClickListener(onCancelDownload);
        }

        displayImageOptions = Utils.getImageLoadingOptions().build();

        itemView.setOnClickListener(onAppClicked);
    }

    /**
     * Figures out the current install/update/download/etc status for the app we are viewing.
     * Then, asks the view to update itself to reflect this status.
     */
    private void refreshStatus(@NonNull App app) {
        Iterator<AppUpdateStatusManager.AppUpdateStatus> statuses = AppUpdateStatusManager.getInstance(activity).getByPackageName(app.packageName).iterator();
        if (statuses.hasNext()) {
            AppUpdateStatusManager.AppUpdateStatus status = statuses.next();
            updateAppStatus(app, status);
        } else {
            updateAppStatus(app, null);
        }
    }

    public void bindModel(@NonNull App app) {
        currentApp = app;

        ImageLoader.getInstance().displayImage(app.iconUrl, icon, displayImageOptions);

        refreshStatus(app);

        final LocalBroadcastManager broadcastManager = LocalBroadcastManager.getInstance(activity.getApplicationContext());
        broadcastManager.unregisterReceiver(onStatusChanged);

        // broadcastManager.registerReceiver(onInstallAction, Installer.getInstallIntentFilter(Uri.parse(currentAppDownloadUrl)));

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(AppUpdateStatusManager.BROADCAST_APPSTATUS_ADDED);
        intentFilter.addAction(AppUpdateStatusManager.BROADCAST_APPSTATUS_REMOVED);
        intentFilter.addAction(AppUpdateStatusManager.BROADCAST_APPSTATUS_CHANGED);
        broadcastManager.registerReceiver(onStatusChanged, intentFilter);
    }

    @NonNull
    private AppListItemState getCurrentViewState(
            @NonNull App app, @Nullable AppUpdateStatusManager.AppUpdateStatus appStatus) {
        if (appStatus == null) {
            return getViewStateDefault(app);
        } else {
            switch (appStatus.status) {
                case ReadyToInstall:
                    return getViewStateReadyToInstall(app);

                case Downloading:
                    return getViewStateDownloading(app, appStatus);

                case Installed:
                    return getViewStateInstalled(app);

                default:
                    return getViewStateDefault(app);
            }
        }
    }

    private void refreshView(@NonNull App app,
                             @Nullable AppUpdateStatusManager.AppUpdateStatus appStatus) {

        AppListItemState viewState = getCurrentViewState(app, appStatus);

        name.setText(viewState.getMainText());

        if (appInstallStatus != null) {
            if (viewState.shouldShowAppInstallStatusText()) {
                appInstallStatus.setVisibility(View.VISIBLE);
                appInstallStatus.setText(viewState.getAppInstallStatusText());
            } else {
                appInstallStatus.setVisibility(View.GONE);
            }
        }

        if (actionButton != null) {
            if (viewState.shouldShowActionButton()) {
                actionButton.setVisibility(View.VISIBLE);
                actionButton.setText(viewState.getActionButtonText());
            } else {
                actionButton.setVisibility(View.GONE);
            }
        }

        if (progressBar != null) {
            if (viewState.showProgress()) {
                progressBar.setVisibility(View.VISIBLE);
                if (viewState.isProgressIndeterminate()) {
                    progressBar.setIndeterminate(true);
                } else {
                    progressBar.setIndeterminate(false);
                    progressBar.setMax(viewState.getProgressMax());
                    progressBar.setProgress(viewState.getProgressCurrent());
                }
            } else {
                progressBar.setVisibility(View.GONE);
            }
        }

        if (cancelButton != null) {
            if (viewState.showProgress()) {
                cancelButton.setVisibility(View.VISIBLE);
            } else {
                cancelButton.setVisibility(View.GONE);
            }
        }

        if (installButton != null) {
            if (viewState.shouldShowActionButton()) {
                installButton.setVisibility(View.GONE);
            } else if (viewState.showProgress()) {
                installButton.setVisibility(View.VISIBLE);
                installButton.setImageDrawable(ContextCompat.getDrawable(activity, R.drawable.ic_download_progress));
                int progressAsDegrees = viewState.getProgressMax() <= 0 ? 0 :
                        (int) (((float) viewState.getProgressCurrent() / viewState.getProgressMax()) * 360);
                installButton.setImageLevel(progressAsDegrees);
            } else if (viewState.shouldShowInstall()) {
                installButton.setVisibility(View.VISIBLE);
                installButton.setImageDrawable(ContextCompat.getDrawable(activity, R.drawable.ic_download));
            } else {
                installButton.setVisibility(View.GONE);
            }
        }

        if (installedVersion != null) {
            installedVersion.setVisibility(View.VISIBLE);
            installedVersion.setText(viewState.getInstalledVersionText());
        }

        if (status != null) {
            CharSequence statusText = viewState.getStatusText();
            if (statusText == null) {
                status.setVisibility(View.GONE);
            } else {
                status.setVisibility(View.VISIBLE);
                status.setText(statusText);
            }
        }

        if (ignoredStatus != null) {
            CharSequence ignoredStatusText = viewState.getIgnoredStatusText();
            if (ignoredStatusText == null) {
                ignoredStatus.setVisibility(View.GONE);
            } else {
                ignoredStatus.setVisibility(View.VISIBLE);
                ignoredStatus.setText(ignoredStatusText);
            }
        }
    }

    private AppListItemState getViewStateInstalled(@NonNull App app) {
        CharSequence mainText = activity.getString(
                R.string.app_list__name__successfully_installed, app.name);

        AppListItemState state = new AppListItemState(activity, app)
                .setMainText(mainText)
                .setAppInstallStatusText(activity.getString(R.string.notification_content_single_installed));

        if (activity.getPackageManager().getLaunchIntentForPackage(app.packageName) != null) {
            state.showActionButton(activity.getString(R.string.menu_launch));
        }

        return state;
    }

    private AppListItemState getViewStateDownloading(
            @NonNull App app, @NonNull AppUpdateStatusManager.AppUpdateStatus currentStatus) {
        CharSequence mainText = activity.getString(
                R.string.app_list__name__downloading_in_progress, app.name);

        return new AppListItemState(activity, app)
                .setMainText(mainText)
                .setProgress(currentStatus.progressCurrent, currentStatus.progressMax);
    }

    private AppListItemState getViewStateReadyToInstall(@NonNull App app) {
        int actionButtonLabel = app.isInstalled()
                ? R.string.app__install_downloaded_update
                : R.string.menu_install;

        return new AppListItemState(activity, app)
                .setMainText(app.name)
                .showActionButton(activity.getString(actionButtonLabel))
                .setAppInstallStatusText(activity.getString(R.string.app_list_download_ready));
    }

    private AppListItemState getViewStateDefault(@NonNull App app) {
        return new AppListItemState(activity, app);
    }

    @SuppressWarnings("FieldCanBeLocal")
    private final View.OnClickListener onAppClicked = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (currentApp == null) {
                return;
            }

            Intent intent = new Intent(activity, AppDetails2.class);
            intent.putExtra(AppDetails2.EXTRA_APPID, currentApp.packageName);
            if (Build.VERSION.SDK_INT >= 21) {
                Pair<View, String> iconTransitionPair = Pair.create((View) icon, activity.getString(R.string.transition_app_item_icon));
                Bundle bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(activity, iconTransitionPair).toBundle();
                activity.startActivity(intent, bundle);
            } else {
                activity.startActivity(intent);
            }
        }
    };

    /**
     * Updates both the progress bar and the circular install button (which shows progress around the outside of the circle).
     * Also updates the app label to indicate that the app is being downloaded.
     */
    private void updateAppStatus(@NonNull App app, @Nullable AppUpdateStatusManager.AppUpdateStatus status) {
        currentStatus = status;
        refreshView(app, status);
    }

    private final BroadcastReceiver onStatusChanged = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            AppUpdateStatusManager.AppUpdateStatus newStatus = intent.getParcelableExtra(AppUpdateStatusManager.EXTRA_STATUS);

            if (currentApp == null || !TextUtils.equals(newStatus.app.packageName, currentApp.packageName) || (installButton == null && progressBar == null)) {
                return;
            }

            updateAppStatus(currentApp, newStatus);
        }
    };

    @SuppressWarnings("FieldCanBeLocal")
    private final View.OnClickListener onActionClicked = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (currentApp == null) {
                return;
            }

            // When the button says "Run", then launch the app.
            if (currentStatus != null && currentStatus.status == AppUpdateStatusManager.Status.Installed) {
                Intent intent = activity.getPackageManager().getLaunchIntentForPackage(currentApp.packageName);
                if (intent != null) {
                    activity.startActivity(intent);

                    // Once it is explicitly launched by the user, then we can pretty much forget about
                    // any sort of notification that the app was successfully installed. It should be
                    // apparent to the user because they just launched it.
                    AppUpdateStatusManager.getInstance(activity).removeApk(currentStatus.getUniqueKey());
                }
                return;
            }

            if (currentStatus != null && currentStatus.status == AppUpdateStatusManager.Status.ReadyToInstall) {
                File apkFilePath = ApkCache.getApkDownloadPath(activity, Uri.parse(currentStatus.apk.getUrl()));
                Utils.debugLog(TAG, "skip download, we have already downloaded " + currentStatus.apk.getUrl() + " to " + apkFilePath);

                // TODO: This seems like a bit of a hack. Is there a better way to do this by changing
                // the Installer API so that we can ask it to install without having to get it to fire
                // off an intent which we then listen for and action?
                final LocalBroadcastManager broadcastManager = LocalBroadcastManager.getInstance(activity);
                final BroadcastReceiver receiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        broadcastManager.unregisterReceiver(this);

                        if (Installer.ACTION_INSTALL_USER_INTERACTION.equals(intent.getAction())) {
                            PendingIntent pendingIntent = intent.getParcelableExtra(Installer.EXTRA_USER_INTERACTION_PI);
                            try {
                                pendingIntent.send();
                            } catch (PendingIntent.CanceledException ignored) { }
                        }
                    }
                };

                broadcastManager.registerReceiver(receiver, Installer.getInstallIntentFilter(Uri.parse(currentStatus.apk.getUrl())));
                Installer installer = InstallerFactory.create(activity, currentStatus.apk);
                installer.installPackage(Uri.parse(apkFilePath.toURI().toString()), Uri.parse(currentStatus.apk.getUrl()));
            } else {
                final Apk suggestedApk = ApkProvider.Helper.findSuggestedApk(activity, currentApp);
                InstallManagerService.queue(activity, currentApp, suggestedApk);
            }
        }
    };

    @SuppressWarnings("FieldCanBeLocal")
    private final View.OnClickListener onCancelDownload = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (currentStatus == null || currentStatus.status != AppUpdateStatusManager.Status.Downloading) {
                return;
            }

            InstallManagerService.cancel(activity, currentStatus.getUniqueKey());
        }
    };
}
