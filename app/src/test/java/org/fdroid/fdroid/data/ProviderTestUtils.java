package org.fdroid.fdroid.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

import org.robolectric.shadows.ShadowContentResolver;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class ProviderTestUtils {

    public static void assertCantDelete(ShadowContentResolver resolver, Uri uri) {
        try {
            resolver.delete(uri, null, null);
            fail();
        } catch (UnsupportedOperationException e) {
            // Successful condition
        } catch (Exception e) {
            fail();
        }
    }

    public static void assertCantUpdate(ShadowContentResolver resolver, Uri uri) {
        try {
            resolver.update(uri, new ContentValues(), null, null);
            fail();
        } catch (UnsupportedOperationException e) {
            // Successful condition
        } catch (Exception e) {
            fail();
        }
    }

    public static void assertInvalidUri(ShadowContentResolver resolver, String uri) {
        assertInvalidUri(resolver, Uri.parse(uri));
    }

    public static void assertValidUri(ShadowContentResolver resolver, String uri, String[] projection) {
        assertValidUri(resolver, Uri.parse(uri), projection);
    }

    public static void assertInvalidUri(ShadowContentResolver resolver, Uri uri) {
        Cursor cursor = resolver.query(uri, new String[] {}, null, null, null);
        assertNull(cursor);
    }

    public static void assertValidUri(ShadowContentResolver resolver, Uri uri, String[] projection) {
        Cursor cursor = resolver.query(uri, projection, null, null, null);
        assertNotNull(cursor);
        cursor.close();
    }

    public static void assertValidUri(ShadowContentResolver resolver, Uri actualUri, String expectedUri, String[] projection) {
        assertValidUri(resolver, actualUri, projection);
        assertEquals(expectedUri, actualUri.toString());
    }

    public static void assertResultCount(ShadowContentResolver resolver, int expectedCount, Uri uri) {
        Cursor cursor = resolver.query(uri, new String[] {}, null, null, null);
        assertResultCount(expectedCount, cursor);
        cursor.close();
    }

    public static void assertResultCount(int expectedCount, List items) {
        assertNotNull(items);
        assertEquals(expectedCount, items.size());
    }

    public static void assertResultCount(int expectedCount, Cursor result) {
        assertNotNull(result);
        assertEquals(expectedCount, result.getCount());
    }

    public static void assertIsInstalledVersionInDb(ShadowContentResolver resolver, String appId, int versionCode, String versionName) {
        Uri uri = InstalledAppProvider.getAppUri(appId);

        String[] projection = {
                InstalledAppProvider.DataColumns.PACKAGE_NAME,
                InstalledAppProvider.DataColumns.VERSION_CODE,
                InstalledAppProvider.DataColumns.VERSION_NAME,
                InstalledAppProvider.DataColumns.APPLICATION_LABEL,
        };

        Cursor cursor = resolver.query(uri, projection, null, null, null);

        assertNotNull(cursor);
        assertEquals("App \"" + appId + "\" not installed", 1, cursor.getCount());

        cursor.moveToFirst();

        assertEquals(appId, cursor.getString(cursor.getColumnIndex(InstalledAppProvider.DataColumns.PACKAGE_NAME)));
        assertEquals(versionCode, cursor.getInt(cursor.getColumnIndex(InstalledAppProvider.DataColumns.VERSION_CODE)));
        assertEquals(versionName, cursor.getString(cursor.getColumnIndex(InstalledAppProvider.DataColumns.VERSION_NAME)));
        cursor.close();
    }

}
