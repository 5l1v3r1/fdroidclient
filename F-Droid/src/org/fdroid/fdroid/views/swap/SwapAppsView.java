package org.fdroid.fdroid.views.swap;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.support.annotation.ColorRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.CursorAdapter;
import android.support.v7.widget.SearchView;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;

import org.fdroid.fdroid.R;
import org.fdroid.fdroid.UpdateService;
import org.fdroid.fdroid.Utils;
import org.fdroid.fdroid.data.App;
import org.fdroid.fdroid.data.AppProvider;
import org.fdroid.fdroid.data.Repo;
import org.fdroid.fdroid.localrepo.SwapService;

import java.util.Timer;
import java.util.TimerTask;

public class SwapAppsView extends ListView implements
        SwapWorkflowActivity.InnerView,
        LoaderManager.LoaderCallbacks<Cursor>,
        SearchView.OnQueryTextListener {

    private DisplayImageOptions displayImageOptions;

    public SwapAppsView(Context context) {
        super(context);
    }

    public SwapAppsView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SwapAppsView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public SwapAppsView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    private SwapWorkflowActivity getActivity() {
        return (SwapWorkflowActivity)getContext();
    }

    private SwapService getState() {
        return getActivity().getState();
    }

    private static final int LOADER_SWAPABLE_APPS = 759283741;
    private static final String TAG = "SwapAppsView";

    private Repo repo;
    private AppListAdapter adapter;
    private String mCurrentFilterString;

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        repo = getActivity().getState().getPeerRepo();

        if (repo == null) {
            // TODO: Uh oh, something stuffed up for this to happen.
            // TODO: What is the best course of action from here?
        }

        adapter = new AppListAdapter(getContext(), getContext().getContentResolver().query(
                AppProvider.getRepoUri(repo), AppProvider.DataColumns.ALL, null, null, null));

        setAdapter(adapter);

        // either reconnect with an existing loader or start a new one
        getActivity().getSupportLoaderManager().initLoader(LOADER_SWAPABLE_APPS, null, this);

        setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                showAppDetails(position);
            }
        });

        displayImageOptions = Utils.getImageLoadingOptions().build();

        schedulePollForUpdates();
    }

    private BroadcastReceiver pollForUpdatesReceiver;

    private void pollForUpdates() {
        if (adapter.getCount() > 1 ||
                (adapter.getCount() == 1 && !new App((Cursor)adapter.getItem(0)).id.equals("org.fdroid.fdroid"))) {
            Log.d(TAG, "Not polling for new apps from swap repo, because we already have more than one.");
            return;
        }

        Log.d(TAG, "Polling swap repo to see if it has any updates.");
        getState().refreshSwap();
        if (pollForUpdatesReceiver != null) {
            pollForUpdatesReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    int statusCode = intent.getIntExtra(UpdateService.EXTRA_STATUS_CODE, -1);
                    switch (statusCode) {
                        case UpdateService.STATUS_COMPLETE_WITH_CHANGES:
                            Log.d(TAG, "Swap repo has updates, notifying the list adapter.");
                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    adapter.notifyDataSetChanged();
                                }
                            });
                            LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(pollForUpdatesReceiver);
                            break;

                        case UpdateService.STATUS_ERROR_GLOBAL:
                            // TODO: Well, if we can't get the index, we probably can't swapp apps.
                            // Tell the user somethign helpful?
                            break;

                        case UpdateService.STATUS_COMPLETE_AND_SAME:
                            schedulePollForUpdates();
                            break;
                    }
                }
            };
            // TODO: Unregister this properly, not just when successful (see swithc statement above)
            LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(pollForUpdatesReceiver);
        }
    }

    private void schedulePollForUpdates() {
        Log.d(TAG, "Scheduling poll for updated swap repo in 5 seconds.");
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                Looper.prepare();
                pollForUpdates();
                Looper.loop();
            }
        }, 5000);
    }

    @Override
    public boolean buildMenu(Menu menu, @NonNull MenuInflater inflater) {

        inflater.inflate(R.menu.swap_search, menu);

        SearchView searchView = new SearchView(getActivity());

        MenuItem searchMenuItem = menu.findItem(R.id.action_search);
        MenuItemCompat.setActionView(searchMenuItem, searchView);
        MenuItemCompat.setShowAsAction(searchMenuItem, MenuItemCompat.SHOW_AS_ACTION_ALWAYS);

        searchView.setOnQueryTextListener(this);
        return true;
    }

    @Override
    public int getStep() {
        return SwapService.STEP_SUCCESS;
    }

    @Override
    public int getPreviousStep() {
        return SwapService.STEP_INTRO;
    }

    @ColorRes
    public int getToolbarColour() {
        return getResources().getColor(R.color.swap_bright_blue);
    }

    @Override
    public String getToolbarTitle() {
        return getResources().getString(R.string.swap_success);
    }

    private void showAppDetails(int position) {
        Cursor c = (Cursor) adapter.getItem(position);
        App app = new App(c);
        // TODO: Show app details screen.
    }

    @Override
    public CursorLoader onCreateLoader(int id, Bundle args) {
        Uri uri = TextUtils.isEmpty(mCurrentFilterString)
                ? AppProvider.getRepoUri(repo)
                : AppProvider.getSearchUri(repo, mCurrentFilterString);

        return new CursorLoader(getActivity(), uri, AppProvider.DataColumns.ALL, null, null, AppProvider.DataColumns.NAME);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        adapter.swapCursor(cursor);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        adapter.swapCursor(null);
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        String newFilter = !TextUtils.isEmpty(newText) ? newText : null;
        if (mCurrentFilterString == null && newFilter == null) {
            return true;
        }
        if (mCurrentFilterString != null && mCurrentFilterString.equals(newFilter)) {
            return true;
        }
        mCurrentFilterString = newFilter;
        getActivity().getSupportLoaderManager().restartLoader(LOADER_SWAPABLE_APPS, null, this);
        return true;
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        // this is not needed since we respond to every change in text
        return true;
    }

    private class AppListAdapter extends CursorAdapter {

        @SuppressWarnings("UnusedDeclaration")
        private static final String TAG = "AppListAdapter";

        @Nullable
        private LayoutInflater inflater;

        @Nullable
        private Drawable defaultAppIcon;

        public AppListAdapter(@NonNull Context context, @Nullable Cursor c) {
            super(context, c, FLAG_REGISTER_CONTENT_OBSERVER);
        }

        @NonNull
        private LayoutInflater getInflater(Context context) {
            if (inflater == null) {
                inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            }
            return inflater;
        }

        private Drawable getDefaultAppIcon(Context context) {
            if (defaultAppIcon == null) {
                defaultAppIcon = context.getResources().getDrawable(android.R.drawable.sym_def_app_icon);
            }
            return defaultAppIcon;
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            View view = getInflater(context).inflate(R.layout.swap_app_list_item, parent, false);
            bindView(view, context, cursor);
            return view;
        }

        @Override
        public void bindView(final View view, final Context context, final Cursor cursor) {

            TextView nameView = (TextView)view.findViewById(R.id.name);
            ImageView iconView = (ImageView)view.findViewById(android.R.id.icon);
            Button btnInstall = (Button)view.findViewById(R.id.btn_install);
            TextView btnAttemptInstall = (TextView)view.findViewById(R.id.btn_attempt_install);
            TextView statusInstalled = (TextView)view.findViewById(R.id.status_installed);
            TextView statusIncompatible = (TextView)view.findViewById(R.id.status_incompatible);

            final App app = new App(cursor);

            nameView.setText(app.name);
            ImageLoader.getInstance().displayImage(app.iconUrl, iconView, displayImageOptions);

            btnInstall.setVisibility(View.GONE);
            btnAttemptInstall.setVisibility(View.GONE);
            statusInstalled.setVisibility(View.GONE);
            statusIncompatible.setVisibility(View.GONE);

            if (app.hasUpdates()) {
                btnInstall.setText(R.string.menu_upgrade);
                btnInstall.setVisibility(View.VISIBLE);
            } else if (app.isInstalled()) {
                statusInstalled.setVisibility(View.VISIBLE);
            } else if (!app.compatible) {
                btnAttemptInstall.setVisibility(View.VISIBLE);
                statusIncompatible.setVisibility(View.VISIBLE);
            } else {
                btnInstall.setText(R.string.menu_install);
                btnInstall.setVisibility(View.VISIBLE);
            }

            OnClickListener installListener = new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (app.hasUpdates() || app.compatible) {
                        getState().install(app);
                    }
                }
            };

            btnInstall.setOnClickListener(installListener);
            btnAttemptInstall.setOnClickListener(installListener);

        }
    }

}
