package org.fdroid.fdroid.views.main;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import org.fdroid.fdroid.R;
import org.fdroid.fdroid.localrepo.TreeUriScannerIntentService;
import org.fdroid.fdroid.views.swap.SwapWorkflowActivity;

import java.io.File;

/**
 * A splash screen encouraging people to start the swap process. The swap
 * process is quite heavy duty in that it fires up Bluetooth and/or WiFi
 * in  order to scan for peers. As such, it is quite convenient to have a
 * more lightweight view to show in the main navigation that doesn't
 * automatically start doing things when the user touches the navigation
 * menu in the bottom navigation.
 * <p>
 * Lots of pieces of the nearby/swap functionality require that the user grant
 * F-Droid permissions at runtime on {@code android-23} and higher. On devices
 * that have a removable SD Card that is currently mounted, this will request
 * permission to read it, so that F-Droid can look for repos on the SD Card.
 * <p>
 * Once {@link Manifest.permission#READ_EXTERNAL_STORAGE} or
 * {@link Manifest.permission#WRITE_EXTERNAL_STORAGE} is granted for F-Droid,
 * then it can read any file on an SD Card and no more prompts are needed. For
 * USB OTG drives, the only way to get read permissions is to prompt the user
 * via {@link Intent#ACTION_OPEN_DOCUMENT_TREE}.
 * <p>
 * For write permissions, {@code android-19} and {@code android-20} devices are
 * basically screwed here.  {@link Intent#ACTION_OPEN_DOCUMENT_TREE} was added
 * in {@code android-21}, and there does not seem to be any other way to get
 * write access to the the removable storage.
 *
 * @see TreeUriScannerIntentService
 */
class NearbyViewBinder {
    public static final String TAG = "NearbyViewBinder";

    static File externalStorage = null;

    NearbyViewBinder(final Activity activity, FrameLayout parent) {
        View swapView = activity.getLayoutInflater().inflate(R.layout.main_tab_swap, parent, true);

        TextView subtext = swapView.findViewById(R.id.text2);
        subtext.setText(activity.getString(R.string.nearby_splash__both_parties_need_fdroid,
                activity.getString(R.string.app_name)));

        ImageView nearbySplash = swapView.findViewById(R.id.image);

        Button startButton = swapView.findViewById(R.id.button);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                activity.startActivity(new Intent(activity, SwapWorkflowActivity.class));
            }
        });

        if (Build.VERSION.SDK_INT >= 21) {
            Log.i(TAG, "Environment.isExternalStorageRemovable(activity.getExternalFilesDir(\"\")) " +
                    Environment.isExternalStorageRemovable(activity.getExternalFilesDir("")));
            File[] dirs = activity.getExternalFilesDirs("");
            if (dirs != null) {
                for (File f : dirs) {
                    if (f != null && Environment.isExternalStorageRemovable(f)) {
                        // remove Android/data/org.fdroid.fdroid/files to get root
                        externalStorage = f.getParentFile().getParentFile().getParentFile().getParentFile();
                        break;
                    }
                }
            }
        } else if (Environment.isExternalStorageRemovable() &&
                (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED_READ_ONLY)
                        || Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED))) {
            Log.i(TAG, "<21 isExternalStorageRemovable MEDIA_MOUNTED");
            externalStorage = Environment.getExternalStorageDirectory();
        }

        if (externalStorage != null) {
            nearbySplash.setVisibility(View.GONE);
            View readExternalStorage = swapView.findViewById(R.id.readExternalStorage);
            readExternalStorage.setVisibility(View.VISIBLE);
            Button requestReadExternalStorage = swapView.findViewById(R.id.requestReadExternalStorage);
            requestReadExternalStorage.setOnClickListener(new View.OnClickListener() {
                @RequiresApi(api = 21)
                @Override
                public void onClick(View v) {
                    File storage = externalStorage.getParentFile();
                    File[] files = storage.listFiles();
                    String msg = "";
                    if (files != null) for (File f : files) {
                        msg += "|" + f.getName();
                    }
                    Toast.makeText(activity, msg, Toast.LENGTH_LONG).show();
                    final String writeExternalStorage = Manifest.permission.WRITE_EXTERNAL_STORAGE;
                    if (Build.VERSION.SDK_INT >= 23
                            && !externalStorage.canRead()
                            && PackageManager.PERMISSION_GRANTED
                            != ContextCompat.checkSelfPermission(activity, writeExternalStorage)) {
                        ActivityCompat.requestPermissions(activity, new String[]{writeExternalStorage},
                                MainActivity.REQUEST_STORAGE_PERMISSIONS);
                    } else {
                        // TODO do something
                    }
                }
            });

        }
    }
}
