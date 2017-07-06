package org.fdroid.fdroid.views.updates.items;

import android.app.Activity;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.View;

import org.fdroid.fdroid.AppUpdateStatusManager;
import org.fdroid.fdroid.R;
import org.fdroid.fdroid.data.App;
import org.fdroid.fdroid.views.apps.AppListItemController;
import org.fdroid.fdroid.views.apps.AppListItemState;

/**
 * Shows apps which are:
 *  * In the process of being downloaded.
 *  * Downloaded and ready to install.
 *  * Recently installed and ready to run.
 */
public class AppStatusListItemController extends AppListItemController {
    public AppStatusListItemController(Activity activity, View itemView) {
        super(activity, itemView);
    }

    @NonNull
    @Override
    protected AppListItemState getCurrentViewState(
            @NonNull App app, @Nullable AppUpdateStatusManager.AppUpdateStatus appStatus) {

        return super.getCurrentViewState(app, appStatus)
                .setStatusText(getStatusText(appStatus));
    }

    @Nullable
    private CharSequence getStatusText(@Nullable AppUpdateStatusManager.AppUpdateStatus appStatus) {
        if (appStatus != null) {
            switch (appStatus.status) {
                case ReadyToInstall:
                    return activity.getString(R.string.app_list_download_ready);

                case Installed:
                    return activity.getString(R.string.notification_content_single_installed);
            }
        }

        return null;
    }
}
