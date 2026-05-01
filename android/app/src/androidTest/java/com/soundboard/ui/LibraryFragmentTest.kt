package com.soundboard.ui

import androidx.fragment.app.testing.launchFragment
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.soundboard.R
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class LibraryFragmentTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Test
    fun searchBarIsDisplayed() {
        launchFragment<LibraryFragment>()
        onView(withId(R.id.search_view)).check(matches(isDisplayed()))
    }
}
