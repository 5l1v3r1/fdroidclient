package org.fdroid.fdroid.views;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentPagerAdapter;

import org.fdroid.fdroid.FDroid;
import org.fdroid.fdroid.R;
import org.fdroid.fdroid.compat.TabManager;
import org.fdroid.fdroid.data.AppProvider;
import org.fdroid.fdroid.views.fragments.AvailableAppsFragment;
import org.fdroid.fdroid.views.fragments.CanUpdateAppsFragment;
import org.fdroid.fdroid.views.fragments.InstalledAppsFragment;

/**
 * Used by the FDroid activity in conjunction with its ViewPager to support
 * swiping of tabs for both old devices (< 3.0) and new devices.
 */
public class AppListFragmentPagerAdapter extends FragmentPagerAdapter {

    private final FDroid parent;

    public AppListFragmentPagerAdapter(FDroid parent) {
        super(parent.getSupportFragmentManager());
        this.parent = parent;
    }

    private String getInstalledTabTitle() {
        int installedCount = AppProvider.Helper.count(parent, AppProvider.getInstalledUri());
        return parent.getString(R.string.tab_installed_apps_i18n, installedCount);
    }

    private String getUpdateTabTitle() {
        int updateCount = AppProvider.Helper.count(parent, AppProvider.getCanUpdateUri());
        return parent.getString(R.string.tab_updates_i18n, updateCount);
    }

    @Override
    public Fragment getItem(int i) {
        switch (i) {
            case TabManager.INDEX_AVAILABLE:
                return new AvailableAppsFragment();
            case TabManager.INDEX_INSTALLED:
                return new InstalledAppsFragment();
            default:
                return new CanUpdateAppsFragment();
        }
    }

    @Override
    public int getCount() {
        return TabManager.INDEX_COUNT;
    }

    @Override
    public String getPageTitle(int i) {
        switch (i) {
            case TabManager.INDEX_AVAILABLE:
                return parent.getString(R.string.tab_available_apps);
            case TabManager.INDEX_INSTALLED:
                return getInstalledTabTitle();
            case TabManager.INDEX_CAN_UPDATE:
                return getUpdateTabTitle();
            default:
                return "";
        }
    }

}
