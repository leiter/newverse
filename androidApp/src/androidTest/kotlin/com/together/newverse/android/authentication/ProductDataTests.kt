package com.together.newverse.android.authentication

import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import com.together.newverse.android.R
import com.together.newverse.android.utils.BaseTest
import junit.framework.Assert.assertTrue
import org.junit.Test

class ProductDataTests : BaseTest() {

    @Test
    fun uploadProductsForTestSeller() {
        val testData = activityRule.activity.testData
        // Device should already be logged in as test seller via Google
        testData.isGoogleAuth = true
        assertTrue("Must be logged in to upload products", testData.isLoggedIn)
        Espresso.onView(withId(R.id.upload_products)).perform(click())
        // Wait for upload to complete (IdleMessenger handles loading state)
    }
}
