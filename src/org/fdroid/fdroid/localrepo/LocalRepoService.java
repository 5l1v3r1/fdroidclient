
package org.fdroid.fdroid.localrepo;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import org.fdroid.fdroid.FDroidApp;
import org.fdroid.fdroid.Preferences;
import org.fdroid.fdroid.Preferences.ChangeListener;
import org.fdroid.fdroid.R;
import org.fdroid.fdroid.net.LocalHTTPD;
import org.fdroid.fdroid.net.WifiStateChangeService;
import org.fdroid.fdroid.views.swap.SwapActivity;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import java.io.IOException;
import java.net.BindException;
import java.util.HashMap;
import java.util.Random;

public class LocalRepoService extends Service {
    private static final String TAG = "LocalRepoService";

    public static final String STATE = "org.fdroid.fdroid.action.LOCAL_REPO_STATE";
    public static final String STARTED = "org.fdroid.fdroid.category.LOCAL_REPO_STARTED";
    public static final String STOPPED = "org.fdroid.fdroid.category.LOCAL_REPO_STOPPED";

    private NotificationManager notificationManager;
    private Notification notification;
    // Unique Identification Number for the Notification.
    // We use it on Notification start, and to cancel it.
    private int NOTIFICATION = R.string.local_repo_running;

    private Handler webServerThreadHandler = null;
    private LocalHTTPD localHttpd;
    private JmDNS jmdns;
    private ServiceInfo pairService;

    public static int START = 1111111;
    public static int STOP = 12345678;
    public static int RESTART = 87654;

    final Messenger messenger = new Messenger(new StartStopHandler(this));

    static class StartStopHandler extends Handler {
        private static LocalRepoService service;

        public StartStopHandler(LocalRepoService service) {
            StartStopHandler.service = service;
        }

        @Override
        public void handleMessage(Message msg) {
            if (msg.arg1 == START) {
                service.startNetworkServices();
            } else if (msg.arg1 == STOP) {
                service.stopNetworkServices();
            } else if (msg.arg1 == RESTART) {
                service.stopNetworkServices();
                service.startNetworkServices();
            } else {
                Log.e(TAG, "unsupported msg.arg1, ignored");
            }
        }
    }

    private BroadcastReceiver onWifiChange = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent i) {
            stopNetworkServices();
            startNetworkServices();
        }
    };

    private ChangeListener localRepoBonjourChangeListener = new ChangeListener() {
        @Override
        public void onPreferenceChange() {
            if (localHttpd.isAlive())
                if (Preferences.get().isLocalRepoBonjourEnabled())
                    registerMDNSService();
                else
                    unregisterMDNSService();
        }
    };

    private ChangeListener localRepoHttpsChangeListener = new ChangeListener() {
        @Override
        public void onPreferenceChange() {
            Log.i("localRepoHttpsChangeListener", "onPreferenceChange");
            if (localHttpd.isAlive()) {
                new AsyncTask<Void, Void, Void>() {
                    @Override
                    protected Void doInBackground(Void... params) {
                        stopNetworkServices();
                        startNetworkServices();
                        return null;
                    }
                }.execute();
            }
        }
    };

    @Override
    public void onCreate() {
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        // launch LocalRepoActivity if the user selects this notification
        Intent intent = new Intent(this, SwapActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_CANCEL_CURRENT);
        notification = new NotificationCompat.Builder(this)
                .setContentTitle(getText(R.string.local_repo_running))
                .setContentText(getText(R.string.touch_to_configure_local_repo))
                .setSmallIcon(R.drawable.ic_swap)
                .setContentIntent(contentIntent)
                .build();
        startForeground(NOTIFICATION, notification);
        startNetworkServices();
        Preferences.get().registerLocalRepoBonjourListeners(localRepoBonjourChangeListener);

        LocalBroadcastManager.getInstance(this).registerReceiver(onWifiChange,
                new IntentFilter(WifiStateChangeService.BROADCAST));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopNetworkServices();
        notificationManager.cancel(NOTIFICATION);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(onWifiChange);
        Preferences.get().unregisterLocalRepoBonjourListeners(localRepoBonjourChangeListener);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return messenger.getBinder();
    }

    private void startNetworkServices() {
        startWebServer();
        if (Preferences.get().isLocalRepoBonjourEnabled())
            registerMDNSService();
        Preferences.get().registerLocalRepoHttpsListeners(localRepoHttpsChangeListener);
    }

    private void stopNetworkServices() {
        Preferences.get().unregisterLocalRepoHttpsListeners(localRepoHttpsChangeListener);
        unregisterMDNSService();
        stopWebServer();
    }

    private void startWebServer() {
        Runnable webServer = new Runnable() {
            // Tell Eclipse this is not a leak because of Looper use.
            @SuppressLint("HandlerLeak")
            @Override
            public void run() {
                localHttpd = new LocalHTTPD(
                        LocalRepoService.this,
                        getFilesDir(),
                        Preferences.get().isLocalRepoHttpsEnabled());
                if (localHttpd == null) {
                    Log.e(TAG, "localHttpd == null!");
                    return;
                }

                Looper.prepare(); // must be run before creating a Handler
                webServerThreadHandler = new Handler() {
                    @Override
                    public void handleMessage(Message msg) {
                        Log.i(TAG, "we've been asked to stop the webserver: " + msg.obj);
                        localHttpd.stop();
                    }
                };
                try {
                    localHttpd.start();
                } catch (BindException e) {
                    int prev = FDroidApp.port;
                    FDroidApp.port = FDroidApp.port + new Random().nextInt(1111);
                    Log.w(TAG, "port " + prev + " occupied, trying on " + FDroidApp.port + "!");
                    startService(new Intent(LocalRepoService.this, WifiStateChangeService.class));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                Looper.loop(); // start the message receiving loop
            }
        };
        new Thread(webServer).start();
        Intent intent = new Intent(STATE);
        intent.putExtra(STATE, STARTED);
        LocalBroadcastManager.getInstance(LocalRepoService.this).sendBroadcast(intent);
    }

    private void stopWebServer() {
        if (webServerThreadHandler == null) {
            Log.i(TAG, "null handler in stopWebServer");
            return;
        }
        Message msg = webServerThreadHandler.obtainMessage();
        msg.obj = webServerThreadHandler.getLooper().getThread().getName() + " says stop";
        webServerThreadHandler.sendMessage(msg);
        Intent intent = new Intent(STATE);
        intent.putExtra(STATE, STOPPED);
        LocalBroadcastManager.getInstance(LocalRepoService.this).sendBroadcast(intent);
    }

    private void registerMDNSService() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                /*
                 * a ServiceInfo can only be registered with a single instance
                 * of JmDNS, and there is only ever a single LocalHTTPD port to
                 * advertise anyway.
                 */
                if (pairService != null || jmdns != null)
                    clearCurrentMDNSService();
                String repoName = Preferences.get().getLocalRepoName();
                HashMap<String, String> values = new HashMap<String, String>();
                values.put("path", "/fdroid/repo");
                values.put("name", repoName);
                values.put("fingerprint", FDroidApp.repo.fingerprint);
                String type;
                if (Preferences.get().isLocalRepoHttpsEnabled()) {
                    values.put("type", "fdroidrepos");
                    type = "_https._tcp.local.";
                } else {
                    values.put("type", "fdroidrepo");
                    type = "_http._tcp.local.";
                }
                try {
                    pairService = ServiceInfo.create(type, repoName, FDroidApp.port, 0, 0, values);
                    jmdns = JmDNS.create();
                    jmdns.registerService(pairService);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void unregisterMDNSService() {
        if (localRepoBonjourChangeListener != null) {
            Preferences.get().unregisterLocalRepoBonjourListeners(localRepoBonjourChangeListener);
            localRepoBonjourChangeListener = null;
        }
        clearCurrentMDNSService();
    }

    private void clearCurrentMDNSService() {
        if (jmdns != null) {
            if (pairService != null) {
                jmdns.unregisterService(pairService);
                pairService = null;
            }
            jmdns.unregisterAllServices();
            try {
                jmdns.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            jmdns = null;
        }
    }
}
