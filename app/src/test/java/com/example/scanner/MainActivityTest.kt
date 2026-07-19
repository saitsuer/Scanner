package com.example.scanner

import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivityTest {

    @Test
    fun homeShowsScanActions() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        assertNotNull(activity.findViewById(R.id.buttonScanDocument))
        assertNotNull(activity.findViewById(R.id.buttonScanId))
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.textEmpty).visibility)
    }
}
