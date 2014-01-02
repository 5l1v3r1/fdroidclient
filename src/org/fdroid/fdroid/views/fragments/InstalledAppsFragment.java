package org.fdroid.fdroid.views.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.fdroid.fdroid.R;
import org.fdroid.fdroid.views.AppListAdapter;

public class InstalledAppsFragment extends AppListFragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return createPlainAppList();
    }

    @Override
    protected AppListAdapter getAppListAdapter() {
        return getAppListManager().getInstalledAdapter();
    }

    @Override
    protected String getFromTitle() {
        return parent.getString(R.string.inst);
    }
}
