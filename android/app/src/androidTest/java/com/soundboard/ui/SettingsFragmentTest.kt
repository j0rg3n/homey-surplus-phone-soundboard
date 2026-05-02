package com.soundboard.ui

import android.content.Context
import androidx.fragment.app.testing.launchFragment
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.clearText
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.soundboard.Constants
import com.soundboard.R
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SettingsFragmentTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    private val prefs by lazy {
        InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences(Constants.SETTINGS_PREFS, Context.MODE_PRIVATE)
    }

    @After
    fun clearPrefs() {
        prefs.edit().clear().commit()
    }

    @Test
    fun deviceNameFieldIsDisplayed() {
        launchFragment<SettingsFragment>()
        onView(withId(R.id.edit_device_name)).check(matches(isDisplayed()))
    }

    @Test
    fun saveButtonIsDisplayed() {
        launchFragment<SettingsFragment>()
        onView(withId(R.id.btn_save)).check(matches(isDisplayed()))
    }

    @Test
    fun deviceNameSavesToSharedPreferences() {
        launchFragment<SettingsFragment>()

        onView(withId(R.id.edit_device_name)).perform(clearText(), typeText("My Soundboard"))
        onView(withId(R.id.btn_save)).perform(click())

        Thread.sleep(200)
        assert(prefs.getString(Constants.PREF_DEVICE_NAME, null) == "My Soundboard") {
            "Expected device name to be saved as 'My Soundboard'"
        }
    }

    @Test
    fun portSavesToSharedPreferences() {
        launchFragment<SettingsFragment>()

        onView(withId(R.id.btn_advanced)).perform(click())
        onView(withId(R.id.edit_port)).perform(clearText(), typeText("9000"))
        onView(withId(R.id.btn_save)).perform(click())

        Thread.sleep(200)
        assert(prefs.getInt(Constants.PREF_PORT, -1) == 9000) {
            "Expected port to be saved as 9000, got ${prefs.getInt(Constants.PREF_PORT, -1)}"
        }
    }

    @Test
    fun advancedSectionStartsCollapsed() {
        launchFragment<SettingsFragment>()
        onView(withId(R.id.layout_advanced)).check(matches(not(isDisplayed())))
    }

    @Test
    fun advancedSectionExpandsOnButtonClick() {
        launchFragment<SettingsFragment>()
        onView(withId(R.id.btn_advanced)).perform(click())
        onView(withId(R.id.layout_advanced)).check(matches(isDisplayed()))
    }

    @Test
    fun developerTipsSectionStartsCollapsed() {
        launchFragment<SettingsFragment>()
        onView(withId(R.id.layout_dev_tips)).check(matches(not(isDisplayed())))
    }

    @Test
    fun developerTipsSectionExpandsOnButtonClick() {
        launchFragment<SettingsFragment>()
        onView(withId(R.id.btn_dev_tips)).perform(click())
        onView(withId(R.id.layout_dev_tips)).check(matches(isDisplayed()))
    }

    @Test
    fun developerTipsSectionCollapsesOnSecondClick() {
        launchFragment<SettingsFragment>()
        onView(withId(R.id.btn_dev_tips)).perform(click())
        onView(withId(R.id.btn_dev_tips)).perform(click())
        onView(withId(R.id.layout_dev_tips)).check(matches(not(isDisplayed())))
    }

    @Test
    fun stopServiceButtonIsDisplayed() {
        launchFragment<SettingsFragment>()
        onView(withId(R.id.btn_stop_service)).check(matches(isDisplayed()))
    }
}
