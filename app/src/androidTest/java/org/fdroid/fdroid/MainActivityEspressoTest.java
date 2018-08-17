package org.fdroid.fdroid;

import android.support.test.espresso.IdlingPolicies;
import android.support.test.espresso.ViewInteraction;
import android.support.test.filters.LargeTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;
import android.view.View;
import org.fdroid.fdroid.views.BannerUpdatingRepos;
import org.fdroid.fdroid.views.main.MainActivity;
import org.hamcrest.Matchers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.action.ViewActions.swipeDown;
import static android.support.test.espresso.action.ViewActions.swipeLeft;
import static android.support.test.espresso.action.ViewActions.swipeRight;
import static android.support.test.espresso.action.ViewActions.swipeUp;
import static android.support.test.espresso.action.ViewActions.typeText;
import static android.support.test.espresso.assertion.ViewAssertions.doesNotExist;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class MainActivityEspressoTest {
    public static final String TAG = "MainActivityEspressoTest";

    static {
        IdlingPolicies.setIdlingResourceTimeout(10, TimeUnit.MINUTES);
    }

    @Rule
    public ActivityTestRule<MainActivity> activityTestRule =
            new ActivityTestRule<>(MainActivity.class);

    @Test
    public void bottomNavFlavorCheck() {
        onView(withText(R.string.updates)).check(matches(isDisplayed()));
        onView(withText(R.string.menu_settings)).check(matches(isDisplayed()));
        onView(withText("THIS SHOULD NOT SHOW UP ANYWHERE!!!")).check(doesNotExist());

        assertTrue(BuildConfig.FLAVOR.startsWith("full") || BuildConfig.FLAVOR.startsWith("basic"));

        if (BuildConfig.FLAVOR.startsWith("basic")) {
            onView(withText(R.string.main_menu__latest_apps)).check(matches(isDisplayed()));
            onView(withText(R.string.main_menu__categories)).check(doesNotExist());
            onView(withText(R.string.main_menu__swap_nearby)).check(doesNotExist());
        }

        if (BuildConfig.FLAVOR.startsWith("full")) {
            onView(withText(R.string.main_menu__latest_apps)).check(matches(isDisplayed()));
            onView(withText(R.string.main_menu__categories)).check(matches(isDisplayed()));
            onView(withText(R.string.main_menu__swap_nearby)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void showSettings() {
        ViewInteraction settingsBottonNavButton = onView(
                allOf(withText(R.string.menu_settings), isDisplayed()));
        settingsBottonNavButton.perform(click());
        onView(withText(R.string.preference_manage_installed_apps)).check(matches(isDisplayed()));
        if (BuildConfig.FLAVOR.startsWith("basic") && BuildConfig.APPLICATION_ID.endsWith(".debug")) {
            // TODO fix me by sorting out the flavor applicationId for debug builds in app/build.gradle
            Log.i(TAG, "Skipping the remainder of showSettings test because it just crashes on basic .debug builds");
            return;
        }
        ViewInteraction manageInstalledAppsButton = onView(
                allOf(withText(R.string.preference_manage_installed_apps), isDisplayed()));
        manageInstalledAppsButton.perform(click());
        onView(withText(R.string.installed_apps__activity_title)).check(matches(isDisplayed()));
    }

    @Test
    public void showUpdates() {
        ViewInteraction updatesBottonNavButton = onView(allOf(withText(R.string.updates), isDisplayed()));
        updatesBottonNavButton.perform(click());
        onView(withText(R.string.updates)).check(matches(isDisplayed()));
    }

    @Test
    public void startSwap() {
        if (!BuildConfig.FLAVOR.startsWith("full")) {
            return;
        }
        ViewInteraction nearbyBottonNavButton = onView(
                allOf(withText(R.string.main_menu__swap_nearby), isDisplayed()));
        nearbyBottonNavButton.perform(click());
        ViewInteraction findPeopleButton = onView(
                allOf(withId(R.id.button), withText(R.string.nearby_splash__find_people_button), isDisplayed()));
        findPeopleButton.perform(click());
        onView(withText(R.string.swap_send_fdroid)).check(matches(isDisplayed()));
    }

    @Test
    public void showCategories() {
        if (!BuildConfig.FLAVOR.startsWith("full")) {
            return;
        }
        onView(allOf(withText(R.string.menu_settings), isDisplayed())).perform(click());
        onView(allOf(withText(R.string.main_menu__categories), isDisplayed())).perform(click());
        onView(allOf(withId(R.id.swipe_to_refresh), isDisplayed()))
                .perform(swipeDown())
                .perform(swipeUp())
                .perform(swipeUp())
                .perform(swipeUp())
                .perform(swipeUp())
                .perform(swipeUp())
                .perform(swipeUp())
                .perform(swipeDown())
                .perform(swipeDown())
                .perform(swipeRight())
                .perform(swipeLeft())
                .perform(swipeLeft())
                .perform(swipeLeft())
                .perform(swipeLeft())
                .perform(click());
    }

    @Test
    public void showLatest() throws InterruptedException {
        if (!BuildConfig.FLAVOR.startsWith("full")) {
            return;
        }
        onView(Matchers.<View>instanceOf(BannerUpdatingRepos.class)).check(matches(not(isDisplayed())));
        onView(allOf(withText(R.string.menu_settings), isDisplayed())).perform(click());
        onView(allOf(withText(R.string.main_menu__latest_apps), isDisplayed())).perform(click());
        onView(allOf(withId(R.id.swipe_to_refresh), isDisplayed()))
                .perform(swipeDown())
                .perform(swipeUp())
                .perform(swipeUp())
                .perform(swipeUp())
                .perform(swipeDown())
                .perform(swipeUp())
                .perform(swipeDown())
                .perform(swipeDown())
                .perform(swipeDown())
                .perform(swipeDown())
                .perform(click());
    }

    @Test
    public void showSearch() {
        onView(allOf(withText(R.string.menu_settings), isDisplayed())).perform(click());
        onView(withId(R.id.fab_search)).check(doesNotExist());
        if (!BuildConfig.FLAVOR.startsWith("full")) {
            return;
        }
        onView(allOf(withText(R.string.main_menu__latest_apps), isDisplayed())).perform(click());
        onView(allOf(withId(R.id.fab_search), isDisplayed())).perform(click());
        onView(withId(R.id.sort)).check(matches(isDisplayed()));
        onView(allOf(withId(R.id.search), isDisplayed()))
                .perform(click())
                .perform(typeText("test"));
        onView(allOf(withId(R.id.sort), isDisplayed())).perform(click());
    }
}