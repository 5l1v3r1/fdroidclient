/*
 * Copyright (C) 2014-2016 Dominik Schürmann <dominik@dominikschuermann.de>
 * Copyright (C) 2015 Daniel Martí <mvdan@mvdan.cc>
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
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301, USA.
 */

package org.fdroid.fdroid.installer;

import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v7.app.AlertDialog;
import android.util.Log;

import org.fdroid.fdroid.R;
import org.fdroid.fdroid.Utils;
import org.fdroid.fdroid.privileged.IPrivilegedCallback;
import org.fdroid.fdroid.privileged.IPrivilegedService;

import java.util.HashMap;

/**
 * Installer based on using internal hidden APIs of the Android OS, which are
 * protected by the permissions
 * <ul>
 * <li>android.permission.INSTALL_PACKAGES</li>
 * <li>android.permission.DELETE_PACKAGES</li>
 * </ul>
 * <p/>
 * Both permissions are protected by systemOrSignature (in newer versions:
 * system|signature). Thus, this installer works only when the "F-Droid Privileged
 * Extension" is installed into the system.
 * <p/>
 * Sources for Android 4.4 change:
 * https://groups.google.com/forum/#!msg/android-
 * security-discuss/r7uL_OEMU5c/LijNHvxeV80J
 * https://android.googlesource.com/platform
 * /frameworks/base/+/ccbf84f44c9e6a5ed3c08673614826bb237afc54
 */
public class PrivilegedInstaller extends Installer {

    private static final String TAG = "PrivilegedInstaller";

    private static final String PRIVILEGED_EXTENSION_SERVICE_INTENT = "org.fdroid.fdroid.privileged.IPrivilegedService";
    public static final String PRIVILEGED_EXTENSION_PACKAGE_NAME = "org.fdroid.fdroid.privileged";

    public static final int IS_EXTENSION_INSTALLED_NO = 0;
    public static final int IS_EXTENSION_INSTALLED_YES = 1;
    public static final int IS_EXTENSION_INSTALLED_SIGNATURE_PROBLEM = 2;
    public static final int IS_EXTENSION_INSTALLED_PERMISSIONS_PROBLEM = 3;

    // From AOSP source code
    public static final int ACTION_INSTALL_REPLACE_EXISTING = 2;

    /**
     * Following return codes are copied from AOSP 5.1 source code
     */
    public static final int INSTALL_SUCCEEDED = 1;
    public static final int INSTALL_FAILED_ALREADY_EXISTS = -1;
    public static final int INSTALL_FAILED_INVALID_APK = -2;
    public static final int INSTALL_FAILED_INVALID_URI = -3;
    public static final int INSTALL_FAILED_INSUFFICIENT_STORAGE = -4;
    public static final int INSTALL_FAILED_DUPLICATE_PACKAGE = -5;
    public static final int INSTALL_FAILED_NO_SHARED_USER = -6;
    public static final int INSTALL_FAILED_UPDATE_INCOMPATIBLE = -7;
    public static final int INSTALL_FAILED_SHARED_USER_INCOMPATIBLE = -8;
    public static final int INSTALL_FAILED_MISSING_SHARED_LIBRARY = -9;
    public static final int INSTALL_FAILED_REPLACE_COULDNT_DELETE = -10;
    public static final int INSTALL_FAILED_DEXOPT = -11;
    public static final int INSTALL_FAILED_OLDER_SDK = -12;
    public static final int INSTALL_FAILED_CONFLICTING_PROVIDER = -13;
    public static final int INSTALL_FAILED_NEWER_SDK = -14;
    public static final int INSTALL_FAILED_TEST_ONLY = -15;
    public static final int INSTALL_FAILED_CPU_ABI_INCOMPATIBLE = -16;
    public static final int INSTALL_FAILED_MISSING_FEATURE = -17;
    public static final int INSTALL_FAILED_CONTAINER_ERROR = -18;
    public static final int INSTALL_FAILED_INVALID_INSTALL_LOCATION = -19;
    public static final int INSTALL_FAILED_MEDIA_UNAVAILABLE = -20;
    public static final int INSTALL_FAILED_VERIFICATION_TIMEOUT = -21;
    public static final int INSTALL_FAILED_VERIFICATION_FAILURE = -22;
    public static final int INSTALL_FAILED_PACKAGE_CHANGED = -23;
    public static final int INSTALL_FAILED_UID_CHANGED = -24;
    public static final int INSTALL_FAILED_VERSION_DOWNGRADE = -25;
    public static final int INSTALL_PARSE_FAILED_NOT_APK = -100;
    public static final int INSTALL_PARSE_FAILED_BAD_MANIFEST = -101;
    public static final int INSTALL_PARSE_FAILED_UNEXPECTED_EXCEPTION = -102;
    public static final int INSTALL_PARSE_FAILED_NO_CERTIFICATES = -103;
    public static final int INSTALL_PARSE_FAILED_INCONSISTENT_CERTIFICATES = -104;
    public static final int INSTALL_PARSE_FAILED_CERTIFICATE_ENCODING = -105;
    public static final int INSTALL_PARSE_FAILED_BAD_PACKAGE_NAME = -106;
    public static final int INSTALL_PARSE_FAILED_BAD_SHARED_USER_ID = -107;
    public static final int INSTALL_PARSE_FAILED_MANIFEST_MALFORMED = -108;
    public static final int INSTALL_PARSE_FAILED_MANIFEST_EMPTY = -109;
    public static final int INSTALL_FAILED_INTERNAL_ERROR = -110;
    public static final int INSTALL_FAILED_USER_RESTRICTED = -111;
    public static final int INSTALL_FAILED_DUPLICATE_PERMISSION = -112;
    public static final int INSTALL_FAILED_NO_MATCHING_ABIS = -113;
    /**
     * Internal return code for NativeLibraryHelper methods to indicate that the package
     * being processed did not contain any native code. This is placed here only so that
     * it can belong to the same value space as the other install failure codes.
     */
    public static final int NO_NATIVE_LIBRARIES = -114;
    public static final int INSTALL_FAILED_ABORTED = -115;

    private static final HashMap<Integer, String> sInstallReturnCodes;

    static {
        // Descriptions extracted from the source code comments in AOSP
        sInstallReturnCodes = new HashMap<>();
        sInstallReturnCodes.put(INSTALL_SUCCEEDED,
                "Success");
        sInstallReturnCodes.put(INSTALL_FAILED_ALREADY_EXISTS,
                "Package is already installed.");
        sInstallReturnCodes.put(INSTALL_FAILED_INVALID_APK,
                "The package archive file is invalid.");
        sInstallReturnCodes.put(INSTALL_FAILED_INVALID_URI,
                "The URI passed in is invalid.");
        sInstallReturnCodes.put(INSTALL_FAILED_INSUFFICIENT_STORAGE,
                "The package manager service found that the device didn't have enough " +
                        "storage space to install the app.");
        sInstallReturnCodes.put(INSTALL_FAILED_DUPLICATE_PACKAGE,
                "A package is already installed with the same name.");
        sInstallReturnCodes.put(INSTALL_FAILED_NO_SHARED_USER,
                "The requested shared user does not exist.");
        sInstallReturnCodes.put(INSTALL_FAILED_UPDATE_INCOMPATIBLE,
                "A previously installed package of the same name has a different signature than " +
                        "the new package (and the old package's data was not removed).");
        sInstallReturnCodes.put(INSTALL_FAILED_SHARED_USER_INCOMPATIBLE,
                "The new package is requested a shared user which is already installed on " +
                        "the device and does not have matching signature.");
        sInstallReturnCodes.put(INSTALL_FAILED_MISSING_SHARED_LIBRARY,
                "The new package uses a shared library that is not available.");
        sInstallReturnCodes.put(INSTALL_FAILED_REPLACE_COULDNT_DELETE,
                "Unknown"); // wrong comment in source
        sInstallReturnCodes.put(INSTALL_FAILED_DEXOPT,
                "The package failed while optimizing and validating its dex files, either " +
                        "because there was not enough storage or the validation failed.");
        sInstallReturnCodes.put(INSTALL_FAILED_OLDER_SDK,
                "The new package failed because the current SDK version is older than that " +
                        "required by the package.");
        sInstallReturnCodes.put(INSTALL_FAILED_CONFLICTING_PROVIDER,
                "The new package failed because it contains a content provider with the same " +
                        "authority as a provider already installed in the system.");
        sInstallReturnCodes.put(INSTALL_FAILED_NEWER_SDK,
                "The new package failed because the current SDK version is newer than that " +
                        "required by the package.");
        sInstallReturnCodes.put(INSTALL_FAILED_TEST_ONLY,
                "The new package failed because it has specified that it is a test-only package " +
                        "and the caller has not supplied the {@link #INSTALL_ALLOW_TEST} flag.");
        sInstallReturnCodes.put(INSTALL_FAILED_CPU_ABI_INCOMPATIBLE,
                "The package being installed contains native code, but none that is compatible " +
                        "with the device's CPU_ABI.");
        sInstallReturnCodes.put(INSTALL_FAILED_MISSING_FEATURE,
                "The new package uses a feature that is not available.");
        sInstallReturnCodes.put(INSTALL_FAILED_CONTAINER_ERROR,
                "A secure container mount point couldn't be accessed on external media.");
        sInstallReturnCodes.put(INSTALL_FAILED_INVALID_INSTALL_LOCATION,
                "The new package couldn't be installed in the specified install location.");
        sInstallReturnCodes.put(INSTALL_FAILED_MEDIA_UNAVAILABLE,
                "The new package couldn't be installed in the specified install location " +
                        "because the media is not available.");
        sInstallReturnCodes.put(INSTALL_FAILED_VERIFICATION_TIMEOUT,
                "The new package couldn't be installed because the verification timed out.");
        sInstallReturnCodes.put(INSTALL_FAILED_VERIFICATION_FAILURE,
                "The new package couldn't be installed because the verification did not succeed.");
        sInstallReturnCodes.put(INSTALL_FAILED_PACKAGE_CHANGED,
                "The package changed from what the calling program expected.");
        sInstallReturnCodes.put(INSTALL_FAILED_UID_CHANGED,
                "The new package is assigned a different UID than it previously held.");
        sInstallReturnCodes.put(INSTALL_FAILED_VERSION_DOWNGRADE,
                "The new package has an older version code than the currently installed package.");
        sInstallReturnCodes.put(INSTALL_PARSE_FAILED_NOT_APK,
                "The parser was given a path that is not a file, or does not end with the " +
                        "expected '.apk' extension.");
        sInstallReturnCodes.put(INSTALL_PARSE_FAILED_BAD_MANIFEST,
                "the parser was unable to retrieve the AndroidManifest.xml file.");
        sInstallReturnCodes.put(INSTALL_PARSE_FAILED_UNEXPECTED_EXCEPTION,
                "The parser encountered an unexpected exception.");
        sInstallReturnCodes.put(INSTALL_PARSE_FAILED_NO_CERTIFICATES,
                "The parser did not find any certificates in the .apk.");
        sInstallReturnCodes.put(INSTALL_PARSE_FAILED_INCONSISTENT_CERTIFICATES,
                "The parser found inconsistent certificates on the files in the .apk.");
        sInstallReturnCodes.put(INSTALL_PARSE_FAILED_CERTIFICATE_ENCODING,
                "The parser encountered a CertificateEncodingException in one of the files in " +
                        "the .apk.");
        sInstallReturnCodes.put(INSTALL_PARSE_FAILED_BAD_PACKAGE_NAME,
                "The parser encountered a bad or missing package name in the manifest.");
        sInstallReturnCodes.put(INSTALL_PARSE_FAILED_BAD_SHARED_USER_ID,
                "The parser encountered a bad shared user id name in the manifest.");
        sInstallReturnCodes.put(INSTALL_PARSE_FAILED_MANIFEST_MALFORMED,
                "The parser encountered some structural problem in the manifest.");
        sInstallReturnCodes.put(INSTALL_PARSE_FAILED_MANIFEST_EMPTY,
                "The parser did not find any actionable tags (instrumentation or application) " +
                        "in the manifest.");
        sInstallReturnCodes.put(INSTALL_FAILED_INTERNAL_ERROR,
                "The system failed to install the package because of system issues.");
        sInstallReturnCodes.put(INSTALL_FAILED_USER_RESTRICTED,
                "The system failed to install the package because the user is restricted from " +
                        "installing apps.");
        sInstallReturnCodes.put(INSTALL_FAILED_DUPLICATE_PERMISSION,
                "The system failed to install the package because it is attempting to define a " +
                        "permission that is already defined by some existing package.");
        sInstallReturnCodes.put(INSTALL_FAILED_NO_MATCHING_ABIS,
                "The system failed to install the package because its packaged native code did " +
                        "not match any of the ABIs supported by the system.");
    }

    public static final int DELETE_SUCCEEDED = 1;
    public static final int DELETE_FAILED_INTERNAL_ERROR = -1;
    public static final int DELETE_FAILED_DEVICE_POLICY_MANAGER = -2;
    public static final int DELETE_FAILED_USER_RESTRICTED = -3;
    public static final int DELETE_FAILED_OWNER_BLOCKED = -4;
    public static final int DELETE_FAILED_ABORTED = -5;

    private static final HashMap<Integer, String> sUninstallReturnCodes;

    static {
        // Descriptions extracted from the source code comments in AOSP
        sUninstallReturnCodes = new HashMap<>();
        sUninstallReturnCodes.put(DELETE_SUCCEEDED,
                "Success");
        sUninstallReturnCodes.put(DELETE_FAILED_INTERNAL_ERROR,
                " the system failed to delete the package for an unspecified reason.");
        sUninstallReturnCodes.put(DELETE_FAILED_DEVICE_POLICY_MANAGER,
                "the system failed to delete the package because it is the active " +
                        "DevicePolicy manager.");
        sUninstallReturnCodes.put(DELETE_FAILED_USER_RESTRICTED,
                "the system failed to delete the package since the user is restricted.");
        sUninstallReturnCodes.put(DELETE_FAILED_OWNER_BLOCKED,
                "the system failed to delete the package because a profile or " +
                        "device owner has marked the package as uninstallable.");
    }

    public PrivilegedInstaller(Context context) {
        super(context);
    }

    public static boolean isExtensionInstalled(Context context) {
        PackageManager pm = context.getPackageManager();
        try {
            pm.getPackageInfo(PRIVILEGED_EXTENSION_PACKAGE_NAME, PackageManager.GET_ACTIVITIES);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static int isExtensionInstalledCorrectly(Context context) {

        // check if installed
        if (!isExtensionInstalled(context)) {
            return IS_EXTENSION_INSTALLED_NO;
        }

        // check if it has the privileged permissions granted
        final Object mutex = new Object();
        final Bundle returnBundle = new Bundle();
        ServiceConnection mServiceConnection = new ServiceConnection() {
            public void onServiceConnected(ComponentName name, IBinder service) {
                IPrivilegedService privService = IPrivilegedService.Stub.asInterface(service);

                try {
                    boolean hasPermissions = privService.hasPrivilegedPermissions();
                    returnBundle.putBoolean("has_permissions", hasPermissions);
                } catch (RemoteException e) {
                    Log.e(TAG, "RemoteException", e);
                }

                synchronized (mutex) {
                    mutex.notify();
                }
            }

            public void onServiceDisconnected(ComponentName name) {
            }
        };
        Intent serviceIntent = new Intent(PRIVILEGED_EXTENSION_SERVICE_INTENT);
        serviceIntent.setPackage(PRIVILEGED_EXTENSION_PACKAGE_NAME);

        try {
            context.getApplicationContext().bindService(serviceIntent, mServiceConnection,
                    Context.BIND_AUTO_CREATE);
        } catch (SecurityException e) {
            return IS_EXTENSION_INSTALLED_SIGNATURE_PROBLEM;
        }

        synchronized (mutex) {
            try {
                mutex.wait(3000);
            } catch (InterruptedException ignored) {
            }
        }

        boolean hasPermissions = returnBundle.getBoolean("has_permissions", false);
        if (!hasPermissions) {
            return IS_EXTENSION_INSTALLED_PERMISSIONS_PROBLEM;
        }
        return IS_EXTENSION_INSTALLED_YES;
    }


    @Override
    protected void installPackage(final Uri uri, final Uri originatingUri, String packageName) {
        sendBroadcastInstall(uri, originatingUri, Installer.ACTION_INSTALL_STARTED);

        final Uri sanitizedUri;
        try {
            sanitizedUri = Installer.prepareApkFile(mContext, uri, packageName);
        } catch (Installer.InstallFailedException e) {
            Log.e(TAG, "prepareApkFile failed", e);
            sendBroadcastInstall(uri, originatingUri, Installer.ACTION_INSTALL_INTERRUPTED,
                    e.getMessage());
            return;
        }

        ServiceConnection mServiceConnection = new ServiceConnection() {
            public void onServiceConnected(ComponentName name, IBinder service) {
                IPrivilegedService privService = IPrivilegedService.Stub.asInterface(service);

                IPrivilegedCallback callback = new IPrivilegedCallback.Stub() {
                    @Override
                    public void handleResult(String packageName, int returnCode) throws RemoteException {
                        if (returnCode == INSTALL_SUCCEEDED) {
                            sendBroadcastInstall(uri, originatingUri, ACTION_INSTALL_COMPLETE);
                        } else {
                            sendBroadcastInstall(uri, originatingUri, ACTION_INSTALL_INTERRUPTED,
                                    "Error " + returnCode + ": "
                                            + sInstallReturnCodes.get(returnCode));
                        }
                    }
                };

                try {
                    privService.installPackage(sanitizedUri, ACTION_INSTALL_REPLACE_EXISTING,
                            null, callback);
                } catch (RemoteException e) {
                    Log.e(TAG, "RemoteException", e);
                    sendBroadcastInstall(uri, originatingUri, ACTION_INSTALL_INTERRUPTED,
                            "connecting to privileged service failed");
                }
            }

            public void onServiceDisconnected(ComponentName name) {
            }
        };

        Intent serviceIntent = new Intent(PRIVILEGED_EXTENSION_SERVICE_INTENT);
        serviceIntent.setPackage(PRIVILEGED_EXTENSION_PACKAGE_NAME);
        mContext.getApplicationContext().bindService(serviceIntent, mServiceConnection,
                Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void uninstallPackage(final String packageName) {
        sendBroadcastUninstall(packageName, Installer.ACTION_UNINSTALL_STARTED);

        ApplicationInfo appInfo;
        try {
            //noinspection WrongConstant (lint is actually wrong here!)
            appInfo = mPm.getApplicationInfo(packageName, PackageManager.GET_UNINSTALLED_PACKAGES);
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Failed to get ApplicationInfo for uninstalling");
            return;
        }

        final boolean isSystem = (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        final boolean isUpdate = (appInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;

        if (isSystem && !isUpdate) {
            // Cannot remove system apps unless we're uninstalling updates
            sendBroadcastUninstall(packageName, ACTION_UNINSTALL_INTERRUPTED,
                    "Cannot remove system apps unless we're uninstalling updates");
            return;
        }

        int messageId;
        if (isUpdate) {
            messageId = R.string.uninstall_update_confirm;
        } else {
            messageId = R.string.uninstall_confirm;
        }

        // TODO: move this to methods in activity/ Installer with activity context!
        final AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        builder.setTitle(appInfo.loadLabel(mPm));
        builder.setIcon(appInfo.loadIcon(mPm));
        builder.setPositiveButton(android.R.string.ok,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            doDeletePackageInternal(packageName);
                        } catch (InstallFailedException e) {
                            sendBroadcastUninstall(packageName, ACTION_UNINSTALL_INTERRUPTED,
                                    "uninstall failed");
                        }
                    }
                });
        builder.setNegativeButton(android.R.string.cancel,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                        sendBroadcastUninstall(packageName, ACTION_UNINSTALL_INTERRUPTED);
                    }
                });
        builder.setMessage(messageId);
        builder.create().show();
    }

    private void doDeletePackageInternal(final String packageName)
            throws InstallFailedException {
        ServiceConnection mServiceConnection = new ServiceConnection() {
            public void onServiceConnected(ComponentName name, IBinder service) {
                IPrivilegedService privService = IPrivilegedService.Stub.asInterface(service);

                IPrivilegedCallback callback = new IPrivilegedCallback.Stub() {
                    @Override
                    public void handleResult(String packageName, int returnCode) throws RemoteException {
                        if (returnCode == DELETE_SUCCEEDED) {
                            sendBroadcastUninstall(packageName, ACTION_UNINSTALL_COMPLETE);
                        } else {
                            sendBroadcastUninstall(packageName, ACTION_UNINSTALL_INTERRUPTED,
                                    "Error " + returnCode + ": "
                                            + sUninstallReturnCodes.get(returnCode));
                        }
                    }
                };

                try {
                    privService.deletePackage(packageName, 0, callback);
                } catch (RemoteException e) {
                    Log.e(TAG, "RemoteException", e);
                    sendBroadcastUninstall(packageName, ACTION_UNINSTALL_INTERRUPTED,
                            "connecting to privileged service failed");
                }
            }

            public void onServiceDisconnected(ComponentName name) {
            }
        };

        Intent serviceIntent = new Intent(PRIVILEGED_EXTENSION_SERVICE_INTENT);
        serviceIntent.setPackage(PRIVILEGED_EXTENSION_PACKAGE_NAME);
        mContext.getApplicationContext().bindService(serviceIntent, mServiceConnection,
                Context.BIND_AUTO_CREATE);
    }

    @Override
    protected boolean isUnattended() {
        return true;
    }

}
