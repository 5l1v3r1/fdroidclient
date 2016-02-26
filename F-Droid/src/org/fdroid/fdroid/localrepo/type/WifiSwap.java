package org.fdroid.fdroid.localrepo.type;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import org.fdroid.fdroid.FDroidApp;
import org.fdroid.fdroid.Preferences;
import org.fdroid.fdroid.Utils;
import org.fdroid.fdroid.localrepo.SwapService;
import org.fdroid.fdroid.net.LocalHTTPD;
import org.fdroid.fdroid.net.WifiStateChangeService;

import java.io.IOException;
import java.net.BindException;
import java.util.Random;

public class WifiSwap extends SwapType {

    private static final String TAG = "WifiSwap";

    private Handler webServerThreadHandler;
    private LocalHTTPD localHttpd;
    private final BonjourBroadcast bonjourBroadcast;

    public WifiSwap(Context context) {
        super(context);
        bonjourBroadcast = new BonjourBroadcast(context);
    }

    protected String getBroadcastAction() {
        return SwapService.WIFI_STATE_CHANGE;
    }

    public BonjourBroadcast getBonjour() {
        return bonjourBroadcast;
    }

    @Override
    public void start() {
        Utils.debugLog(TAG, "Preparing swap webserver.");
        sendBroadcast(SwapService.EXTRA_STARTING);

        if (FDroidApp.ipAddressString == null) {
            Log.e(TAG, "Not starting swap webserver, because we don't seem to be connected to a network.");
            setConnected(false);
        }
        
        Runnable webServer = new Runnable() {
            // Tell Eclipse this is not a leak because of Looper use.
            @SuppressLint("HandlerLeak")
            @Override
            public void run() {
                localHttpd = new LocalHTTPD(
                        context,
                        FDroidApp.ipAddressString,
                        FDroidApp.port,
                        context.getFilesDir(),
                        Preferences.get().isLocalRepoHttpsEnabled());

                Looper.prepare(); // must be run before creating a Handler
                webServerThreadHandler = new Handler() {
                    @Override
                    public void handleMessage(Message msg) {
                        Log.i(TAG, "we've been asked to stop the webserver: " + msg.obj);
                        setConnected(false);
                        localHttpd.stop();
                        Looper looper = Looper.myLooper();
                        if (looper == null) {
                            Log.e(TAG, "Looper.myLooper() was null for sum reason while shutting down the swap webserver.");
                        } else {
                            looper.quit();
                        }
                    }
                };
                try {
                    Utils.debugLog(TAG, "Starting swap webserver...");
                    localHttpd.start();
                    setConnected(true);
                    Utils.debugLog(TAG, "Swap webserver started.");
                } catch (BindException e) {
                    int prev = FDroidApp.port;
                    FDroidApp.port = FDroidApp.port + new Random().nextInt(1111);
                    setConnected(false);
                    Log.w(TAG, "port " + prev + " occupied, trying on " + FDroidApp.port + "!");
                    context.startService(new Intent(context, WifiStateChangeService.class));
                } catch (IOException e) {
                    setConnected(false);
                    Log.e(TAG, "Could not start local repo HTTP server", e);
                }
                Looper.loop(); // start the message receiving loop
            }
        };
        new Thread(webServer).start();
        bonjourBroadcast.start();
    }

    @Override
    public void stop() {
        sendBroadcast(SwapService.EXTRA_STOPPING);
        if (webServerThreadHandler == null) {
            Log.i(TAG, "null handler in stopWebServer");
        } else {
            Utils.debugLog(TAG, "Sending message to swap webserver to stop it.");
            Message msg = webServerThreadHandler.obtainMessage();
            msg.obj = webServerThreadHandler.getLooper().getThread().getName() + " says stop";
            webServerThreadHandler.sendMessage(msg);
        }

        // Stop the Bonjour stuff after asking the webserver to stop. This is not required in this
        // order, but it helps. In practice, the Bonjour stuff takes a second or two to stop. This
        // should give enough time for the message we posted above to reach the web server thread
        // and for the webserver to thus be stopped.
        bonjourBroadcast.stop();
        setConnected(false);
    }

}
