package com.soundboard.ui

import androidx.fragment.app.testing.launchFragment
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.soundboard.R
import com.soundboard.data.ActivePlayback
import com.soundboard.data.PlaybackRepository
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class NowPlayingFragmentPlaybackTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    private val stopCalled = mutableListOf<String>()

    @BindValue
    @JvmField
    val playbackRepository: PlaybackRepository = object : PlaybackRepository() {
        override fun requestStop(handle: String) {
            stopCalled.add(handle)
            super.requestStop(handle)
        }
    }.apply {
        add(
            ActivePlayback(
                handle = "h1",
                sampleId = "s1",
                sampleName = "Test Sound",
                startedAt = 0L,
                durationMs = 3000L,
            )
        )
    }

    @Test
    fun soundNameShownForActivePlayback() {
        launchFragment<NowPlayingFragment>()
        onView(withText("Test Sound")).check(matches(isDisplayed()))
    }

    @Test
    fun stopButtonVisibleForActivePlayback() {
        launchFragment<NowPlayingFragment>()
        onView(withId(R.id.btn_stop)).check(matches(isDisplayed()))
    }

    @Test
    fun clickingStopButtonCallsRequestStop() {
        launchFragment<NowPlayingFragment>()
        onView(withId(R.id.btn_stop)).perform(click())
        Thread.sleep(200)
        assertTrue("requestStop should have been called with h1", "h1" in stopCalled)
    }
}
