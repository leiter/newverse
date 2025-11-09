package com.together.newverse.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.together.newverse.ui.navigation.AppScaffold
import com.together.newverse.ui.theme.NewverseTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTheme(R.style.AppTheme)

        enableEdgeToEdge()
        setContent {
            NewverseTheme {
                AppScaffold()
            }
        }
    }
}
