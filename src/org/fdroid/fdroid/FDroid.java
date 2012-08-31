/*
 * Copyright (C) 2010-12  Ciaran Gultnieks, ciaran@ciarang.com
 * Copyright (C) 2009  Roberto Jacinto, roberto.jacinto@caixamagica.pt
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.fdroid.fdroid;

import java.io.File;
import java.util.Calendar;
import java.util.Date;
import java.util.Vector;

import org.fdroid.fdroid.R;

import android.R.drawable;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.app.TabActivity;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.ResultReceiver;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.TabHost.TabSpec;

public class FDroid extends TabActivity implements OnItemClickListener,
        OnItemSelectedListener {

    private String LOCAL_PATH = "/sdcard/.fdroid";

    private static final int REQUEST_APPDETAILS = 0;
    private static final int REQUEST_MANAGEREPOS = 1;
    private static final int REQUEST_PREFS = 2;

    private static final int UPDATE_REPO = Menu.FIRST;
    private static final int MANAGE_REPO = Menu.FIRST + 1;
    private static final int PREFERENCES = Menu.FIRST + 2;
    private static final int ABOUT = Menu.FIRST + 3;
    private static final int SEARCH = Menu.FIRST + 4;

    private DB db = null;

    // Apps that are available to be installed
    private AppListAdapter apps_av = new AppListAdapter(this);

    // Apps that are installed
    private AppListAdapter apps_in = new AppListAdapter(this);

    // Apps that can be upgraded
    private AppListAdapter apps_up = new AppListAdapter(this);

    // Category list
    private ArrayAdapter<String> categories;
    private String currentCategory = null;

    private ProgressDialog pd;

    private static final String TAB_IN = "INST";
    private static final String TAB_UN = "UNIN";
    private static final String TAB_UP = "UPDT";
    private TabHost tabHost;
    private TabSpec ts;
    private TabSpec ts1;
    private TabSpec tsUp;

    private boolean triedEmptyUpdate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.fdroid);

        File local_path = new File(LOCAL_PATH);
        if (!local_path.exists())
            local_path.mkdir();

        File icon_path = new File(DB.getIconsPath());
        if (!icon_path.exists())
            icon_path.mkdir();

        Spinner spinner = (Spinner) findViewById(R.id.category);
        categories = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, new Vector<String>());
        categories
                .setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(categories);
        spinner.setOnItemSelectedListener(FDroid.this);

        tabHost = getTabHost();
        createTabs();

        Intent i = getIntent();
        if (i.hasExtra("uri")) {
            Intent call = new Intent(this, ManageRepo.class);
            call.putExtra("uri", i.getStringExtra("uri"));
            startActivityForResult(call, REQUEST_MANAGEREPOS);
        }

    }

    @Override
    protected void onStart() {
        super.onStart();
        db = new DB(this);
        triedEmptyUpdate = false;
        populateLists(true);
    }

    @Override
    protected void onStop() {
        db.close();
        super.onStop();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        super.onCreateOptionsMenu(menu);
        menu.add(Menu.NONE, UPDATE_REPO, 1, R.string.menu_update_repo).setIcon(
                android.R.drawable.ic_menu_rotate);
        menu.add(Menu.NONE, MANAGE_REPO, 2, R.string.menu_manage).setIcon(
                android.R.drawable.ic_menu_agenda);
        menu.add(Menu.NONE, SEARCH, 3, R.string.menu_search).setIcon(
                android.R.drawable.ic_menu_search);
        menu.add(Menu.NONE, PREFERENCES, 4, R.string.menu_preferences).setIcon(
                android.R.drawable.ic_menu_preferences);
        menu.add(Menu.NONE, ABOUT, 5, R.string.menu_about).setIcon(
                android.R.drawable.ic_menu_help);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {

        case UPDATE_REPO:
            updateRepos();
            return true;

        case MANAGE_REPO:
            Intent i = new Intent(this, ManageRepo.class);
            startActivityForResult(i, REQUEST_MANAGEREPOS);
            return true;

        case PREFERENCES:
            Intent prefs = new Intent(getBaseContext(), Preferences.class);
            startActivityForResult(prefs, REQUEST_PREFS);
            return true;

        case SEARCH:
            onSearchRequested();
            return true;

        case ABOUT:
            LayoutInflater li = LayoutInflater.from(this);
            View view = li.inflate(R.layout.about, null);

            // Fill in the version...
            TextView tv = (TextView) view.findViewById(R.id.version);
            PackageManager pm = getPackageManager();
            try {
                PackageInfo pi = pm.getPackageInfo(getApplicationContext()
                        .getPackageName(), 0);
                tv.setText(pi.versionName);
            } catch (Exception e) {
            }

            Builder p = new AlertDialog.Builder(this).setView(view);
            final AlertDialog alrt = p.create();
            alrt.setIcon(R.drawable.icon);
            alrt.setTitle(getString(R.string.about_title));
            alrt.setButton(AlertDialog.BUTTON_NEUTRAL,
                    getString(R.string.about_website),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog,
                                int whichButton) {
                            Uri uri = Uri.parse("http://f-droid.org");
                            startActivity(new Intent(Intent.ACTION_VIEW, uri));
                        }
                    });
            alrt.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.ok),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog,
                                int whichButton) {
                        }
                    });
            alrt.show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        switch (requestCode) {
        case REQUEST_APPDETAILS:
            break;
        case REQUEST_MANAGEREPOS:
            if (data.hasExtra("update")) {
                AlertDialog.Builder ask_alrt = new AlertDialog.Builder(this);
                ask_alrt.setTitle(getString(R.string.repo_update_title));
                ask_alrt.setIcon(android.R.drawable.ic_menu_rotate);
                ask_alrt.setMessage(getString(R.string.repo_alrt));
                ask_alrt.setPositiveButton(getString(R.string.yes),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,
                                    int whichButton) {
                                updateRepos();
                            }
                        });
                ask_alrt.setNegativeButton(getString(R.string.no),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,
                                    int whichButton) {
                                return;
                            }
                        });
                AlertDialog alert = ask_alrt.create();
                alert.show();
            }
            break;
        case REQUEST_PREFS:
            // The automatic update settings may have changed, so reschedule (or
            // unschedule) the
            // service accordingly. It's cheap, so no need to check if the
            // particular setting has
            // actually been changed.
            UpdateService.schedule(getBaseContext());
            break;

        }
    }

    private void createTabs() {
        tabHost.clearAllTabs();

        // TabContentFactory that can generate the appropriate list for each
        // tab...
        TabHost.TabContentFactory tf = new TabHost.TabContentFactory() {
            @Override
            public View createTabContent(String tag) {

                AppListAdapter ad;
                if (tag.equals(TAB_IN))
                    ad = apps_in;
                else if (tag.equals(TAB_UP))
                    ad = apps_up;
                else
                    ad = apps_av;

                ListView lst = new ListView(FDroid.this);
                lst.setFastScrollEnabled(true);
                lst.setOnItemClickListener(FDroid.this);
                lst.setAdapter(ad);
                return lst;
            }
        };

        // Create the tab of installed apps...
        ts = tabHost.newTabSpec(TAB_IN);
        ts.setIndicator(getString(R.string.tab_installed), getResources()
                .getDrawable(drawable.star_off));
        ts.setContent(tf);

        // Create the tab of apps with updates...
        tsUp = tabHost.newTabSpec(TAB_UP);
        tsUp.setIndicator(getString(R.string.tab_updates), getResources()
                .getDrawable(drawable.star_on));
        tsUp.setContent(tf);

        // Create the tab of available apps...
        ts1 = tabHost.newTabSpec(TAB_UN);
        ts1.setIndicator(getString(R.string.tab_noninstalled), getResources()
                .getDrawable(drawable.ic_input_add));
        ts1.setContent(tf);

        tabHost.addTab(ts1);
        tabHost.addTab(ts);
        tabHost.addTab(tsUp);

    }

    // Populate the lists.
    // 'update' - true to update the installed status of the applications
    // by asking the system.
    private void populateLists(boolean update) {

        apps_in.clear();
        apps_av.clear();
        apps_up.clear();
        categories.clear();

        long startTime = System.currentTimeMillis();

        // Populate the category list with the real categories, and the locally
        // generated meta-categories for "All", "What's New" and "Recently
        // Updated"...
        String cat_all = getString(R.string.category_all);
        String cat_whatsnew = getString(R.string.category_whatsnew);
        String cat_recentlyupdated = getString(R.string.category_recentlyupdated);
        categories.add(cat_all);
        for (String s : db.getCategories()) {
            Log.d("FDroid", "s: " + s);
            categories.add(s);
        }
        categories.add(cat_whatsnew);
        categories.add(cat_recentlyupdated);
        if (currentCategory == null)
            currentCategory = cat_all;

        Vector<DB.App> apps = db.getApps(null, null, update, true);
        if (apps.isEmpty()) {
            // Don't attempt this more than once - we may have invalid
            // repositories.
            if (triedEmptyUpdate)
                return;
            // If there are no apps, update from the repos - it must be a
            // new installation.
            Log.d("FDroid", "Empty app list forces repo update");
            updateRepos();
            triedEmptyUpdate = true;
            return;
        }
        Log.d("FDroid", "Updating lists - " + apps.size() + " apps in total"
                + " (update took " + (System.currentTimeMillis() - startTime)
                + " ms)");

        // Calculate the cutoff date we'll use for What's New and Recently
        // Updated...
        Calendar recent = Calendar.getInstance();
        recent.add(Calendar.DAY_OF_YEAR, -14);
        Date recentDate = recent.getTime();

        for (DB.App app : apps) {
            if (currentCategory.equals(cat_all)) {
                // Let everything through!
            } else if (currentCategory.equals(cat_whatsnew)) {
                if (app.added == null)
                    continue;
                if (app.added.compareTo(recentDate) < 0)
                    continue;
            } else if (currentCategory.equals(cat_recentlyupdated)) {
                if (app.lastUpdated == null)
                    continue;
                // Don't include in the recently updated category if the
                // 'update' was actually it being added.
                if (app.lastUpdated.compareTo(app.added) == 0)
                    continue;
                if (app.lastUpdated.compareTo(recentDate) < 0)
                    continue;
            } else {
                if (!currentCategory.equals(app.category))
                    continue;
            }
            if (app.installedVersion == null) {
                apps_av.addItem(app);
            } else {
                apps_in.addItem(app);
                if (app.hasUpdates)
                    apps_up.addItem(app);
            }
        }

        // Update the count on the 'Updates' tab to show the number available.
        // This is quite unpleasant, but seems to be the only way to do it.
        TextView uptext = (TextView) tabHost.getTabWidget().getChildAt(2)
                .findViewById(android.R.id.title);
        uptext.setText(getString(R.string.tab_updates) + " ("
                + Integer.toString(apps_up.getCount()) + ")");

        // Tell the lists that the data behind the adapter has changed, so
        // they can refresh...
        apps_av.notifyDataSetChanged();
        apps_in.notifyDataSetChanged();
        apps_up.notifyDataSetChanged();
        categories.notifyDataSetChanged();

    }

    // For receiving results from the UpdateService when we've told it to
    // update in response to a user request.
    private class UpdateReceiver extends ResultReceiver {
        public UpdateReceiver(Handler handler) {
            super(handler);
        }
        @Override
        protected void onReceiveResult (int resultCode, Bundle resultData) {
            if (resultCode == 1) {
                Toast.makeText(FDroid.this,
                        getString(R.string.connection_error_msg),
                        Toast.LENGTH_LONG).show();
            } else {
                populateLists(true);
            }
            if (pd.isShowing())
                pd.dismiss();
        }
    }
    private UpdateReceiver mUpdateReceiver;

    // Force a repo update now. A progress dialog is shown and the UpdateService
    // is told to do the update, which will result in the database changing. The
    // UpdateReceiver class should get told when this is finished.
    private void updateRepos() {
        pd = ProgressDialog.show(this, getString(R.string.process_wait_title),
                getString(R.string.process_update_msg), true, true);
        pd.setIcon(android.R.drawable.ic_dialog_info);

        Intent intent = new Intent(this, UpdateService.class);
        mUpdateReceiver = new UpdateReceiver(new Handler());
        intent.putExtra("receiver", mUpdateReceiver);
        startService(intent);
    }

    public void onItemSelected(AdapterView<?> parent, View view, int pos,
            long id) {
        currentCategory = parent.getItemAtPosition(pos).toString();
        populateLists(false);
    }

    public void onNothingSelected(AdapterView<?> parent) {
        // We always have at least "All"
    }

    // Handler for a click on one of the items in an application list. Pops
    // up a dialog that shows the details of the application and all its
    // available versions, with buttons to allow installation etc.
    public void onItemClick(AdapterView<?> arg0, View arg1, final int arg2,
            long arg3) {

        final DB.App app;
        String curtab = tabHost.getCurrentTabTag();
        if (curtab.equalsIgnoreCase(TAB_IN)) {
            app = (DB.App) apps_in.getItem(arg2);
        } else if (curtab.equalsIgnoreCase(TAB_UP)) {
            app = (DB.App) apps_up.getItem(arg2);
        } else {
            app = (DB.App) apps_av.getItem(arg2);
        }

        Intent intent = new Intent(this, AppDetails.class);
        intent.putExtra("appid", app.id);
        startActivityForResult(intent, REQUEST_APPDETAILS);

    }

}
