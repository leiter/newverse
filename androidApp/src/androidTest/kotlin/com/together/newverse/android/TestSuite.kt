package com.together.newverse.android

import com.together.newverse.android.authentication.LoginCases
import com.together.newverse.android.authentication.ProductDataTests
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(LoginCases::class, ProductDataTests::class)
class TestSuite
