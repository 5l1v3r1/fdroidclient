package org.fdroid.fdroid.data;

import android.app.Application;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

import org.fdroid.fdroid.Assert;
import org.fdroid.fdroid.BuildConfig;
import org.fdroid.fdroid.Utils;
import org.fdroid.fdroid.mock.MockApk;
import org.fdroid.fdroid.mock.MockApp;
import org.fdroid.fdroid.mock.MockRepo;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.fdroid.fdroid.Assert.assertCantDelete;
import static org.fdroid.fdroid.Assert.assertContainsOnly;
import static org.fdroid.fdroid.Assert.assertResultCount;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

@Config(constants = BuildConfig.class, application = Application.class)
@RunWith(RobolectricGradleTestRunner.class)
public class ApkProviderTest extends FDroidProviderTest {

    private static final String[] PROJ = ApkProvider.DataColumns.ALL;

    @Test
    public void testAppApks() {
        for (int i = 1; i <= 10; i++) {
            Assert.insertApk(contentResolver, "org.fdroid.fdroid", i);
            Assert.insertApk(contentResolver, "com.example", i);
        }

        assertTotalApkCount(20);

        Cursor fdroidApks = contentResolver.query(
                ApkProvider.getAppUri("org.fdroid.fdroid"),
                PROJ,
                null, null, null);
        assertResultCount(10, fdroidApks);
        assertBelongsToApp(fdroidApks, "org.fdroid.fdroid");
        fdroidApks.close();

        Cursor exampleApks = contentResolver.query(
                ApkProvider.getAppUri("com.example"),
                PROJ,
                null, null, null);
        assertResultCount(10, exampleApks);
        assertBelongsToApp(exampleApks, "com.example");
        exampleApks.close();

        ApkProvider.Helper.deleteApksByApp(context, new MockApp("com.example"));

        Cursor all = queryAllApks();
        assertResultCount(10, all);
        assertBelongsToApp(all, "org.fdroid.fdroid");
        all.close();
    }

    @Test
    public void testDeleteArbitraryApks() {
        Apk one   = insertApkForRepo("com.example.one", 1, 10);
        Apk two   = insertApkForRepo("com.example.two", 1, 10);
        Apk three = insertApkForRepo("com.example.three", 1, 10);
        Apk four  = insertApkForRepo("com.example.four", 1, 10);
        Apk five  = insertApkForRepo("com.example.five", 1, 10);

        assertTotalApkCount(5);

        assertEquals("com.example.one", one.packageName);
        assertEquals("com.example.two", two.packageName);
        assertEquals("com.example.five", five.packageName);

        String[] expectedIds = {
            "com.example.one",
            "com.example.two",
            "com.example.three",
            "com.example.four",
            "com.example.five",
        };

        List<Apk> all = ApkProvider.Helper.findByRepo(context, new MockRepo(10), ApkProvider.DataColumns.ALL);
        List<String> actualIds = new ArrayList<>();
        for (Apk apk : all) {
            actualIds.add(apk.packageName);
        }

        assertContainsOnly(actualIds, expectedIds);

        List<Apk> toDelete = new ArrayList<>(3);
        toDelete.add(two);
        toDelete.add(three);
        toDelete.add(four);
        ApkProvider.Helper.deleteApks(context, toDelete);

        assertTotalApkCount(2);

        List<Apk> allRemaining = ApkProvider.Helper.findByRepo(context, new MockRepo(10), ApkProvider.DataColumns.ALL);
        List<String> actualRemainingIds = new ArrayList<>();
        for (Apk apk : allRemaining) {
            actualRemainingIds.add(apk.packageName);
        }

        String[] expectedRemainingIds = {
            "com.example.one",
            "com.example.five",
        };

        assertContainsOnly(actualRemainingIds, expectedRemainingIds);
    }

    @Test
    public void testInvalidDeleteUris() {
        Apk apk = new MockApk("org.fdroid.fdroid", 10);

        assertCantDelete(contentResolver, ApkProvider.getContentUri());
        assertCantDelete(contentResolver, ApkProvider.getContentUri("org.fdroid.fdroid", 10));
        assertCantDelete(contentResolver, ApkProvider.getContentUri(apk));
        assertCantDelete(contentResolver, Uri.withAppendedPath(ApkProvider.getContentUri(), "some-random-path"));
    }

    private static final long REPO_KEEP = 1;
    private static final long REPO_DELETE = 2;

    @Test
    public void testRepoApks() {

        // Insert apks into two repos, one of which we will later purge the
        // the apks from.
        for (int i = 1; i <= 5; i++) {
            insertApkForRepo("org.fdroid.fdroid", i, REPO_KEEP);
            insertApkForRepo("com.example." + i, 1, REPO_DELETE);
        }
        for (int i = 6; i <= 10; i++) {
            insertApkForRepo("org.fdroid.fdroid", i, REPO_DELETE);
            insertApkForRepo("com.example." + i, 1, REPO_KEEP);
        }

        assertTotalApkCount(20);

        Cursor cursor = contentResolver.query(
                ApkProvider.getRepoUri(REPO_DELETE), PROJ, null, null, null);
        assertResultCount(10, cursor);
        assertBelongsToRepo(cursor, REPO_DELETE);
        cursor.close();

        int count = ApkProvider.Helper.deleteApksByRepo(context, new MockRepo(REPO_DELETE));
        assertEquals(10, count);

        assertTotalApkCount(10);
        cursor = contentResolver.query(
                ApkProvider.getRepoUri(REPO_DELETE), PROJ, null, null, null);
        assertResultCount(0, cursor);
        cursor.close();

        // The only remaining apks should be those from REPO_KEEP.
        assertBelongsToRepo(queryAllApks(), REPO_KEEP);
    }

    @Test
    public void testQuery() {
        Cursor cursor = queryAllApks();
        assertNotNull(cursor);
        cursor.close();
    }

    @Test
    public void testInsert() {

        // Start with an empty database...
        Cursor cursor = queryAllApks();
        assertNotNull(cursor);
        assertEquals(0, cursor.getCount());
        cursor.close();

        Apk apk = new MockApk("org.fdroid.fdroid", 13);

        // Insert a new record...
        Uri newUri = Assert.insertApk(contentResolver, apk.packageName, apk.versionCode);
        assertEquals(ApkProvider.getContentUri(apk).toString(), newUri.toString());
        cursor = queryAllApks();
        assertNotNull(cursor);
        assertEquals(1, cursor.getCount());

        // And now we should be able to recover these values from the apk
        // value object (because the queryAllApks() helper asks for VERSION_CODE and
        // PACKAGE_NAME.
        cursor.moveToFirst();
        Apk toCheck = new Apk(cursor);
        cursor.close();
        assertEquals("org.fdroid.fdroid", toCheck.packageName);
        assertEquals(13, toCheck.versionCode);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCursorMustMoveToFirst() {
        Assert.insertApk(contentResolver, "org.example.test", 12);
        Cursor cursor = queryAllApks();
        new Apk(cursor);
    }

    @Test
    public void testCount() {
        String[] projectionCount = new String[] {ApkProvider.DataColumns._COUNT};

        for (int i = 0; i < 13; i++) {
            Assert.insertApk(contentResolver, "com.example", i);
        }

        Uri all = ApkProvider.getContentUri();
        Cursor allWithFields = contentResolver.query(all, PROJ, null, null, null);
        Cursor allWithCount = contentResolver.query(all, projectionCount, null, null, null);

        assertResultCount(13, allWithFields);
        allWithFields.close();
        assertResultCount(1, allWithCount);

        allWithCount.moveToFirst();
        int countColumn = allWithCount.getColumnIndex(ApkProvider.DataColumns._COUNT);
        assertEquals(13, allWithCount.getInt(countColumn));
        allWithCount.close();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInsertWithInvalidExtraFieldDescription() {
        assertInvalidExtraField(RepoProvider.DataColumns.DESCRIPTION);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInsertWithInvalidExtraFieldAddress() {
        assertInvalidExtraField(RepoProvider.DataColumns.ADDRESS);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInsertWithInvalidExtraFieldFingerprint() {
        assertInvalidExtraField(RepoProvider.DataColumns.FINGERPRINT);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInsertWithInvalidExtraFieldName() {
        assertInvalidExtraField(RepoProvider.DataColumns.NAME);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInsertWithInvalidExtraFieldSigningCert() {
        assertInvalidExtraField(RepoProvider.DataColumns.SIGNING_CERT);
    }

    public void assertInvalidExtraField(String field) {
        ContentValues invalidRepo = new ContentValues();
        invalidRepo.put(field, "Test data");
        Assert.insertApk(contentResolver, "org.fdroid.fdroid", 10, invalidRepo);
    }

    @Test
    public void testInsertWithValidExtraFields() {

        assertResultCount(0, queryAllApks());

        ContentValues values = new ContentValues();
        values.put(ApkProvider.DataColumns.REPO_ID, 10);
        values.put(ApkProvider.DataColumns.REPO_ADDRESS, "http://example.com");
        values.put(ApkProvider.DataColumns.REPO_VERSION, 3);
        values.put(ApkProvider.DataColumns.FEATURES, "Some features");
        Uri uri = Assert.insertApk(contentResolver, "com.example.com", 1, values);

        assertResultCount(1, queryAllApks());

        String[] projections = ApkProvider.DataColumns.ALL;
        Cursor cursor = contentResolver.query(uri, projections, null, null, null);
        cursor.moveToFirst();
        Apk apk = new Apk(cursor);
        cursor.close();

        // These should have quietly been dropped when we tried to save them,
        // because the provider only knows how to query them (not update them).
        assertEquals(null, apk.repoAddress);
        assertEquals(0, apk.repoVersion);

        // But this should have saved correctly...
        assertEquals("Some features", apk.features.toString());
        assertEquals("com.example.com", apk.packageName);
        assertEquals(1, apk.versionCode);
        assertEquals(10, apk.repo);
    }

    @Test
    public void testKnownApks() {

        for (int i = 0; i < 7; i++) {
            Assert.insertApk(contentResolver, "org.fdroid.fdroid", i);
        }

        for (int i = 0; i < 9; i++) {
            Assert.insertApk(contentResolver, "org.example", i);
        }

        for (int i = 0; i < 3; i++) {
            Assert.insertApk(contentResolver, "com.example", i);
        }

        Assert.insertApk(contentResolver, "com.apk.thingo", 1);

        Apk[] known = {
            new MockApk("org.fdroid.fdroid", 1),
            new MockApk("org.fdroid.fdroid", 3),
            new MockApk("org.fdroid.fdroid", 5),

            new MockApk("com.example", 1),
            new MockApk("com.example", 2),
        };

        Apk[] unknown = {
            new MockApk("org.fdroid.fdroid", 7),
            new MockApk("org.fdroid.fdroid", 9),
            new MockApk("org.fdroid.fdroid", 11),
            new MockApk("org.fdroid.fdroid", 13),

            new MockApk("com.example", 3),
            new MockApk("com.example", 4),
            new MockApk("com.example", 5),

            new MockApk("info.example", 1),
            new MockApk("info.example", 2),
        };

        List<Apk> apksToCheck = new ArrayList<>(known.length + unknown.length);
        Collections.addAll(apksToCheck, known);
        Collections.addAll(apksToCheck, unknown);

        String[] projection = {
            ApkProvider.DataColumns.PACKAGE_NAME,
            ApkProvider.DataColumns.VERSION_CODE,
        };

        List<Apk> knownApks = ApkProvider.Helper.knownApks(context, apksToCheck, projection);

        assertResultCount(known.length, knownApks);

        for (Apk knownApk : knownApks) {
            assertContains(knownApks, knownApk);
        }
    }

    @Test
    public void testFindByApp() {

        for (int i = 0; i < 7; i++) {
            Assert.insertApk(contentResolver, "org.fdroid.fdroid", i);
        }

        for (int i = 0; i < 9; i++) {
            Assert.insertApk(contentResolver, "org.example", i);
        }

        for (int i = 0; i < 3; i++) {
            Assert.insertApk(contentResolver, "com.example", i);
        }

        Assert.insertApk(contentResolver, "com.apk.thingo", 1);

        assertTotalApkCount(7 + 9 + 3 + 1);

        List<Apk> fdroidApks = ApkProvider.Helper.findByPackageName(context, "org.fdroid.fdroid");
        assertResultCount(7, fdroidApks);
        assertBelongsToApp(fdroidApks, "org.fdroid.fdroid");

        List<Apk> exampleApks = ApkProvider.Helper.findByPackageName(context, "org.example");
        assertResultCount(9, exampleApks);
        assertBelongsToApp(exampleApks, "org.example");

        List<Apk> exampleApks2 = ApkProvider.Helper.findByPackageName(context, "com.example");
        assertResultCount(3, exampleApks2);
        assertBelongsToApp(exampleApks2, "com.example");

        List<Apk> thingoApks = ApkProvider.Helper.findByPackageName(context, "com.apk.thingo");
        assertResultCount(1, thingoApks);
        assertBelongsToApp(thingoApks, "com.apk.thingo");
    }

    @Test
    public void testUpdate() {

        Uri apkUri = Assert.insertApk(contentResolver, "com.example", 10);

        String[] allFields = ApkProvider.DataColumns.ALL;
        Cursor cursor = contentResolver.query(apkUri, allFields, null, null, null);
        assertResultCount(1, cursor);

        cursor.moveToFirst();
        Apk apk = new Apk(cursor);
        cursor.close();

        assertEquals("com.example", apk.packageName);
        assertEquals(10, apk.versionCode);

        assertNull(apk.features);
        assertNull(apk.added);
        assertNull(apk.hashType);

        apk.features = Utils.CommaSeparatedList.make("one,two,three");
        long dateTimestamp = System.currentTimeMillis();
        apk.added = new Date(dateTimestamp);
        apk.hashType = "i'm a hash type";

        ApkProvider.Helper.update(context, apk);

        // Should not have inserted anything else, just updated the already existing apk.
        Cursor allCursor = contentResolver.query(ApkProvider.getContentUri(), allFields, null, null, null);
        assertResultCount(1, allCursor);
        allCursor.close();

        Cursor updatedCursor = contentResolver.query(apkUri, allFields, null, null, null);
        assertResultCount(1, updatedCursor);

        updatedCursor.moveToFirst();
        Apk updatedApk = new Apk(updatedCursor);
        updatedCursor.close();

        assertEquals("com.example", updatedApk.packageName);
        assertEquals(10, updatedApk.versionCode);

        assertNotNull(updatedApk.features);
        assertNotNull(updatedApk.added);
        assertNotNull(updatedApk.hashType);

        assertEquals("one,two,three", updatedApk.features.toString());
        assertEquals(new Date(dateTimestamp).getYear(), updatedApk.added.getYear());
        assertEquals(new Date(dateTimestamp).getMonth(), updatedApk.added.getMonth());
        assertEquals(new Date(dateTimestamp).getDay(), updatedApk.added.getDay());
        assertEquals("i'm a hash type", updatedApk.hashType);
    }

    @Test
    public void testFind() {
        // Insert some random apks either side of the "com.example", so that
        // the Helper.find() method doesn't stumble upon the app we are interested
        // in by shear dumb luck...
        for (int i = 0; i < 10; i++) {
            Assert.insertApk(contentResolver, "org.fdroid.apk." + i, i);
        }

        ContentValues values = new ContentValues();
        values.put(ApkProvider.DataColumns.VERSION_NAME, "v1.1");
        values.put(ApkProvider.DataColumns.HASH, "xxxxyyyy");
        values.put(ApkProvider.DataColumns.HASH_TYPE, "a hash type");
        Assert.insertApk(contentResolver, "com.example", 11, values);

        // ...and a few more for good measure...
        for (int i = 15; i < 20; i++) {
            Assert.insertApk(contentResolver, "com.other.thing." + i, i);
        }

        Apk apk = ApkProvider.Helper.find(context, "com.example", 11);

        assertNotNull(apk);

        // The find() method populates ALL fields if you don't specify any,
        // so we expect to find each of the ones we inserted above...
        assertEquals("com.example", apk.packageName);
        assertEquals(11, apk.versionCode);
        assertEquals("v1.1", apk.versionName);
        assertEquals("xxxxyyyy", apk.hash);
        assertEquals("a hash type", apk.hashType);

        String[] projection = {
            ApkProvider.DataColumns.PACKAGE_NAME,
            ApkProvider.DataColumns.HASH,
        };

        Apk apkLessFields = ApkProvider.Helper.find(context, "com.example", 11, projection);

        assertNotNull(apkLessFields);

        assertEquals("com.example", apkLessFields.packageName);
        assertEquals("xxxxyyyy", apkLessFields.hash);

        // Didn't ask for these fields, so should be their default values...
        assertNull(apkLessFields.hashType);
        assertNull(apkLessFields.versionName);
        assertEquals(0, apkLessFields.versionCode);

        Apk notFound = ApkProvider.Helper.find(context, "com.doesnt.exist", 1000);
        assertNull(notFound);
    }

    protected final Cursor queryAllApks() {
        return contentResolver.query(ApkProvider.getContentUri(), PROJ, null, null, null);
    }

    protected void assertContains(List<Apk> apks, Apk apk) {
        boolean found = false;
        for (Apk a : apks) {
            if (a.versionCode == apk.versionCode && a.packageName.equals(apk.packageName)) {
                found = true;
                break;
            }
        }
        if (!found) {
            fail("Apk [" + apk + "] not found in " + Assert.listToString(apks));
        }
    }

    protected void assertBelongsToApp(Cursor apks, String appId) {
        assertBelongsToApp(ApkProvider.Helper.cursorToList(apks), appId);
    }

    protected void assertBelongsToApp(List<Apk> apks, String appId) {
        for (Apk apk : apks) {
            assertEquals(appId, apk.packageName);
        }
    }

    protected void assertTotalApkCount(int expected) {
        assertResultCount(expected, queryAllApks());
    }

    protected void assertBelongsToRepo(Cursor apkCursor, long repoId) {
        for (Apk apk : ApkProvider.Helper.cursorToList(apkCursor)) {
            assertEquals(repoId, apk.repo);
        }
    }

    protected Apk insertApkForRepo(String id, int versionCode, long repoId) {
        ContentValues additionalValues = new ContentValues();
        additionalValues.put(ApkProvider.DataColumns.REPO_ID, repoId);
        Uri uri = Assert.insertApk(contentResolver, id, versionCode, additionalValues);
        return ApkProvider.Helper.get(context, uri);
    }
}
