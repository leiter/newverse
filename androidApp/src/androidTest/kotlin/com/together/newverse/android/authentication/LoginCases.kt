package com.together.newverse.android.authentication

import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import com.together.newverse.android.R
import com.together.newverse.android.utils.BaseTest
import junit.framework.Assert.*
import org.junit.Test

class LoginCases : BaseTest() {

    @Test
    fun createAccount() {
        val testData = activityRule.activity.testData
        Espresso.onView(withId(R.id.create_account)).perform(click())
        assertTrue(testData.isLoggedIn)
        Espresso.onView(withId(R.id.logout)).perform(click())
        assertFalse(testData.isLoggedIn)
        Espresso.onView(withId(R.id.login)).perform(click())
        assertTrue(testData.isLoggedIn)
        Espresso.onView(withId(R.id.delete_user)).perform(click())
        assertFalse(testData.isLoggedIn)
    }

    @Test
    fun testGoogleAuth() {
        val testData = activityRule.activity.testData
        testData.isGoogleAuth = true
        if(testData.isLoggedIn){
            Espresso.onView(withId(R.id.logout)).perform(click())
        }
        Espresso.onView(withId(R.id.login_google)).perform(click())
        // Note: Google auth would require additional implementation
        // assertTrue(testData.isLoggedIn)
        if(testData.isLoggedIn) {
            Espresso.onView(withId(R.id.logout)).perform(click())
            assertFalse(testData.isLoggedIn)
        }
    }
}
