/*
 * Copyright (C) 2010-12  Ciaran Gultnieks, ciaran@ciarang.com
 * Copyright (C) 2013 Stefan Völkel, bd@bc-bd.org
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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.Signature;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.NavUtils;
import android.support.v4.view.MenuItemCompat;
import android.text.Editable;
import android.text.Html;
import android.text.Html.TagHandler;
import android.text.Spanned;
import android.text.format.DateFormat;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.assist.ImageScaleType;
import org.fdroid.fdroid.Utils.CommaSeparatedList;
import org.fdroid.fdroid.compat.ActionBarCompat;
import org.fdroid.fdroid.compat.MenuManager;
import org.fdroid.fdroid.compat.PackageManagerCompat;
import org.fdroid.fdroid.data.Apk;
import org.fdroid.fdroid.data.ApkProvider;
import org.fdroid.fdroid.data.App;
import org.fdroid.fdroid.data.AppProvider;
import org.fdroid.fdroid.data.Repo;
import org.fdroid.fdroid.data.RepoProvider;
import org.fdroid.fdroid.installer.Installer;
import org.fdroid.fdroid.installer.Installer.AndroidNotCompatibleException;
import org.fdroid.fdroid.installer.Installer.InstallerCallback;
import org.fdroid.fdroid.net.Downloader;
import org.xml.sax.XMLReader;

import java.io.File;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.List;

public class AppDetails extends ListActivity implements ProgressListener {
    private static final String TAG = "AppDetails";

    public static final int REQUEST_ENABLE_BLUETOOTH = 2;

    public static final String EXTRA_APPID = "appid";
    public static final String EXTRA_FROM = "from";

    private FDroidApp fdroidApp;
    private ApkListAdapter adapter;
    private ProgressDialog progressDialog;

    private static class ViewHolder {
        TextView version;
        TextView status;
        TextView size;
        TextView api;
        TextView incompatibleReasons;
        TextView buildtype;
        TextView added;
        TextView nativecode;
    }
    
    // observer to update view when package has been installed/deleted
    AppObserver myAppObserver;
    class AppObserver extends ContentObserver {
       public AppObserver(Handler handler) {
          super(handler);           
       }

       @Override
       public void onChange(boolean selfChange) {
          this.onChange(selfChange, null);
       }        

       @Override
       public void onChange(boolean selfChange, Uri uri) {
           if (!reset()) {
               AppDetails.this.finish();
               return;
           }
           updateViews();

           MenuManager.create(AppDetails.this).invalidateOptionsMenu();
       }        
    }


    private class ApkListAdapter extends ArrayAdapter<Apk> {

        private LayoutInflater mInflater = (LayoutInflater) mctx.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);

        public ApkListAdapter(Context context, App app) {
            super(context, 0);
            List<Apk> apks = ApkProvider.Helper.findByApp(context, app.id);
            for (Apk apk : apks ) {
                if (apk.compatible || pref_incompatibleVersions) {
                    add(apk);
                }
            }

        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            java.text.DateFormat df = DateFormat.getDateFormat(mctx);
            Apk apk = getItem(position);
            ViewHolder holder;

            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.apklistitem, null);

                holder = new ViewHolder();
                holder.version = (TextView) convertView.findViewById(R.id.version);
                holder.status = (TextView) convertView.findViewById(R.id.status);
                holder.size = (TextView) convertView.findViewById(R.id.size);
                holder.api = (TextView) convertView.findViewById(R.id.api);
                holder.incompatibleReasons = (TextView) convertView.findViewById(R.id.incompatible_reasons);
                holder.buildtype = (TextView) convertView.findViewById(R.id.buildtype);
                holder.added = (TextView) convertView.findViewById(R.id.added);
                holder.nativecode = (TextView) convertView.findViewById(R.id.nativecode);

                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            holder.version.setText(getString(R.string.version)
                    + " " + apk.version
                    + (apk.vercode == app.suggestedVercode ? "  ☆" : ""));

            if (apk.vercode == app.installedVersionCode
                    && mInstalledSigID != null && apk.sig != null
                    && apk.sig.equals(mInstalledSigID)) {
                holder.status.setText(getString(R.string.inst));
            } else {
                holder.status.setText(getString(R.string.not_inst));
            }

            if (apk.size > 0) {
                holder.size.setText(Utils.getFriendlySize(apk.size));
                holder.size.setVisibility(View.VISIBLE);
            } else {
                holder.size.setVisibility(View.GONE);
            }

            if (!pref_expert) {
                holder.api.setVisibility(View.GONE);
            } else if (apk.minSdkVersion > 0 && apk.maxSdkVersion > 0) {
                holder.api.setText(getString(R.string.minsdk_up_to_maxsdk,
                            Utils.getAndroidVersionName(apk.minSdkVersion),
                            Utils.getAndroidVersionName(apk.maxSdkVersion)));
                holder.api.setVisibility(View.VISIBLE);
            } else if (apk.minSdkVersion > 0) {
                holder.api.setText(getString(R.string.minsdk_or_later,
                            Utils.getAndroidVersionName(apk.minSdkVersion)));
                holder.api.setVisibility(View.VISIBLE);
            } else if (apk.maxSdkVersion > 0) {
                holder.api.setText(getString(R.string.up_to_maxsdk,
                            Utils.getAndroidVersionName(apk.maxSdkVersion)));
                holder.api.setVisibility(View.VISIBLE);
            }

            if (apk.srcname != null) {
                holder.buildtype.setText("source");
            } else {
                holder.buildtype.setText("bin");
            }

            if (apk.added != null) {
                holder.added.setText(getString(R.string.added_on,
                            df.format(apk.added)));
                holder.added.setVisibility(View.VISIBLE);
            } else {
                holder.added.setVisibility(View.GONE);
            }

            if (pref_expert && apk.nativecode != null) {
                holder.nativecode.setText(apk.nativecode.toString().replaceAll(","," "));
                holder.nativecode.setVisibility(View.VISIBLE);
            } else {
                holder.nativecode.setVisibility(View.GONE);
            }

            if (apk.incompatible_reasons != null) {
                holder.incompatibleReasons.setText(
                    getResources().getString(
                        R.string.requires_features,
                        apk.incompatible_reasons.toPrettyString()));
                holder.incompatibleReasons.setVisibility(View.VISIBLE);
            } else {
                holder.incompatibleReasons.setVisibility(View.GONE);
            }

            // Disable it all if it isn't compatible...
            View[] views = {
                convertView,
                holder.version,
                holder.status,
                holder.size,
                holder.api,
                holder.buildtype,
                holder.added,
                holder.nativecode
            };

            for (View v : views) {
                v.setEnabled(apk.compatible);
            }

            return convertView;
        }
    }

    private static final int INSTALL = Menu.FIRST;
    private static final int UNINSTALL = Menu.FIRST + 1;
    private static final int IGNOREALL = Menu.FIRST + 2;
    private static final int IGNORETHIS = Menu.FIRST + 3;
    private static final int WEBSITE = Menu.FIRST + 4;
    private static final int ISSUES = Menu.FIRST + 5;
    private static final int SOURCE = Menu.FIRST + 6;
    private static final int LAUNCH = Menu.FIRST + 7;
    private static final int SHARE = Menu.FIRST + 8;
    private static final int DONATE = Menu.FIRST + 9;
    private static final int BITCOIN = Menu.FIRST + 10;
    private static final int LITECOIN = Menu.FIRST + 11;
    private static final int DOGECOIN = Menu.FIRST + 12;
    private static final int FLATTR = Menu.FIRST + 13;
    private static final int DONATE_URL = Menu.FIRST + 14;
    private static final int SEND_VIA_BLUETOOTH = Menu.FIRST + 15;

    private App app;
    private String appid;
    private PackageManager mPm;
    private ApkDownloader downloadHandler;
    private boolean stateRetained;

    private boolean startingIgnoreAll;
    private int startingIgnoreThis;

    LinearLayout headerView;
    View infoView;

    private final Context mctx = this;
    private DisplayImageOptions displayImageOptions;
    private Installer installer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        
        fdroidApp = ((FDroidApp) getApplication());
        fdroidApp.applyTheme(this);

        super.onCreate(savedInstanceState);

        displayImageOptions = new DisplayImageOptions.Builder()
            .cacheInMemory(true)
            .cacheOnDisk(true)
            .imageScaleType(ImageScaleType.NONE)
            .showImageOnLoading(R.drawable.ic_repo_app_default)
            .showImageForEmptyUri(R.drawable.ic_repo_app_default)
            .bitmapConfig(Bitmap.Config.RGB_565)
            .build();

        setContentView(R.layout.appdetails);

        // Actionbar cannot be accessed until after setContentView (on 3.0 and 3.1 devices)
        // see: http://blog.perpetumdesign.com/2011/08/strange-case-of-dr-action-and-mr-bar.html
        // for reason why.
        ActionBarCompat.create(this).setDisplayHomeAsUpEnabled(true);

        Intent i = getIntent();
        Uri data = i.getData();
        if (data != null) {
            if (data.isHierarchical()) {
                if (data.getHost() != null && data.getHost().equals("details")) {
                    // market://details?id=app.id
                    appid = data.getQueryParameter("id");
                } else {
                    // https://f-droid.org/app/app.id
                    appid = data.getLastPathSegment();
                    if (appid != null && appid.equals("app")) appid = null;
                }
            } else {
                // fdroid.app:app.id
                appid = data.getEncodedSchemeSpecificPart();
            }
            Log.d("FDroid", "AppDetails launched from link, for '" + appid + "'");
        } else if (!i.hasExtra(EXTRA_APPID)) {
            Log.d("FDroid", "No application ID in AppDetails!?");
        } else {
            appid = i.getStringExtra(EXTRA_APPID);
        }

        if (i.hasExtra(EXTRA_FROM)) {
            setTitle(i.getStringExtra(EXTRA_FROM));
        }

        mPm = getPackageManager();
        installer = Installer.getActivityInstaller(this, mPm,
                myInstallerCallback);
        
        // Get the preferences we're going to use in this Activity...
        AppDetails old = (AppDetails) getLastNonConfigurationInstance();
        if (old != null) {
            copyState(old);
        } else {
            if (!reset()) {
                finish();
                return;
            }
        }

        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(getBaseContext());
        pref_expert = prefs.getBoolean(Preferences.PREF_EXPERT, false);
        pref_permissions = prefs.getBoolean(Preferences.PREF_PERMISSIONS, false);
        pref_incompatibleVersions = prefs.getBoolean(
                Preferences.PREF_INCOMP_VER, false);

        // Set up the list...
        headerView = new LinearLayout(this);
        ListView lv = (ListView) findViewById(android.R.id.list);
        lv.addHeaderView(headerView);
        adapter = new ApkListAdapter(this, app);
        setListAdapter(adapter);

        startViews();

    }

    private boolean pref_expert;
    private boolean pref_permissions;
    private boolean pref_incompatibleVersions;

    // The signature of the installed version.
    private Signature mInstalledSignature;
    private String mInstalledSigID;

    @Override
    protected void onResume() {
        Log.d(TAG, "onresume");
        super.onResume();
        
        // register observer to know when install status changes
        myAppObserver = new AppObserver(new Handler());
        getContentResolver().registerContentObserver(
              AppProvider.getContentUri(app.id),
              true,
              myAppObserver);
        
        if (!reset()) {
            finish();
            return;
        }
        updateViews();

        MenuManager.create(this).invalidateOptionsMenu();
    }

    @Override
    protected void onPause() {
        if (myAppObserver != null) {
            getContentResolver().unregisterContentObserver(myAppObserver);
        }
        if (app != null && (app.ignoreAllUpdates != startingIgnoreAll
                || app.ignoreThisUpdate != startingIgnoreThis)) {
            setIgnoreUpdates(app.id, app.ignoreAllUpdates, app.ignoreThisUpdate);
        }
        super.onPause();
    }

    public void setIgnoreUpdates(String appId, boolean ignoreAll, int ignoreVersionCode) {

        Uri uri = AppProvider.getContentUri(appId);

        ContentValues values = new ContentValues(2);
        values.put(AppProvider.DataColumns.IGNORE_ALLUPDATES, ignoreAll ? 1 : 0);
        values.put(AppProvider.DataColumns.IGNORE_THISUPDATE, ignoreVersionCode);

        getContentResolver().update(uri, values, null, null);

    }


    @Override
    public Object onRetainNonConfigurationInstance() {
        stateRetained = true;
        return this;
    }

    @Override
    protected void onDestroy() {
        // TODO: Generally need to verify the downloader stuff plays well with orientation changes...
        if (downloadHandler != null) {
            if (!stateRetained)
                downloadHandler.cancel();
            removeProgressDialog();
        }
        super.onDestroy();
    }

    private void removeProgressDialog() {
        if (progressDialog != null) {
            progressDialog.dismiss();
            progressDialog = null;
        }
    }

    // Copy all relevant state from an old instance. This is used in
    // place of reset(), so it must initialize all fields normally set
    // there.
    private void copyState(AppDetails old) {
        // TODO: Reimplement copyState with the new downloader stuff... But really, if we start to use fragments for
        // this view, then it will probably not be relevant any more...
        /*
        if (old.downloadHandler != null)
            downloadHandler = new DownloadHandler(old.downloadHandler);
        app = old.app;
        mInstalledSignature = old.mInstalledSignature;
        mInstalledSigID = old.mInstalledSigID;
        */
    }

    // Reset the display and list contents. Used when entering the activity, and
    // also when something has been installed/uninstalled.
    // Return true if the app was found, false otherwise.
    private boolean reset() {

        Log.d("FDroid", "Getting application details for " + appid);
        app = null;

        if (appid != null && appid.length() > 0) {
            app = AppProvider.Helper.findById(getContentResolver(), appid);
        }

        if (app == null) {
            Toast toast = Toast.makeText(this,
                    getString(R.string.no_such_app), Toast.LENGTH_LONG);
            toast.show();
            finish();
            return false;
        }

        startingIgnoreAll = app.ignoreAllUpdates;
        startingIgnoreThis = app.ignoreThisUpdate;

        // Get the signature of the installed package...
        mInstalledSignature = null;
        mInstalledSigID = null;
        if (app.isInstalled()) {
            PackageManager pm = getBaseContext().getPackageManager();
            try {
                PackageInfo pi = pm.getPackageInfo(appid,
                        PackageManager.GET_SIGNATURES);
                mInstalledSignature = pi.signatures[0];
                Hasher hash = new Hasher("MD5", mInstalledSignature
                        .toCharsString().getBytes());
                mInstalledSigID = hash.getHash();
            } catch (NameNotFoundException e) {
                Log.d("FDroid", "Failed to get installed signature");
            } catch (NoSuchAlgorithmException e) {
                Log.d("FDroid", "Failed to calculate signature MD5 sum");
                mInstalledSignature = null;
            }
        }
        return true;
    }

    private void startViews() {

        // Insert the 'infoView' (which contains the summary, various odds and
        // ends, and the description) into the appropriate place, if we're in
        // landscape mode. In portrait mode, we put it in the listview's
        // header..
        infoView = View.inflate(this, R.layout.appinfo, null);
        LinearLayout landparent = (LinearLayout) findViewById(R.id.landleft);
        headerView.removeAllViews();
        if (landparent != null) {
            landparent.addView(infoView);
            Log.d("FDroid", "Setting landparent infoview");
        } else {
            headerView.addView(infoView);
            Log.d("FDroid", "Setting header infoview");
        }

        // Set the icon...
        ImageView iv = (ImageView) findViewById(R.id.icon);
        ImageLoader.getInstance().displayImage(app.iconUrl, iv,
            displayImageOptions);

        // Set the title and other header details...
        TextView tv = (TextView) findViewById(R.id.title);
        tv.setText(app.name);
        tv = (TextView) findViewById(R.id.license);
        tv.setText(app.license);

        if (app.categories != null) {
            tv = (TextView) findViewById(R.id.categories);
            tv.setText(app.categories.toString().replaceAll(",",", "));
        }

        tv = (TextView) infoView.findViewById(R.id.description);

        tv.setMovementMethod(LinkMovementMethod.getInstance());

        // Need this to add the unimplemented support for ordered and unordered
        // lists to Html.fromHtml().
        class HtmlTagHandler implements TagHandler {
            int listNum;

            @Override
            public void handleTag(boolean opening, String tag, Editable output,
                    XMLReader reader) {
                if (tag.equals("ul")) {
                    if (opening)
                        listNum = -1;
                    else
                        output.append('\n');
                } else if (opening && tag.equals("ol")) {
                    if (opening)
                        listNum = 1;
                    else
                        output.append('\n');
                } else if (tag.equals("li")) {
                    if (opening) {
                        if (listNum == -1) {
                            output.append("\t• ");
                        } else {
                            output.append("\t").append(Integer.toString(listNum)).append(". ");
                            listNum++;
                        }
                    } else {
                        output.append('\n');
                    }
                }
            }
        }
        Spanned desc = Html.fromHtml(
                app.description, null, new HtmlTagHandler());
        tv.setText(desc.subSequence(0, desc.length() - 2));

        tv = (TextView) infoView.findViewById(R.id.appid);
        if (pref_expert)
            tv.setText(app.id);
        else
            tv.setVisibility(View.GONE);

        tv = (TextView) infoView.findViewById(R.id.summary);
        tv.setText(app.summary);

        Apk curApk = null;
        for (int i = 0; i < adapter.getCount(); i ++) {
            Apk apk = adapter.getItem(i);
            if (apk.vercode == app.suggestedVercode) {
                curApk = apk;
                break;
            }
        }

        if (pref_permissions && !adapter.isEmpty() &&
                ((curApk != null && curApk.compatible) || pref_incompatibleVersions)) {
            tv = (TextView) infoView.findViewById(R.id.permissions_list);

            CommaSeparatedList permsList = adapter.getItem(0).permissions;
            if (permsList == null) {
                tv.setText(getString(R.string.no_permissions));
            } else {
                Iterator<String> permissions = permsList.iterator();
                StringBuilder sb = new StringBuilder();
                while (permissions.hasNext()) {
                    String permissionName = permissions.next();
                    try {
                        Permission permission = new Permission(this, permissionName);
                        sb.append("\t• ").append(permission.getName()).append('\n');
                    } catch (NameNotFoundException e) {
                        if (permissionName.equals("ACCESS_SUPERUSER")) {
                            sb.append("\t• Full permissions to all device features and storage\n");
                        } else {
                            Log.d("FDroid", "Permission not yet available: "
                                    +permissionName);
                        }
                    }
                }
                if (sb.length() > 0) sb.setLength(sb.length() - 1);
                tv.setText(sb.toString());
            }
            tv = (TextView) infoView.findViewById(R.id.permissions);
            tv.setText(getString(
                    R.string.permissions_for_long, adapter.getItem(0).version));
        } else {
            infoView.findViewById(R.id.permissions).setVisibility(View.GONE);
            infoView.findViewById(R.id.permissions_list).setVisibility(View.GONE);
        }

        tv = (TextView) infoView.findViewById(R.id.antifeatures);
        if (app.antiFeatures != null) {
            StringBuilder sb = new StringBuilder();
            for (String af : app.antiFeatures) {
                String afdesc = descAntiFeature(af);
                if (afdesc != null) {
                    sb.append("\t• ").append(afdesc).append("\n");
                }
            }
            if (sb.length() > 0) {
                sb.setLength(sb.length() - 1);
                tv.setText(sb.toString());
            } else {
                tv.setVisibility(View.GONE);
            }
        } else {
            tv.setVisibility(View.GONE);
        }
    }

    private String descAntiFeature(String af) {
        if (af.equals("Ads"))
            return getString(R.string.antiadslist);
        if (af.equals("Tracking"))
            return getString(R.string.antitracklist);
        if (af.equals("NonFreeNet"))
            return getString(R.string.antinonfreenetlist);
        if (af.equals("NonFreeAdd"))
            return getString(R.string.antinonfreeadlist);
        if (af.equals("NonFreeDep"))
            return getString(R.string.antinonfreedeplist);
        if (af.equals("UpstreamNonFree"))
            return getString(R.string.antiupstreamnonfreelist);
        return null;
    }

    private void updateViews() {

        // Refresh the list...
        adapter.notifyDataSetChanged();

        TextView tv = (TextView) findViewById(R.id.status);
        if (app.isInstalled()) {
            tv.setText(getString(R.string.details_installed,
                    app.installedVersionName));
            NfcBeamManager.setAndroidBeam(this, app.id);
        } else {
            tv.setText(getString(R.string.details_notinstalled));
            NfcBeamManager.disableAndroidBeam(this);
        }

        tv = (TextView) infoView.findViewById(R.id.signature);
        if (pref_expert && mInstalledSignature != null) {
            tv.setVisibility(View.VISIBLE);
            tv.setText("Signed: " + mInstalledSigID);
        } else {
            tv.setVisibility(View.GONE);
        }

    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        final Apk apk = adapter.getItem(position - l.getHeaderViewsCount());
        if (app.installedVersionCode == apk.vercode)
            removeApk(app.id);
        else if (app.installedVersionCode > apk.vercode) {
            AlertDialog.Builder ask_alrt = new AlertDialog.Builder(this);
            ask_alrt.setMessage(getString(R.string.installDowngrade));
            ask_alrt.setPositiveButton(getString(R.string.yes),
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog,
                                int whichButton) {
                            install(apk);
                        }
                    });
            ask_alrt.setNegativeButton(getString(R.string.no),
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog,
                                int whichButton) {
                        }
                    });
            AlertDialog alert = ask_alrt.create();
            alert.show();
        } else
            install(apk);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {

        super.onCreateOptionsMenu(menu);
        menu.clear();
        if (app == null)
            return true;
        if (app.canAndWantToUpdate()) {
            MenuItemCompat.setShowAsAction(menu.add(
                        Menu.NONE, INSTALL, 0, R.string.menu_upgrade)
                        .setIcon(R.drawable.ic_menu_refresh),
                    MenuItemCompat.SHOW_AS_ACTION_ALWAYS |
                    MenuItemCompat.SHOW_AS_ACTION_WITH_TEXT);
        }

        // Check count > 0 due to incompatible apps resulting in an empty list.
        if (!app.isInstalled() && app.suggestedVercode > 0 &&
                adapter.getCount() > 0) {
            MenuItemCompat.setShowAsAction(menu.add(
                        Menu.NONE, INSTALL, 1, R.string.menu_install)
                        .setIcon(android.R.drawable.ic_menu_add),
                    MenuItemCompat.SHOW_AS_ACTION_ALWAYS |
                    MenuItemCompat.SHOW_AS_ACTION_WITH_TEXT);
        } else if (app.isInstalled()) {
            MenuItemCompat.setShowAsAction(menu.add(
                        Menu.NONE, UNINSTALL, 1, R.string.menu_uninstall)
                        .setIcon(android.R.drawable.ic_menu_delete),
                    MenuItemCompat.SHOW_AS_ACTION_IF_ROOM |
                    MenuItemCompat.SHOW_AS_ACTION_WITH_TEXT);

            if (mPm.getLaunchIntentForPackage(app.id) != null) {
                MenuItemCompat.setShowAsAction(menu.add(
                            Menu.NONE, LAUNCH, 1, R.string.menu_launch)
                            .setIcon(android.R.drawable.ic_media_play),
                        MenuItemCompat.SHOW_AS_ACTION_ALWAYS |
                        MenuItemCompat.SHOW_AS_ACTION_WITH_TEXT);
            }
        }

        MenuItemCompat.setShowAsAction(menu.add(
                    Menu.NONE, SHARE, 1, R.string.menu_share)
                    .setIcon(android.R.drawable.ic_menu_share),
                MenuItemCompat.SHOW_AS_ACTION_IF_ROOM |
                MenuItemCompat.SHOW_AS_ACTION_WITH_TEXT);

        menu.add(Menu.NONE, IGNOREALL, 2, R.string.menu_ignore_all)
                    .setIcon(android.R.drawable.ic_menu_close_clear_cancel)
                    .setCheckable(true)
                    .setChecked(app.ignoreAllUpdates);

        if (app.hasUpdates()) {
            menu.add(Menu.NONE, IGNORETHIS, 2, R.string.menu_ignore_this)
                        .setIcon(android.R.drawable.ic_menu_close_clear_cancel)
                        .setCheckable(true)
                        .setChecked(app.ignoreThisUpdate >= app.suggestedVercode);
        }
        if (app.webURL.length() > 0) {
            menu.add(Menu.NONE, WEBSITE, 3, R.string.menu_website).setIcon(
                    android.R.drawable.ic_menu_view);
        }
        if (app.trackerURL.length() > 0) {
            menu.add(Menu.NONE, ISSUES, 4, R.string.menu_issues).setIcon(
                    android.R.drawable.ic_menu_view);
        }
        if (app.sourceURL.length() > 0) {
            menu.add(Menu.NONE, SOURCE, 5, R.string.menu_source).setIcon(
                    android.R.drawable.ic_menu_view);
        }

        if (app.bitcoinAddr != null || app.litecoinAddr != null ||
                app.dogecoinAddr != null ||
                app.flattrID != null || app.donateURL != null) {
            SubMenu donate = menu.addSubMenu(Menu.NONE, DONATE, 7,
                    R.string.menu_donate).setIcon(
                    android.R.drawable.ic_menu_send);
            if (app.bitcoinAddr != null)
                donate.add(Menu.NONE, BITCOIN, 8, R.string.menu_bitcoin);
            if (app.litecoinAddr != null)
                donate.add(Menu.NONE, LITECOIN, 8, R.string.menu_litecoin);
            if (app.dogecoinAddr != null)
                donate.add(Menu.NONE, DOGECOIN, 8, R.string.menu_dogecoin);
            if (app.flattrID != null)
                donate.add(Menu.NONE, FLATTR, 9, R.string.menu_flattr);
            if (app.donateURL != null)
                donate.add(Menu.NONE, DONATE_URL, 10, R.string.menu_website);
        }
        if (app.isInstalled() && fdroidApp.bluetoothAdapter != null) { // ignore on devices without Bluetooth
            menu.add(Menu.NONE, SEND_VIA_BLUETOOTH, 6, R.string.send_via_bluetooth);
        }

        return true;
    }


    public void tryOpenUri(String s) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(s));
        if (intent.resolveActivity(getPackageManager()) == null) {
            Toast.makeText(this,
                    getString(R.string.no_handler_app, intent.getDataString()),
                    Toast.LENGTH_LONG).show();
            return;
        }
        startActivity(intent);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {

        case android.R.id.home:
            NavUtils.navigateUpFromSameTask(this);
            return true;

        case LAUNCH:
            launchApk(app.id);
            return true;

        case SHARE:
            shareApp(app);
            return true;

        case INSTALL:
            // Note that this handles updating as well as installing.
            if (app.suggestedVercode > 0) {
                final Apk apkToInstall = ApkProvider.Helper.find(this, app.id, app.suggestedVercode);
                install(apkToInstall);
            }
            return true;

        case UNINSTALL:
            removeApk(app.id);
            return true;

        case IGNOREALL:
            app.ignoreAllUpdates ^= true;
            item.setChecked(app.ignoreAllUpdates);
            return true;

        case IGNORETHIS:
            if (app.ignoreThisUpdate >= app.suggestedVercode)
                app.ignoreThisUpdate = 0;
            else
                app.ignoreThisUpdate = app.suggestedVercode;
            item.setChecked(app.ignoreThisUpdate > 0);
            return true;

        case WEBSITE:
            tryOpenUri(app.webURL);
            return true;

        case ISSUES:
            tryOpenUri(app.trackerURL);
            return true;

        case SOURCE:
            tryOpenUri(app.sourceURL);
            return true;

        case BITCOIN:
            tryOpenUri("bitcoin:" + app.bitcoinAddr);
            return true;

        case LITECOIN:
            tryOpenUri("litecoin:" + app.litecoinAddr);
            return true;

        case DOGECOIN:
            tryOpenUri("dogecoin:" + app.dogecoinAddr);
            return true;

        case FLATTR:
            tryOpenUri("https://flattr.com/thing/" + app.flattrID);
            return true;

        case DONATE_URL:
            tryOpenUri(app.donateURL);
            return true;

        case SEND_VIA_BLUETOOTH:
            /*
             * If Bluetooth has not been enabled/turned on, then
             * enabling device discoverability will automatically enable Bluetooth
             */
            Intent discoverBt = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverBt.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 121);
            startActivityForResult(discoverBt, REQUEST_ENABLE_BLUETOOTH);
            // if this is successful, the Bluetooth transfer is started
            return true;

        }
        return super.onOptionsItemSelected(item);
    }

    // Install the version of this app denoted by 'app.curApk'.
    private void install(final Apk apk) {
        final Activity activity = this;
        String [] projection = { RepoProvider.DataColumns.ADDRESS };
        Repo repo = RepoProvider.Helper.findById(this, apk.repo, projection);
        if (repo == null || repo.address == null) {
            return;
        }
        final String repoaddress = repo.address;

        if (!apk.compatible) {
            AlertDialog.Builder ask_alrt = new AlertDialog.Builder(this);
            ask_alrt.setMessage(getString(R.string.installIncompatible));
            ask_alrt.setPositiveButton(getString(R.string.yes),
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog,
                            int whichButton) {
                            startDownload(apk, repoaddress);
                        }
                    });
            ask_alrt.setNegativeButton(getString(R.string.no),
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog,
                                int whichButton) {
                        }
                    });
            AlertDialog alert = ask_alrt.create();
            alert.show();
            return;
        }
        if (mInstalledSigID != null && apk.sig != null
                && !apk.sig.equals(mInstalledSigID)) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(R.string.SignatureMismatch).setPositiveButton(
                    getString(R.string.ok),
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.cancel();
                        }
                    });
            AlertDialog alert = builder.create();
            alert.show();
            return;
        }
        startDownload(apk, repoaddress);
    }

    private void startDownload(Apk apk, String repoAddress) {
        downloadHandler = new ApkDownloader(apk, repoAddress, Utils.getApkCacheDir(getBaseContext()));
        getProgressDialog(downloadHandler.getRemoteAddress()).show();
        downloadHandler.setProgressListener(this);
        downloadHandler.download();
    }
    private void installApk(File file, String packageName) {
        setProgressBarIndeterminateVisibility(true);

        try {
            installer.installPackage(file);
        } catch (AndroidNotCompatibleException e) {
            Log.e(TAG, "Android not compatible with this Installer!", e);
        }
    }

    private void removeApk(String packageName) {
        setProgressBarIndeterminateVisibility(true);

        try {
            installer.deletePackage(packageName);
        } catch (AndroidNotCompatibleException e) {
            Log.e(TAG, "Android not compatible with this Installer!", e);
        }
    }

    Installer.InstallerCallback myInstallerCallback = new Installer.InstallerCallback() {

        @Override
        public void onSuccess(final int operation) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {                    
                    if (operation == Installer.InstallerCallback.OPERATION_INSTALL) {
                        if (downloadHandler != null) {
                            downloadHandler = null;
                        }

                        PackageManagerCompat.setInstaller(mPm, app.id);
                    }

                    setProgressBarIndeterminateVisibility(false);
                }
            });
        }

        @Override
        public void onError(int operation, final int errorCode) {
            if (errorCode == InstallerCallback.ERROR_CODE_CANCELED) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setProgressBarIndeterminateVisibility(false);
                    }
                });
            } else {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setProgressBarIndeterminateVisibility(false);
                        
                        Log.e(TAG, "Installer aborted with errorCode: " + errorCode);
                        
                        AlertDialog.Builder alertBuilder = new AlertDialog.Builder(AppDetails.this);
                        alertBuilder.setTitle(R.string.installer_error_title);
                        alertBuilder.setMessage(R.string.installer_error_title);
                        alertBuilder.setNeutralButton(android.R.string.ok, null);
                        alertBuilder.create().show();
                    }
                });
            }
        }
    };

    private void launchApk(String id) {
        Intent intent = mPm.getLaunchIntentForPackage(id);
        startActivity(intent);
    }

    private void shareApp(App app) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");

        shareIntent.putExtra(Intent.EXTRA_SUBJECT, app.name);
        shareIntent.putExtra(Intent.EXTRA_TEXT, app.name + " (" + app.summary + ") - https://f-droid.org/app/" + app.id);

        startActivity(Intent.createChooser(shareIntent, getString(R.string.menu_share)));
    }

    private ProgressDialog getProgressDialog(String file) {
        if (progressDialog == null) {
            final ProgressDialog pd = new ProgressDialog(this);
            pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            pd.setMessage(getString(R.string.download_server) + ":\n " + file);
            pd.setCancelable(true);
            pd.setCanceledOnTouchOutside(false);

            // The indeterminate-ness will get overridden on the first progress event we receive.
            pd.setIndeterminate(true);

            pd.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    downloadHandler.cancel();
                    progressDialog = null;
                    Toast.makeText(AppDetails.this, getString(R.string.download_cancelled), Toast.LENGTH_LONG).show();
                }
            });
            pd.setButton(DialogInterface.BUTTON_NEUTRAL,
                    getString(R.string.cancel),
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            pd.cancel();
                        }
                    }
            );
            progressDialog = pd;
        }
        return progressDialog;
    }

    private void updateProgressDialog(int progress, int total) {
        ProgressDialog pd = getProgressDialog(downloadHandler.getRemoteAddress());
        pd.setIndeterminate(false);
        pd.setProgress(progress);
        pd.setMax(total);
    }

    @Override
    public void onProgress(Event event) {
        boolean finished = false;
        if (event.type.equals(Downloader.EVENT_PROGRESS)) {
            updateProgressDialog(event.progress, event.total);
        } else if (event.type.equals(ApkDownloader.EVENT_ERROR)) {
            final String text;
            if (event.getData().getInt(ApkDownloader.EVENT_DATA_ERROR_TYPE) == ApkDownloader.ERROR_HASH_MISMATCH)
                text = getString(R.string.corrupt_download);
            else
                text = getString(R.string.details_notinstalled);
            // this must be on the main UI thread
            Toast.makeText(this, text, Toast.LENGTH_LONG).show();
            finished = true;
        } else if (event.type.equals(ApkDownloader.EVENT_APK_DOWNLOAD_COMPLETE)) {
            installApk(downloadHandler.localFile(), downloadHandler.getApk().id);
            finished = true;
        }

        if (finished) {
            // The dialog can't be dismissed when it's not displayed,
            // so do it when the activity is being destroyed.
            removeProgressDialog();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // handle cases for install manager first
        if (installer.handleOnActivityResult(requestCode, resultCode, data)) {
            return;
        }
        
        switch (requestCode) {
        case REQUEST_ENABLE_BLUETOOTH:
            fdroidApp.sendViaBluetooth(this, resultCode, app.id);
			break;
        }
    }
}
