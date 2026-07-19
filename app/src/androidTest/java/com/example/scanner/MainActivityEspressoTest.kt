package com.example.scanner

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityEspressoTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Before
    fun prepareDevice() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        if (!device.isScreenOn) device.wakeUp()
        device.executeShellCommand("wm dismiss-keyguard")
        activityRule.scenario.onActivity { it.window.decorView.requestFocus() }
    }

    @Test
    fun homeScreenShowsScannerActions() {
        onView(withText("Scanner")).check(matches(isDisplayed()))
        onView(withId(R.id.buttonScanDocument)).check(matches(isDisplayed()))
        onView(withId(R.id.buttonScanId)).check(matches(isDisplayed()))
        onView(withId(R.id.textEmpty)).check(matches(isDisplayed()))
    }
}
