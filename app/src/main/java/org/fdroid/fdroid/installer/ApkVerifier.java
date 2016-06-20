/*
 * Copyright (C) 2016 Dominik Schürmann <dominik@dominikschuermann.de>
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

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import org.fdroid.fdroid.Utils;
import org.fdroid.fdroid.data.Apk;

import java.util.Arrays;
import java.util.HashSet;

/**
 * This ApkVerifier verifies that the downloaded apk corresponds to the Apk information
 * displayed to the user. This is especially important in case an unattended installer
 * has been used which displays permissions before download.
 */
public class ApkVerifier {

    private static final String TAG = "ApkVerifier";

    private final Uri localApkUri;
    private final Apk expectedApk;
    private final PackageManager pm;

    ApkVerifier(Context context, Uri localApkUri, Apk expectedApk) {
        this.localApkUri = localApkUri;
        this.expectedApk = expectedApk;
        this.pm = context.getPackageManager();
    }

    public void verifyApk() throws ApkVerificationException {
        // parse downloaded apk file locally
        PackageInfo localApkInfo = pm.getPackageArchiveInfo(
                localApkUri.getPath(), PackageManager.GET_PERMISSIONS);
        if (localApkInfo == null) {
            throw new ApkVerificationException("parsing apk file failed!");
        }

        // check if the apk has the expected packageName
        if (!TextUtils.equals(localApkInfo.packageName, expectedApk.packageName)) {
            throw new ApkVerificationException("apk has unexpected packageName!");
        }

        if (localApkInfo.versionCode < 0) {
            throw new ApkVerificationException("apk has no valid versionCode!");
        }

        // verify permissions, important for unattended installer
        HashSet<String> localPermissions = getLocalPermissionsSet(localApkInfo);
        HashSet<String> expectedPermissions = expectedApk.getFullPermissionsSet();
        Utils.debugLog(TAG, "localPermissions: " + localPermissions);
        Utils.debugLog(TAG, "expectedPermissions: " + expectedPermissions);
        if (!localPermissions.equals(expectedPermissions)) {
            throw new ApkVerificationException("permissions of apk not equals expected permissions!");
        }

        int localTargetSdkVersion = localApkInfo.applicationInfo.targetSdkVersion;
        int expectedTargetSdkVersion = expectedApk.targetSdkVersion;
        Utils.debugLog(TAG, "localTargetSdkVersion: " + localTargetSdkVersion);
        Utils.debugLog(TAG, "expectedTargetSdkVersion: " + expectedTargetSdkVersion);
        if (expectedTargetSdkVersion == Apk.SDK_VERSION_MIN_VALUE) {
            // NOTE: In old fdroidserver versions, targetSdkVersion was not stored inside the repo!
            Log.w(TAG, "Skipping check for targetSdkVersion, not available in this repo!");
        } else if (localTargetSdkVersion != expectedTargetSdkVersion) {
            throw new ApkVerificationException("targetSdkVersion of apk not equals expected targetSdkVersion!");
        }

    }

    private HashSet<String> getLocalPermissionsSet(PackageInfo localApkInfo) {
        String[] localPermissions = localApkInfo.requestedPermissions;
        if (localPermissions == null) {
            return new HashSet<>();
        }

        return new HashSet<>(Arrays.asList(localApkInfo.requestedPermissions));
    }

    public static class ApkVerificationException extends Exception {

        public ApkVerificationException(String message) {
            super(message);
        }

        public ApkVerificationException(Throwable cause) {
            super(cause);
        }
    }

}
