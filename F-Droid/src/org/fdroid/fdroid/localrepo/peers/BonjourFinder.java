package org.fdroid.fdroid.localrepo.peers;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;

public class BonjourFinder extends PeerFinder<BonjourPeer> implements ServiceListener {

    private static final String TAG = "BonjourFinder";

    public static final String HTTP_SERVICE_TYPE = "_http._tcp.local.";
    public static final String HTTPS_SERVICE_TYPE = "_https._tcp.local.";

    private JmDNS mJmdns;
    private WifiManager wifiManager;
    private WifiManager.MulticastLock mMulticastLock;

    public BonjourFinder(Context context) {
        super(context);
    }

    @Override
    public void scan() {

        if (wifiManager == null) {
            wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            mMulticastLock = wifiManager.createMulticastLock(context.getPackageName());
            mMulticastLock.setReferenceCounted(false);
        }

        if (isScanning) {
            Log.d(TAG, "Requested Bonjour scan, but already scanning, so will ignore request.");
            return;
        }

        isScanning = true;
        mMulticastLock.acquire();
        new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground(Void... params) {
                try {
                    int ip = wifiManager.getConnectionInfo().getIpAddress();
                    byte[] byteIp = {
                            (byte) (ip & 0xff),
                            (byte) (ip >> 8 & 0xff),
                            (byte) (ip >> 16 & 0xff),
                            (byte) (ip >> 24 & 0xff)
                    };
                    Log.d(TAG, "Searching for mDNS clients...");
                    mJmdns = JmDNS.create(InetAddress.getByAddress(byteIp));
                    Log.d(TAG, "Finished searching for mDNS clients.");
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void result) {
                Log.d(TAG, "Adding mDNS service listeners.");
                if (mJmdns != null) {
                    mJmdns.addServiceListener(HTTP_SERVICE_TYPE, BonjourFinder.this);
                    mJmdns.addServiceListener(HTTPS_SERVICE_TYPE, BonjourFinder.this);
                }
            }
        }.execute();

    }

    @Override
    public void serviceRemoved(ServiceEvent event) {
    }

    @Override
    public void serviceAdded(final ServiceEvent event) {
        // TODO: Get clarification, but it looks like this is:
        //   1) Identifying that there is _a_ bonjour service available
        //   2) Adding it to the list to give some sort of feedback to the user
        //   3) Requesting more detailed info in an async manner
        //   4) If that is in fact an fdroid repo (after requesting info), then add it again
        //      so that more detailed info can be shown to the user.
        //
        //    If so, when is the old one removed?
        addFDroidService(event);
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                mJmdns.requestServiceInfo(event.getType(), event.getName(), true);
                return null;
            }
        }.execute();
    }

    @Override
    public void serviceResolved(ServiceEvent event) {
        addFDroidService(event);
    }

    private void addFDroidService(ServiceEvent event) {
        final ServiceInfo serviceInfo = event.getInfo();
        if (serviceInfo.getPropertyString("type").startsWith("fdroidrepo")) {
            foundPeer(new BonjourPeer(serviceInfo));
        }
    }

    @Override
    public void cancel() {
        mMulticastLock.release();
        if (mJmdns == null)
            return;
        mJmdns.removeServiceListener(HTTP_SERVICE_TYPE, this);
        mJmdns.removeServiceListener(HTTPS_SERVICE_TYPE, this);
        mJmdns = null;
        isScanning = false;

    }

}
