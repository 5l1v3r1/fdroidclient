/*
 * Copyright (C) 2010-12  Ciaran Gultnieks, ciaran@ciarang.com
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

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;

import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;

public class UpdateService extends IntentService implements ProgressListener {

    public static final String RESULT_MESSAGE = "msg";
    public static final int STATUS_COMPLETE = 0;
    public static final int STATUS_ERROR = 1;
    public static final int STATUS_INFO = 2;

    private ResultReceiver receiver = null;

    public UpdateService() {
        super("UpdateService");
    }

    // Schedule (or cancel schedule for) this service, according to the
    // current preferences. Should be called a) at boot, b) if the preference
    // is changed, or c) on startup, in case we get upgraded.
    public static void schedule(Context ctx) {

        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(ctx);
        String sint = prefs.getString("updateInterval", "0");
        int interval = Integer.parseInt(sint);

        Intent intent = new Intent(ctx, UpdateService.class);
        PendingIntent pending = PendingIntent.getService(ctx, 0, intent, 0);

        AlarmManager alarm = (AlarmManager) ctx
                .getSystemService(Context.ALARM_SERVICE);
        alarm.cancel(pending);
        if (interval > 0) {
            alarm.setInexactRepeating(AlarmManager.ELAPSED_REALTIME,
                    SystemClock.elapsedRealtime() + 5000,
                    AlarmManager.INTERVAL_HOUR, pending);
            Log.d("FDroid", "Update scheduler alarm set");
        } else {
            Log.d("FDroid", "Update scheduler alarm not set");
        }

    }

    protected void sendStatus(int statusCode) {
        sendStatus(statusCode, null);
    }

    protected void sendStatus(int statusCode, String message) {
        if (receiver != null) {
            Bundle resultData = new Bundle();
            if (message != null && message.length() > 0)
                resultData.putString(RESULT_MESSAGE, message);
            receiver.send(statusCode, resultData);
        }
    }

    /**
     * We might be doing a scheduled run, or we might have been launched by the
     * app in response to a user's request. If we have a receiver, it's the
     * latter...
     */
    private boolean isScheduledRun() {
        return receiver == null;
    }

    // Get the number of apps that have updates available.
    public int getNumUpdates(List<DB.App> apps) {
        int count = 0;
        for (DB.App app : apps) {
            if (app.toUpdate)
                count++;
        }
        return count;
    }

    protected void onHandleIntent(Intent intent) {

        receiver = intent.getParcelableExtra("receiver");

        long startTime = System.currentTimeMillis();
        String errmsg = "";
        try {

            SharedPreferences prefs = PreferenceManager
                    .getDefaultSharedPreferences(getBaseContext());

            // See if it's time to actually do anything yet...
            if (isScheduledRun()) {
                long lastUpdate = prefs.getLong("lastUpdateCheck", 0);
                String sint = prefs.getString("updateInterval", "0");
                int interval = Integer.parseInt(sint);
                if (interval == 0) {
                    Log.d("FDroid", "Skipping update - disabled");
                    return;
                }
                long elapsed = System.currentTimeMillis() - lastUpdate;
                if (elapsed < interval * 60 * 60 * 1000) {
                    Log.d("FDroid", "Skipping update - done " + elapsed
                            + "ms ago, interval is " + interval + " hours");
                    return;
                }

                // If we are to update the repos only on wifi, make sure that
                // connection is active
                if (prefs.getBoolean("updateOnWifiOnly", false)) {
                    ConnectivityManager conMan = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                    NetworkInfo.State wifi = conMan.getNetworkInfo(1).getState();
                    if (wifi != NetworkInfo.State.CONNECTED &&
                            wifi !=  NetworkInfo.State.CONNECTING) {
                        Log.d("FDroid", "Skipping update - wifi not available");
                        return;
                    }
                }
            } else {
                Log.d("FDroid", "Unscheduled (manually requested) update");
            }

            boolean notify = prefs.getBoolean("updateNotify", false);

            // Grab some preliminary information, then we can release the
            // database while we do all the downloading, etc...
            int updates = 0;
            List<DB.Repo> repos;
            try {
                DB db = DB.getDB();
                repos = db.getRepos();
            } finally {
                DB.releaseDB();
            }

            // Process each repo...
            List<DB.App> apps;
            List<DB.App> updatingApps = new ArrayList<DB.App>();
            List<Integer> keeprepos = new ArrayList<Integer>();
            boolean success = true;
            boolean changes = false;
            for (DB.Repo repo : repos) {
                if (repo.inuse) {

                    sendStatus(
                            STATUS_INFO,
                            getString(R.string.status_connecting_to_repo,
                                    repo.address));

                    StringBuilder newetag = new StringBuilder();
                    String err = RepoXMLHandler.doUpdate(getBaseContext(),
                            repo, updatingApps, newetag, keeprepos, this);
                    if (err == null) {
                        String nt = newetag.toString();
                        if (!nt.equals(repo.lastetag)) {
                            repo.lastetag = newetag.toString();
                            changes = true;
                        }
                    } else {
                        success = false;
                        err = "Update failed for " + repo.address + " - " + err;
                        Log.d("FDroid", err);
                        if (errmsg.length() == 0)
                            errmsg = err;
                        else
                            errmsg += "\n" + err;
                    }
                }
            }

            if (!changes && success) {
                Log.d("FDroid",
                        "Not checking app details or compatibility, because all repos were up to date.");
            } else if (changes && success) {

                sendStatus(STATUS_INFO,
                        getString(R.string.status_checking_compatibility));
                apps = ((FDroidApp) getApplication()).getApps();

                DB db = DB.getDB();
                try {

                    // Need to flag things we're keeping despite having received
                    // no data about during the update. (i.e. stuff from a repo
                    // that we know is unchanged due to the etag)
                    for (int keep : keeprepos) {
                        for (DB.App app : apps) {
                            boolean keepapp = false;
                            for (DB.Apk apk : app.apks) {
                                if (apk.repo == keep) {
                                    keepapp = true;
                                    break;
                                }
                            }
                            if (keepapp) {
                                DB.App app_k = null;
                                for (DB.App app2 : updatingApps) {
                                    if (app2.id.equals(app.id)) {
                                        app_k = app2;
                                        break;
                                    }
                                }
                                if (app_k == null) {
                                    updatingApps.add(app);
                                    app_k = app;
                                }
                                app_k.updated = true;
                                db.populateDetails(app_k, keep);
                                for (DB.Apk apk : app.apks)
                                    if (apk.repo == keep)
                                        apk.updated = true;
                            }
                        }
                    }

                    db.beginUpdate(apps);
                    for (DB.App app : updatingApps) {
                        db.updateApplication(app);
                    }
                    db.endUpdate();
                    for (DB.Repo repo : repos)
                        db.writeLastEtag(repo);
                } catch (Exception ex) {
                    db.cancelUpdate();
                    Log.e("FDroid", "Exception during update processing:\n"
                            + Log.getStackTraceString(ex));
                    errmsg = "Exception during processing - " + ex.getMessage();
                    success = false;
                } finally {
                    DB.releaseDB();
                }

            }

            if (success && changes) {
                ((FDroidApp) getApplication()).invalidateAllApps();
                if (notify) {
                    apps = ((FDroidApp) getApplication()).getApps();
                    updates = getNumUpdates(apps);
                }
            }

            if (success && changes && notify && updates > 0) {
                Log.d("FDroid", "Notifying "+updates+" updates.");
                NotificationCompat.Builder mBuilder =
                    new NotificationCompat.Builder(
                        this)
                        .setAutoCancel(true)
                        .setContentTitle(
                                getString(R.string.fdroid_updates_available));
                if (Build.VERSION.SDK_INT >= 11) {
                    mBuilder.setSmallIcon(R.drawable.ic_stat_notify_updates);
                } else {
                    mBuilder.setSmallIcon(R.drawable.ic_launcher);
                }
                Intent notifyIntent = new Intent(this, FDroid.class)
                        .putExtra(FDroid.EXTRA_TAB_UPDATE, true);
                if (updates > 1) {
                    mBuilder.setContentText(getString(
                            R.string.many_updates_available, updates));

                } else {
                    mBuilder.setContentText(getString(R.string.one_update_available));
                }
                TaskStackBuilder stackBuilder = TaskStackBuilder
                        .create(this).addParentStack(FDroid.class)
                        .addNextIntent(notifyIntent);
                PendingIntent pendingIntent = stackBuilder
                        .getPendingIntent(0,
                                PendingIntent.FLAG_UPDATE_CURRENT);
                mBuilder.setContentIntent(pendingIntent);
                NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                mNotificationManager.notify(1, mBuilder.build());
            }

            if (!success) {
                if (errmsg.length() == 0)
                    errmsg = "Unknown error";
                sendStatus(STATUS_ERROR, errmsg);
            } else {
                sendStatus(STATUS_COMPLETE);
                Editor e = prefs.edit();
                e.putLong("lastUpdateCheck", System.currentTimeMillis());
                e.commit();
            }

        } catch (Exception e) {
            Log.e("FDroid",
                    "Exception during update processing:\n"
                            + Log.getStackTraceString(e));
            if (errmsg.length() == 0)
                errmsg = "Unknown error";
            sendStatus(STATUS_ERROR, errmsg);
        } finally {
            Log.d("FDroid", "Update took "
                    + ((System.currentTimeMillis() - startTime) / 1000)
                    + " seconds.");
            receiver = null;
        }
    }


    /**
     * Received progress event from the RepoXMLHandler. It could be progress
     * downloading from the repo, or perhaps processing the info from the repo.
     */
    @Override
    public void onProgress(ProgressListener.Event event) {

        String message = "";
        if (event.type == RepoXMLHandler.PROGRESS_TYPE_DOWNLOAD) {
            String repoAddress = event.data
                    .getString(RepoXMLHandler.PROGRESS_DATA_REPO);
            String downloadedSize = Utils.getFriendlySize(event.progress);
            String totalSize = Utils.getFriendlySize(event.total);
            int percent = (int) ((double) event.progress / event.total * 100);
            message = getString(R.string.status_download, repoAddress,
                    downloadedSize, totalSize, percent);
        } else if (event.type == RepoXMLHandler.PROGRESS_TYPE_PROCESS_XML) {
            String repoAddress = event.data
                    .getString(RepoXMLHandler.PROGRESS_DATA_REPO);
            message = getString(R.string.status_processing_xml, repoAddress,
                    event.progress, event.total);
        }

        sendStatus(STATUS_INFO, message);
    }
}
