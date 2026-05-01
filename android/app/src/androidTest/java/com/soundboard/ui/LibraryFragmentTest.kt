package com.soundboard.ui

import androidx.fragment.app.testing.launchFragment
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.soundboard.R
import com.soundboard.data.SampleRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class LibraryFragmentTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var sampleRepository: SampleRepository

    @Test
    fun searchBarIsDisplayed() {
        launchFragment<LibraryFragment>()
        onView(withId(R.id.search_view)).check(matches(isDisplayed()))
    }

    @Test
    fun importFabIsDisplayed() {
        launchFragment<LibraryFragment>()
        onView(withId(R.id.fab_import)).check(matches(isDisplayed()))
    }

    @Test
    fun searchFilterShowsMatchingItemsOnly() {
        hiltRule.inject()

        val tmpFile = File.createTempFile("clip", ".mp3").also { it.writeBytes(byteArrayOf(1)) }
        runBlocking {
            sampleRepository.import("Alpha Sound", tmpFile, 1000L)
            sampleRepository.import("Beta Sound", tmpFile, 1000L)
        }
        tmpFile.delete()

        launchFragment<LibraryFragment>()

        onView(withId(R.id.search_view)).perform(click())
        onView(withId(androidx.appcompat.R.id.search_src_text)).perform(typeText("Alpha"))

        Thread.sleep(400)

        onView(withText("Alpha Sound")).check(matches(isDisplayed()))
    }

    @Test
    fun clickingItemOpensEditSheet() {
        hiltRule.inject()

        val tmpFile = File.createTempFile("clip", ".mp3").also { it.writeBytes(byteArrayOf(1)) }
        runBlocking {
            sampleRepository.import("Edit Me", tmpFile, 1000L)
        }
        tmpFile.delete()

        launchFragment<LibraryFragment>()
        Thread.sleep(300)

        onView(withText("Edit Me")).perform(click())
        Thread.sleep(300)

        onView(withId(R.id.btn_delete)).check(matches(isDisplayed()))
    }

    @Test
    fun deleteButtonShowsConfirmationDialog() {
        hiltRule.inject()

        val tmpFile = File.createTempFile("clip", ".mp3").also { it.writeBytes(byteArrayOf(1)) }
        runBlocking {
            sampleRepository.import("Delete Me", tmpFile, 1000L)
        }
        tmpFile.delete()

        launchFragment<LibraryFragment>()
        Thread.sleep(300)

        onView(withText("Delete Me")).perform(click())
        Thread.sleep(300)

        onView(withId(R.id.btn_delete)).perform(click())
        Thread.sleep(200)

        onView(withText("Delete")).check(matches(isDisplayed()))
    }
}
