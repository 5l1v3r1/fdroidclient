package org.fdroid.fdroid.data;

import android.provider.BaseColumns;

/**
 * The authoritative reference to each table/column which should exist in the database.
 * Constants from this interface should be used in preference to string literals when referring to
 * the tables/columns in the database.
 */
public interface Schema {

    interface AppTable {

        String NAME = DBHelper.TABLE_APP;

        interface Cols {
            String _ID = "rowid as _id"; // Required for CursorLoaders
            String _COUNT = "_count";
            String IS_COMPATIBLE = "compatible";
            String PACKAGE_NAME = "id";
            String NAME = "name";
            String SUMMARY = "summary";
            String ICON = "icon";
            String DESCRIPTION = "description";
            String LICENSE = "license";
            String AUTHOR = "author";
            String EMAIL = "email";
            String WEB_URL = "webURL";
            String TRACKER_URL = "trackerURL";
            String SOURCE_URL = "sourceURL";
            String CHANGELOG_URL = "changelogURL";
            String DONATE_URL = "donateURL";
            String BITCOIN_ADDR = "bitcoinAddr";
            String LITECOIN_ADDR = "litecoinAddr";
            String FLATTR_ID = "flattrID";
            String SUGGESTED_VERSION_CODE = "suggestedVercode";
            String UPSTREAM_VERSION_NAME = "upstreamVersion";
            String UPSTREAM_VERSION_CODE = "upstreamVercode";
            String ADDED = "added";
            String LAST_UPDATED = "lastUpdated";
            String CATEGORIES = "categories";
            String ANTI_FEATURES = "antiFeatures";
            String REQUIREMENTS = "requirements";
            String IGNORE_ALLUPDATES = "ignoreAllUpdates";
            String IGNORE_THISUPDATE = "ignoreThisUpdate";
            String ICON_URL = "iconUrl";
            String ICON_URL_LARGE = "iconUrlLarge";

            interface SuggestedApk {
                String VERSION_NAME = "suggestedApkVersion";
            }

            interface InstalledApp {
                String VERSION_CODE = "installedVersionCode";
                String VERSION_NAME = "installedVersionName";
                String SIGNATURE = "installedSig";
            }

            String[] ALL = {
                    _ID, IS_COMPATIBLE, PACKAGE_NAME, NAME, SUMMARY, ICON, DESCRIPTION,
                    LICENSE, AUTHOR, EMAIL, WEB_URL, TRACKER_URL, SOURCE_URL,
                    CHANGELOG_URL, DONATE_URL, BITCOIN_ADDR, LITECOIN_ADDR, FLATTR_ID,
                    UPSTREAM_VERSION_NAME, UPSTREAM_VERSION_CODE, ADDED, LAST_UPDATED,
                    CATEGORIES, ANTI_FEATURES, REQUIREMENTS, IGNORE_ALLUPDATES,
                    IGNORE_THISUPDATE, ICON_URL, ICON_URL_LARGE,
                    SUGGESTED_VERSION_CODE, SuggestedApk.VERSION_NAME,
                    InstalledApp.VERSION_CODE, InstalledApp.VERSION_NAME,
                    InstalledApp.SIGNATURE,
            };
        }
    }

    interface ApkTable {
        String NAME = DBHelper.TABLE_APK;
        interface Cols extends BaseColumns {
            String _COUNT_DISTINCT_ID = "countDistinct";

            String PACKAGE_NAME    = "id";
            String VERSION_NAME    = "version";
            String REPO_ID         = "repo";
            String HASH            = "hash";
            String VERSION_CODE    = "vercode";
            String NAME            = "apkName";
            String SIZE            = "size";
            String SIGNATURE       = "sig";
            String SOURCE_NAME     = "srcname";
            String MIN_SDK_VERSION = "minSdkVersion";
            String TARGET_SDK_VERSION = "targetSdkVersion";
            String MAX_SDK_VERSION = "maxSdkVersion";
            String PERMISSIONS     = "permissions";
            String FEATURES        = "features";
            String NATIVE_CODE     = "nativecode";
            String HASH_TYPE       = "hashType";
            String ADDED_DATE      = "added";
            String IS_COMPATIBLE   = "compatible";
            String INCOMPATIBLE_REASONS = "incompatibleReasons";
            String REPO_VERSION    = "repoVersion";
            String REPO_ADDRESS    = "repoAddress";

            String[] ALL = {
                    _ID, PACKAGE_NAME, VERSION_NAME, REPO_ID, HASH, VERSION_CODE, NAME,
                    SIZE, SIGNATURE, SOURCE_NAME, MIN_SDK_VERSION, TARGET_SDK_VERSION, MAX_SDK_VERSION,
                    PERMISSIONS, FEATURES, NATIVE_CODE, HASH_TYPE, ADDED_DATE,
                    IS_COMPATIBLE, REPO_VERSION, REPO_ADDRESS, INCOMPATIBLE_REASONS,
            };
        }
    }

    interface RepoTable {
        String NAME = "fdroid_repo";
        interface Cols extends BaseColumns {

            String ADDRESS      = "address";
            String NAME         = "name";
            String DESCRIPTION  = "description";
            String IN_USE       = "inuse";
            String PRIORITY     = "priority";
            String SIGNING_CERT = "pubkey";
            String FINGERPRINT  = "fingerprint";
            String MAX_AGE      = "maxage";
            String LAST_ETAG    = "lastetag";
            String LAST_UPDATED = "lastUpdated";
            String VERSION      = "version";
            String IS_SWAP      = "isSwap";
            String USERNAME     = "username";
            String PASSWORD     = "password";
            String TIMESTAMP    = "timestamp";

            String[] ALL = {
                    _ID, ADDRESS, NAME, DESCRIPTION, IN_USE, PRIORITY, SIGNING_CERT,
                    FINGERPRINT, MAX_AGE, LAST_UPDATED, LAST_ETAG, VERSION, IS_SWAP,
                    USERNAME, PASSWORD, TIMESTAMP,
            };
        }
    }

}
