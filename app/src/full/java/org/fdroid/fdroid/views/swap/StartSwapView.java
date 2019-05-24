package org.fdroid.fdroid.views.swap;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiConfiguration;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.widget.SwitchCompat;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import cc.mvdan.accesspoint.WifiApControl;
import org.fdroid.fdroid.FDroidApp;
import org.fdroid.fdroid.R;
import org.fdroid.fdroid.Utils;
import org.fdroid.fdroid.localrepo.SwapService;
import org.fdroid.fdroid.localrepo.SwapView;
import org.fdroid.fdroid.localrepo.peers.Peer;
import org.fdroid.fdroid.net.WifiStateChangeService;
import rx.Subscriber;
import rx.Subscription;

import java.util.ArrayList;

@SuppressWarnings("LineLength")
public class StartSwapView extends SwapView {
    private static final String TAG = "StartSwapView";

    public StartSwapView(Context context) {
        super(context);
    }

    public StartSwapView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public StartSwapView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @TargetApi(21)
    public StartSwapView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    private class PeopleNearbyAdapter extends ArrayAdapter<Peer> {

        PeopleNearbyAdapter(Context context) {
            super(context, 0, new ArrayList<Peer>());
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext())
                        .inflate(R.layout.swap_peer_list_item, parent, false);
            }

            Peer peer = getItem(position);
            ((TextView) convertView.findViewById(R.id.peer_name)).setText(peer.getName());
            ((ImageView) convertView.findViewById(R.id.icon))
                    .setImageDrawable(getResources().getDrawable(peer.getIcon()));

            return convertView;
        }
    }

    @Nullable /* Emulators typically don't have bluetooth adapters */
    private final BluetoothAdapter bluetooth = BluetoothAdapter.getDefaultAdapter();

    private SwitchCompat bluetoothSwitch;
    private TextView viewBluetoothId;
    private TextView textBluetoothVisible;
    private TextView viewWifiId;
    private TextView viewWifiNetwork;
    private TextView peopleNearbyText;
    private ListView peopleNearbyList;
    private ProgressBar peopleNearbyProgress;

    private PeopleNearbyAdapter peopleNearbyAdapter;

    /**
     * When peers are emitted by the peer finder, add them to the adapter
     * so that they will show up in the list of peers.
     */
    private final Subscriber<Peer> onPeerFound = new Subscriber<Peer>() {

        @Override
        public void onCompleted() {
            uiShowNotSearchingForPeers();
        }

        @Override
        public void onError(Throwable e) {
            uiShowNotSearchingForPeers();
        }

        @Override
        public void onNext(Peer peer) {
            Utils.debugLog(TAG, "Found peer: " + peer + ", adding to list of peers in UI.");
            peopleNearbyAdapter.add(peer);
        }
    };

    private Subscription peerFinderSubscription;

    /**
     * Remove relevant listeners/subscriptions/etc so that they do not receive and process events
     * when this view is not in use.
     * <p>
     * TODO: Not sure if this is the best place to handle being removed from the view.
     */
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (peerFinderSubscription != null) {
            peerFinderSubscription.unsubscribe();
            peerFinderSubscription = null;
        }

        if (bluetoothSwitch != null) {
            bluetoothSwitch.setOnCheckedChangeListener(null);
        }

        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(onBluetoothSwapStateChanged);
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(onWifiNetworkChanged);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        if (peerFinderSubscription == null) {
            peerFinderSubscription = getActivity().getSwapService().scanForPeers().subscribe(onPeerFound);
        }

        uiInitPeers();
        uiInitBluetooth();
        uiInitWifi();
        uiInitButtons();

        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(
                onWifiNetworkChanged, new IntentFilter(WifiStateChangeService.BROADCAST));
    }

    private final BroadcastReceiver onWifiNetworkChanged = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            uiUpdateWifiNetwork();
        }
    };

    private void uiInitButtons() {
        findViewById(R.id.btn_send_fdroid).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                getActivity().sendFDroid();
            }
        });

        findViewById(R.id.btn_qr_scanner).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                getActivity().startQrWorkflow();
            }
        });
    }

    /**
     * Setup the list of nearby peers with an adapter, and hide or show it and the associated
     * message for when no peers are nearby depending on what is happening.
     */
    private void uiInitPeers() {

        peopleNearbyText = (TextView) findViewById(R.id.text_people_nearby);
        peopleNearbyList = (ListView) findViewById(R.id.list_people_nearby);
        peopleNearbyProgress = (ProgressBar) findViewById(R.id.searching_people_nearby);

        peopleNearbyAdapter = new PeopleNearbyAdapter(getContext());
        peopleNearbyList.setAdapter(peopleNearbyAdapter);

        peopleNearbyList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Peer peer = peopleNearbyAdapter.getItem(position);
                onPeerSelected(peer);
            }
        });
    }

    private void uiShowNotSearchingForPeers() {
        peopleNearbyProgress.setVisibility(View.GONE);
        if (peopleNearbyList.getAdapter().getCount() > 0) {
            peopleNearbyText.setText(getContext().getString(R.string.swap_people_nearby));
        } else {
            peopleNearbyText.setText(getContext().getString(R.string.swap_no_peers_nearby));
        }
    }

    private void uiInitBluetooth() {
        if (bluetooth != null) {

            viewBluetoothId = (TextView) findViewById(R.id.device_id_bluetooth);
            viewBluetoothId.setText(bluetooth.getName());
            viewBluetoothId.setVisibility(bluetooth.isEnabled() ? View.VISIBLE : View.GONE);

            textBluetoothVisible = findViewById(R.id.bluetooth_visible);
            if (getActivity().getSwapService().isBluetoothDiscoverable()) {
                textBluetoothVisible.setText(R.string.swap_visible_bluetooth);
            } else {
                textBluetoothVisible.setText(R.string.swap_not_visible_bluetooth);
            }

            bluetoothSwitch = (SwitchCompat) findViewById(R.id.switch_bluetooth);
            Utils.debugLog(TAG, getActivity().getSwapService().isBluetoothDiscoverable()
                    ? "Initially marking switch as checked, because Bluetooth is discoverable."
                    : "Initially marking switch as not-checked, because Bluetooth is not discoverable.");
            bluetoothSwitch.setOnCheckedChangeListener(onBluetoothSwitchToggled);
            setBluetoothSwitchState(getActivity().getSwapService().isBluetoothDiscoverable(), true);

            LocalBroadcastManager.getInstance(getContext()).registerReceiver(onBluetoothSwapStateChanged,
                    new IntentFilter(SwapService.BLUETOOTH_STATE_CHANGE));

        } else {
            findViewById(R.id.bluetooth_info).setVisibility(View.GONE);
        }
    }

    private final BroadcastReceiver onBluetoothSwapStateChanged = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.hasExtra(SwapService.EXTRA_STARTING)) {
                Utils.debugLog(TAG, "Bluetooth service is starting (setting toggle to disabled, not checking because we will wait for an intent that bluetooth is actually enabled)");
                bluetoothSwitch.setEnabled(false);
                textBluetoothVisible.setText(R.string.swap_setting_up_bluetooth);
                // bluetoothSwitch.setChecked(true);
            } else {
                if (intent.hasExtra(SwapService.EXTRA_STARTED)) {
                    Utils.debugLog(TAG, "Bluetooth service has started (updating text to visible, but not marking as checked).");
                    textBluetoothVisible.setText(R.string.swap_visible_bluetooth);
                    bluetoothSwitch.setEnabled(true);
                    // bluetoothSwitch.setChecked(true);
                } else {
                    Utils.debugLog(TAG, "Bluetooth service has stopped (setting switch to not-visible).");
                    textBluetoothVisible.setText(R.string.swap_not_visible_bluetooth);
                    setBluetoothSwitchState(false, true);
                }
            }
        }
    };

    private void setBluetoothSwitchState(boolean isChecked, boolean isEnabled) {
        bluetoothSwitch.setOnCheckedChangeListener(null);
        bluetoothSwitch.setChecked(isChecked);
        bluetoothSwitch.setEnabled(isEnabled);
        bluetoothSwitch.setOnCheckedChangeListener(onBluetoothSwitchToggled);
    }

    private final CompoundButton.OnCheckedChangeListener onBluetoothSwitchToggled = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (isChecked) {
                Utils.debugLog(TAG, "Received onCheckChanged(true) for Bluetooth swap, prompting user as to whether they want to enable Bluetooth.");
                getActivity().startBluetoothSwap();
                textBluetoothVisible.setText(R.string.swap_visible_bluetooth);
                viewBluetoothId.setVisibility(View.VISIBLE);
                Utils.debugLog(TAG, "Received onCheckChanged(true) for Bluetooth swap (prompting user or setup Bluetooth complete)");
                // TODO: When they deny the request for enabling bluetooth, we need to disable this switch...
            } else {
                Utils.debugLog(TAG, "Received onCheckChanged(false) for Bluetooth swap, disabling Bluetooth swap.");
                getActivity().getSwapService().getBluetoothSwap().stop();
                textBluetoothVisible.setText(R.string.swap_not_visible_bluetooth);
                viewBluetoothId.setVisibility(View.GONE);
                Utils.debugLog(TAG, "Received onCheckChanged(false) for Bluetooth swap, Bluetooth swap disabled successfully.");
            }
            SwapService.putBluetoothVisibleUserPreference(isChecked);
        }
    };

    private void uiInitWifi() {

        viewWifiId = (TextView) findViewById(R.id.device_id_wifi);
        viewWifiNetwork = (TextView) findViewById(R.id.wifi_network);

        uiUpdateWifiNetwork();
    }

    private void uiUpdateWifiNetwork() {

        viewWifiId.setText(FDroidApp.ipAddressString);
        viewWifiId.setVisibility(TextUtils.isEmpty(FDroidApp.ipAddressString) ? View.GONE : View.VISIBLE);

        WifiApControl wifiAp = WifiApControl.getInstance(getActivity());
        if (wifiAp != null && wifiAp.isWifiApEnabled()) {
            WifiConfiguration config = wifiAp.getConfiguration();
            viewWifiNetwork.setText(getContext().getString(R.string.swap_active_hotspot, config.SSID));
        } else if (TextUtils.isEmpty(FDroidApp.ssid)) {
            // not connected to or setup with any wifi network
            viewWifiNetwork.setText(R.string.swap_no_wifi_network);
        } else {
            // connected to a regular wifi network
            viewWifiNetwork.setText(FDroidApp.ssid);
        }
    }

    private void onPeerSelected(Peer peer) {
        getActivity().swapWith(peer);
    }
}
