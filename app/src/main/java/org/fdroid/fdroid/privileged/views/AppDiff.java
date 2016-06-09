/*
**
** Copyright 2007, The Android Open Source Project
** Copyright 2015 Dominik Schürmann <dominik@dominikschuermann.de>
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

package org.fdroid.fdroid.privileged.views;

import android.annotation.TargetApi;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import org.fdroid.fdroid.Permission;
import org.fdroid.fdroid.data.Apk;

import java.util.ArrayList;

@TargetApi(Build.VERSION_CODES.M)
public class AppDiff {

    private final PackageManager mPm;
    public final PackageInfo mPkgInfo;

    public ApplicationInfo mInstalledAppInfo;

    /**
     * Constructor based on F-Droids Apk object
     */
    public AppDiff(PackageManager mPm, Apk apk) {
        this.mPm = mPm;

        mPkgInfo = new PackageInfo();
        mPkgInfo.packageName = apk.packageName;
        mPkgInfo.applicationInfo = new ApplicationInfo();
        mPkgInfo.requestedPermissions = apk.getFullPermissionsArray();

        init();
    }

    private void init() {
        String pkgName = mPkgInfo.packageName;
        // Check if there is already a package on the device with this name
        // but it has been renamed to something else.
        final String[] oldName = mPm.canonicalToCurrentPackageNames(new String[]{pkgName});
        if (oldName != null && oldName.length > 0 && oldName[0] != null) {
            pkgName = oldName[0];
            mPkgInfo.packageName = pkgName;
            mPkgInfo.applicationInfo.packageName = pkgName;
        }
        // Check if package is already installed
        try {
            // This is a little convoluted because we want to get all uninstalled
            // apps, but this may include apps with just data, and if it is just
            // data we still want to count it as "installed".
            //noinspection WrongConstant (lint is actually wrong here!)
            mInstalledAppInfo = mPm.getApplicationInfo(pkgName,
                    PackageManager.GET_UNINSTALLED_PACKAGES);
            if ((mInstalledAppInfo.flags & ApplicationInfo.FLAG_INSTALLED) == 0) {
                mInstalledAppInfo = null;
            }
        } catch (PackageManager.NameNotFoundException e) {
            mInstalledAppInfo = null;
        }
    }
}
