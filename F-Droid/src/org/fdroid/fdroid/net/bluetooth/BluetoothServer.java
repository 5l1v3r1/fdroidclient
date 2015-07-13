package org.fdroid.fdroid.net.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Build;
import android.util.Log;
import org.fdroid.fdroid.FDroidApp;
import org.fdroid.fdroid.Utils;
import org.fdroid.fdroid.net.HttpDownloader;
import org.fdroid.fdroid.net.bluetooth.httpish.Request;
import org.fdroid.fdroid.net.bluetooth.httpish.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Act as a layer on top of LocalHTTPD server, by forwarding requests served
 * over bluetooth to that server.
 */
public class BluetoothServer extends Thread {

    private static final String TAG = "BluetoothServer";

    private BluetoothServerSocket serverSocket;
    private List<Connection> clients = new ArrayList<>();

    private final Context context;

    private String deviceBluetoothName = null;
    public final static String BLUETOOTH_NAME_TAG = "FDroid:";

    public BluetoothServer(Context context) {
        this.context = context.getApplicationContext();
    }

    public void close() {

        for (Connection connection : clients) {
            connection.interrupt();
        }

        if (serverSocket != null) {
            Utils.closeQuietly(serverSocket);
        }

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        adapter.setName(deviceBluetoothName.replace(BLUETOOTH_NAME_TAG,""));

    }

    @Override
    public void run() {

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();


        //store the original bluetoothname, and update this one to be unique
        deviceBluetoothName = adapter.getName();

        if (!deviceBluetoothName.contains(BLUETOOTH_NAME_TAG))
            adapter.setName(BLUETOOTH_NAME_TAG + deviceBluetoothName);


        try {
            serverSocket = adapter.listenUsingInsecureRfcommWithServiceRecord("FDroid App Swap", BluetoothConstants.fdroidUuid());
        } catch (IOException e) {
            Log.e(TAG, "Error starting Bluetooth server socket, will stop the server now - " + e.getMessage());
            return;
        }

        while (true) {
            try {
                BluetoothSocket clientSocket = serverSocket.accept();
                if (clientSocket != null && !isInterrupted()) {
                    Connection client = new Connection(context, clientSocket);
                    client.start();
                    clients.add(client);
                } else {
                    break;
                }
            } catch (IOException e) {
                Log.e(TAG, "Error receiving client connection over Bluetooth server socket, will continue listening for other clients - " + e.getMessage());
            }
        }

    }

    private static class Connection extends Thread
    {

        private final Context context;
        private final BluetoothSocket socket;

        public Connection(Context context, BluetoothSocket socket) {
            this.context = context.getApplicationContext();
            this.socket = socket;
        }

        @Override
        public void run() {

            Log.d(TAG, "Listening for incoming Bluetooth requests from client");

            BluetoothConnection connection;
            try {
                connection = new BluetoothConnection(socket);
                connection.open();
            } catch (IOException e) {
                Log.e(TAG, "Error listening for incoming connections over bluetooth - " + e.getMessage());
                return;
            }

            while (true) {

                try {
                    Log.d(TAG, "Listening for new Bluetooth request from client.");
                    Request incomingRequest = Request.listenForRequest(connection);
                    handleRequest(incomingRequest).send(connection);
                } catch (IOException e) {
                    Log.e(TAG, "Error receiving incoming connection over bluetooth - " + e.getMessage());
                }

                if (isInterrupted())
                    break;

            }

        }

        private Response handleRequest(Request request) throws IOException {

            Log.d(TAG, "Received Bluetooth request from client, will process it now.");

            try {
                HttpDownloader downloader = new HttpDownloader("http://127.0.0.1:" + ( FDroidApp.port + 1 ) + "/" + request.getPath(), context);

                Response.Builder builder;

                if (request.getMethod().equals(Request.Methods.HEAD)) {
                    builder = new Response.Builder();
                } else {
                    builder = new Response.Builder(downloader.getInputStream());
                }

                // TODO: At this stage, will need to download the file to get this info.
                // However, should be able to make totalDownloadSize and getCacheTag work without downloading.
                return builder
                        .setStatusCode(downloader.getStatusCode())
                        .setFileSize(downloader.totalDownloadSize())
                        .build();

            } catch (IOException e) {
                if (Build.VERSION.SDK_INT <= 9) {
                    // Would like to use the specific IOException below with a "cause", but it is
                    // only supported on SDK 9, so I guess this is the next most useful thing.
                    throw e;
                } else {
                    throw new IOException("Error getting file " + request.getPath() + " from local repo proxy - " + e.getMessage(), e);
                }
            }

        }
    }
}
