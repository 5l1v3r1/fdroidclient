/*
 * Copyright (C) 2011-13  Ciaran Gultnieks, ciaran@ciarang.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
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

import java.util.ArrayList;
import java.util.List;

import android.app.ListActivity;
import android.app.SearchManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;

import android.support.v4.app.NavUtils;
import android.support.v4.view.MenuItemCompat;

import org.fdroid.fdroid.compat.ActionBarCompat;
import org.fdroid.fdroid.views.AppListAdapter;
import org.fdroid.fdroid.views.AvailableAppListAdapter;

public class SearchResults extends ListActivity {

    private static final int REQUEST_APPDETAILS = 0;

    private static final int SEARCH = Menu.FIRST;

    private AppListAdapter applist;

    private String mQuery;

    protected void getQuery(Intent intent) {
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            mQuery = intent.getStringExtra(SearchManager.QUERY);
        } else {
            Uri data = intent.getData();
            if (data.isHierarchical()) {
                mQuery = data.getQueryParameter("q");
                if (mQuery.startsWith("pname:"))
                    mQuery = mQuery.substring(6);
            } else {
                mQuery = data.getEncodedSchemeSpecificPart();
            }
        }
        if (mQuery == null || mQuery.length() == 0)
            finish();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {

        ((FDroidApp) getApplication()).applyTheme(this);

        super.onCreate(savedInstanceState);
        ActionBarCompat.create(this).setDisplayHomeAsUpEnabled(true);
        applist = new AvailableAppListAdapter(this);
        setContentView(R.layout.searchresults);

        // Start a search by just typing
        setDefaultKeyMode(DEFAULT_KEYS_SEARCH_LOCAL);

        getQuery(getIntent());

        updateView();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        getQuery(intent);
        super.onNewIntent(intent);
        updateView();
    }

    private void updateView() {

        List<String> matchingids = new ArrayList<String>();
        try {
            DB db = DB.getDB();
            matchingids = db.doSearch(mQuery);
        } catch (Exception ex) {
            Log.d("FDroid", "Search failed - " + ex.getMessage());
        } finally {
            DB.releaseDB();
        }

        List<DB.App> apps = new ArrayList<DB.App>();
        List<DB.App> allApps = ((FDroidApp) getApplication()).getApps();
        for (DB.App app : allApps) {
            boolean include = false;
            for (String id : matchingids) {
                if (id.equals(app.id)) {
                    apps.add(app);
                    break;
                }
            }
        }

        TextView tv = (TextView) findViewById(R.id.description);
        String headertext;
        if (apps.size() == 0) {
            headertext = String.format(getString(R.string.searchres_noapps),
                    mQuery);
        } else if (apps.size() == 1) {
            headertext = String.format(getString(R.string.searchres_oneapp),
                    mQuery);
        } else {
            headertext = String.format(getString(R.string.searchres_napps),
                    apps.size(), mQuery);
        }
        tv.setText(headertext);
        Log.d("FDroid", "Search for '" + mQuery + "' returned " + apps.size()
                + " results");
        applist.clear();
        for (DB.App app : apps) {
            applist.addItem(app);
        }
        getListView().setFastScrollEnabled(true);
        applist.notifyDataSetChanged();
        setListAdapter(applist);

    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        final DB.App app;
        app = (DB.App) applist.getItem(position);

        Intent intent = new Intent(this, AppDetails.class);
        intent.putExtra("appid", app.id);
        startActivityForResult(intent, REQUEST_APPDETAILS);
        super.onListItemClick(l, v, position, id);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        super.onCreateOptionsMenu(menu);
        MenuItem search = menu.add(Menu.NONE, SEARCH, 1, R.string.menu_search).setIcon(
                android.R.drawable.ic_menu_search);
        MenuItemCompat.setShowAsAction(search, MenuItemCompat.SHOW_AS_ACTION_ALWAYS);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {

        case android.R.id.home:
            NavUtils.navigateUpFromSameTask(this);
            return true;

        case SEARCH:
            onSearchRequested();
            return true;

        }
        return super.onOptionsItemSelected(item);
    }

}
