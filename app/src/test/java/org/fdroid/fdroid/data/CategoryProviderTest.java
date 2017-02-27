package org.fdroid.fdroid.data;

import android.app.Application;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

import org.fdroid.fdroid.BuildConfig;
import org.fdroid.fdroid.R;
import org.fdroid.fdroid.data.Schema.AppMetadataTable.Cols;
import org.fdroid.fdroid.mock.MockRepo;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowContentResolver;

import java.util.List;

import static org.fdroid.fdroid.Assert.assertContainsOnly;
import static org.junit.Assert.assertEquals;

// TODO: Use sdk=24 when Robolectric supports this
@Config(constants = BuildConfig.class, application = Application.class, sdk = 23)
@RunWith(RobolectricGradleTestRunner.class)
public class CategoryProviderTest extends FDroidProviderTest {

    @Before
    public void setup() {
        ShadowContentResolver.registerProvider(AppProvider.getAuthority(), new AppProvider());
    }

    // ========================================================================
    //  "Categories"
    //  (at this point) not an additional table, but we treat them sort of
    //  like they are. That means that if we change the implementation to
    //  use a separate table in the future, these should still pass.
    // ========================================================================

    @Test
    public void queryFreeTextAndCategories() {
        insertAppWithCategory("com.dog", "Dog", "Animal");
        insertAppWithCategory("com.cat", "Cat", "Animal");
        insertAppWithCategory("com.crow", "Crow", "Animal,Bird");
        insertAppWithCategory("com.chicken", "Chicken", "Animal,Bird,Food");
        insertAppWithCategory("com.dog-statue", "Dog Statue", "Animal,Mineral");
        insertAppWithCategory("com.rock", "Rock", "Mineral");
        insertAppWithCategory("com.banana", "Banana", "Food");
        insertAppWithCategory("com.dog-food", "Dog Food", "Food");

        assertPackagesInUri(AppProvider.getSearchUri("dog", "Animal"), new String[] {
                "com.dog",
                "com.dog-statue",
        });

        assertPackagesInUri(AppProvider.getSearchUri("dog", "Food"), new String[] {
                "com.dog-food",
        });

        assertPackagesInUri(AppProvider.getSearchUri("dog", null), new String[] {
                "com.dog",
                "com.dog-statue",
                "com.dog-food",
        });
    }

    @Test
    public void queryAppsInCategories() {
        insertAppWithCategory("com.dog", "Dog", "Animal");
        insertAppWithCategory("com.cat", "Cat", "Animal");
        insertAppWithCategory("com.crow", "Crow", "Animal,Bird");
        insertAppWithCategory("com.chicken", "Chicken", "Animal,Bird,Food");
        insertAppWithCategory("com.bird-statue", "Bird Statue", "Bird,Mineral");
        insertAppWithCategory("com.rock", "Rock", "Mineral");
        insertAppWithCategory("com.banana", "Banana", "Food");

        assertPackagesInCategory("Animal", new String[] {
                "com.dog",
                "com.cat",
                "com.crow",
                "com.chicken",
        });

        assertPackagesInCategory("animal", new String[] {
                "com.dog",
                "com.cat",
                "com.crow",
                "com.chicken",
        });

        assertPackagesInCategory("Bird", new String[]{
                "com.crow",
                "com.chicken",
                "com.bird-statue",
        });

        assertPackagesInCategory("Food", new String[] {
                "com.chicken",
                "com.banana",
        });

        assertPackagesInCategory("Mineral", new String[] {
                "com.rock",
                "com.bird-statue",
        });

        assertNoPackagesInUri(AppProvider.getCategoryUri("Not a category"));
    }

    private void assertNoPackagesInUri(Uri uri) {
        Cursor noApps = contentResolver.query(uri, Cols.ALL, null, null, null);
        assertEquals(noApps.getCount(), 0);
    }

    private void assertPackagesInCategory(String category, String[] expectedPackages) {
        assertPackagesInUri(AppProvider.getCategoryUri(category), expectedPackages);
    }

    private void assertPackagesInUri(Uri uri, String[] expectedPackages) {
        List<App> apps = AppProvider.Helper.cursorToList(contentResolver.query(uri, Cols.ALL, null, null, null));
        AppProviderTest.assertContainsOnlyIds(apps, expectedPackages);
    }

    @Test
    public void topAppsFromCategory() {
        insertAppWithCategory("com.dog", "Dog", "Animal");
        insertAppWithCategory("com.cat", "Cat", "Animal");
        insertAppWithCategory("com.bird", "Bird", "Animal");
        insertAppWithCategory("com.snake", "Snake", "Animal");
        insertAppWithCategory("com.rat", "Rat", "Animal");

        insertAppWithCategory("com.rock", "Rock", "Mineral");
        insertAppWithCategory("com.stone", "Stone", "Mineral");
        insertAppWithCategory("com.boulder", "Boulder", "Mineral");

        insertAppWithCategory("com.banana", "Banana", "Vegetable");
        insertAppWithCategory("com.tomato", "Tomato", "Vegetable");

        assertContainsOnly(topAppsFromCategory("Animal", 3), new String[] {"com.bird", "com.cat", "com.dog", });
        assertContainsOnly(topAppsFromCategory("Animal", 2), new String[] {"com.bird", "com.cat", });
        assertContainsOnly(topAppsFromCategory("Animal", 1), new String[] {"com.bird", });

        assertContainsOnly(topAppsFromCategory("Mineral", 2), new String[] {"com.boulder", "com.rock", });

        assertContainsOnly(topAppsFromCategory("Vegetable", 10), new String[] {"com.banana", "com.tomato", });
    }

    public String[] topAppsFromCategory(String category, int numToGet) {
        List<App> apps = AppProvider.Helper.cursorToList(contentResolver.query(AppProvider.getTopFromCategoryUri(category, numToGet), Cols.ALL, null, null, Cols.NAME));
        String[] packageNames = new String[apps.size()];
        for (int i = 0; i < apps.size(); i++) {
            packageNames[i] = apps.get(i).packageName;
        }

        return packageNames;
    }

    @Test
    public void testCategoriesSingle() {
        insertAppWithCategory("com.dog", "Dog", "Animal");
        insertAppWithCategory("com.rock", "Rock", "Mineral");
        insertAppWithCategory("com.banana", "Banana", "Vegetable");

        List<String> categories = CategoryProvider.Helper.categories(context);
        String[] expected = new String[] {
                context.getResources().getString(R.string.category_Whats_New),
                context.getResources().getString(R.string.category_Recently_Updated),
                context.getResources().getString(R.string.category_All),
                "Animal",
                "Mineral",
                "Vegetable",
        };
        assertContainsOnly(categories, expected);
    }

    @Test
    public void testCategoriesMultiple() {
        long mainRepo = 1;

        insertAppWithCategory("com.rock.dog", "Rock-Dog", "Mineral,Animal", mainRepo);
        insertAppWithCategory("com.dog.rock.apple", "Dog-Rock-Apple", "Animal,Mineral,Vegetable", mainRepo);
        insertAppWithCategory("com.banana.apple", "Banana", "Vegetable,Vegetable", mainRepo);

        List<String> categories = CategoryProvider.Helper.categories(context);
        String[] expected = new String[] {
                context.getResources().getString(R.string.category_Whats_New),
                context.getResources().getString(R.string.category_Recently_Updated),
                context.getResources().getString(R.string.category_All),

                "Animal",
                "Mineral",
                "Vegetable",
        };
        assertContainsOnly(categories, expected);

        int additionalRepo = 2;

        insertAppWithCategory("com.example.game", "Game",
                "Running,Shooting,Jumping,Bleh,Sneh,Pleh,Blah,Test category," +
                "The quick brown fox jumps over the lazy dog,With apostrophe's", additionalRepo);

        List<String> categoriesLonger = CategoryProvider.Helper.categories(context);
        String[] expectedLonger = new String[] {
                context.getResources().getString(R.string.category_Whats_New),
                context.getResources().getString(R.string.category_Recently_Updated),
                context.getResources().getString(R.string.category_All),

                "Animal",
                "Mineral",
                "Vegetable",

                "Running",
                "Shooting",
                "Jumping",
                "Bleh",
                "Sneh",
                "Pleh",
                "Blah",
                "Test category",
                "The quick brown fox jumps over the lazy dog",
                "With apostrophe's",
        };

        assertContainsOnly(categoriesLonger, expectedLonger);

        RepoProvider.Helper.purgeApps(context, new MockRepo(additionalRepo));
        List<String> categoriesAfterPurge = CategoryProvider.Helper.categories(context);
        assertContainsOnly(categoriesAfterPurge, expected);
    }

    private void insertAppWithCategory(String id, String name, String categories) {
        insertAppWithCategory(id, name, categories, 1);
    }

    private void insertAppWithCategory(String id, String name, String categories, long repoId) {
        ContentValues values = new ContentValues(1);
        values.put(Cols.ForWriting.Categories.CATEGORIES, categories);
        AppProviderTest.insertApp(contentResolver, context, id, name, values, repoId);
    }
}
