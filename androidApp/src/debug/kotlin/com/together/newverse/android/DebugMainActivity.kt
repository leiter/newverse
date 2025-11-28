package com.together.newverse.android

import android.graphics.Bitmap
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import com.together.newverse.domain.repository.ArticleRepository
import com.together.newverse.domain.repository.BasketRepository
import com.together.newverse.domain.repository.OrderRepository
import com.together.newverse.domain.repository.ProfileRepository
import com.together.newverse.stories.BuyerStoriesRunner
import com.together.newverse.stories.BuyerStory
import com.together.newverse.ui.screens.buy.BasketViewModel
import com.together.newverse.ui.state.BuyAppViewModel
import com.together.newverse.ui.theme.NewverseTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.android.ext.android.inject
import org.koin.compose.KoinContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume

/**
 * Debug MainActivity for running buyer stories
 * This is only available in debug builds
 */
class DebugMainActivity : ComponentActivity() {

    private val unifiedViewModel: BuyAppViewModel by inject()
    private val basketViewModel: BasketViewModel by inject()
    private val articleRepository: ArticleRepository by inject()
    private val basketRepository: BasketRepository by inject()
    private val orderRepository: OrderRepository by inject()
    private val profileRepository: ProfileRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTheme(R.style.AppTheme)
        enableEdgeToEdge()

        setContent {
            KoinContext {
                NewverseTheme {
                    DebugStoriesScreen()
                }
            }
        }
    }

    /**
     * Takes a screenshot of the current window and saves it to Download/testScreen/ directory.
     *
     * @param filename The name of the screenshot file (without extension)
     * @return The file path where the screenshot was saved, or null if failed
     */
    suspend fun takeScreenshot(filename: String = "screenshot_${System.currentTimeMillis()}"): String? {
        return suspendCancellableCoroutine { continuation ->
            try {
                val window = this.window
                val view = window.decorView.rootView

                // Create bitmap with window dimensions
                val bitmap = createBitmap(view.width, view.height)

                // Use PixelCopy for hardware-accelerated windows
                PixelCopy.request(
                    window,
                    bitmap,
                    { copyResult ->
                        if (copyResult == PixelCopy.SUCCESS) {
                            // Save to Download/testScreen/ directory
                            val result = saveBitmapToFile(bitmap, filename)
                            continuation.resume(result)
                        } else {
                            Log.e(TAG, "PixelCopy failed with result: $copyResult")
                            continuation.resume(null)
                        }
                    },
                    Handler(Looper.getMainLooper())
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error taking screenshot", e)
                continuation.resume(null)
            }
        }
    }

    /**
     * Saves a bitmap to the Download/testScreen/ directory.
     */
    private fun saveBitmapToFile(bitmap: Bitmap, filename: String): String? {
        return try {
            // Get Download directory
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val testScreenDir = File(downloadDir, "testScreen")

            // Create directory if it doesn't exist
            if (!testScreenDir.exists()) {
                testScreenDir.mkdirs()
            }

            // Create file
            val file = File(testScreenDir, "$filename.png")

            // Write bitmap to file
            FileOutputStream(file).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                outputStream.flush()
            }

            Log.d(TAG, "Screenshot saved to: ${file.absolutePath}")
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error saving screenshot", e)
            null
        }
    }

    companion object {
        private const val TAG = "DebugMainActivity"
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun DebugStoriesScreen() {
        val scope = rememberCoroutineScope()
        var isRunning by remember { mutableStateOf(false) }
        var currentStory by remember { mutableStateOf<String?>(null) }

        val runner = remember {
            BuyerStoriesRunner(
                buyAppViewModel = unifiedViewModel,
                basketViewModel = basketViewModel,
                articleRepository = articleRepository,
                basketRepository = basketRepository,
                orderRepository = orderRepository,
                profileRepository = profileRepository
            )
        }

        // Auto-run Story 1 on launch
        LaunchedEffect(Unit) {
            isRunning = true
            currentStory = BuyerStory.STORY_1_BROWSE_AND_ADD.displayName
            try {
                Log.d("DebugMainActivity", "Auto-starting Story 1")
                runner.runStory(BuyerStory.STORY_1_BROWSE_AND_ADD)
                Log.d("DebugMainActivity", "Completed Story 1")
            } catch (e: Exception) {
                Log.e("DebugMainActivity", "Error running Story 1", e)
            } finally {
                isRunning = false
                currentStory = null
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Debug: Buyer Stories") }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Buyer User Interaction Stories",
                    style = MaterialTheme.typography.headlineSmall
                )

                Text(
                    text = "Check Logcat for detailed output (tag: System.out)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (currentStory != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Text(
                            text = "Running: $currentStory",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Individual story buttons
                StoryButton(
                    story = BuyerStory.STORY_1_BROWSE_AND_ADD,
                    isRunning = isRunning,
                    onRunStory = { story ->
                        isRunning = true
                        currentStory = story.displayName
                        scope.launch {
                            try {
                                Log.d("DebugMainActivity", "Starting ${story.displayName}")
                                runner.runStory(story)
                                Log.d("DebugMainActivity", "Completed ${story.displayName}")
                            } catch (e: Exception) {
                                Log.e("DebugMainActivity", "Error running story", e)
                            } finally {
                                isRunning = false
                                currentStory = null
                            }
                        }
                    }
                )

                StoryButton(
                    story = BuyerStory.STORY_2_MODIFY_QUANTITIES,
                    isRunning = isRunning,
                    onRunStory = { story ->
                        isRunning = true
                        currentStory = story.displayName
                        scope.launch {
                            try {
                                runner.runStory(story)
                            } catch (e: Exception) {
                                Log.e("DebugMainActivity", "Error running story", e)
                            } finally {
                                isRunning = false
                                currentStory = null
                            }
                        }
                    }
                )

                StoryButton(
                    story = BuyerStory.STORY_3_CHECKOUT,
                    isRunning = isRunning,
                    onRunStory = { story ->
                        isRunning = true
                        currentStory = story.displayName
                        scope.launch {
                            try {
                                runner.runStory(story)
                            } catch (e: Exception) {
                                Log.e("DebugMainActivity", "Error running story", e)
                            } finally {
                                isRunning = false
                                currentStory = null
                            }
                        }
                    }
                )

                StoryButton(
                    story = BuyerStory.STORY_4_EDIT_ORDER,
                    isRunning = isRunning,
                    onRunStory = { story ->
                        isRunning = true
                        currentStory = story.displayName
                        scope.launch {
                            try {
                                runner.runStory(story)
                            } catch (e: Exception) {
                                Log.e("DebugMainActivity", "Error running story", e)
                            } finally {
                                isRunning = false
                                currentStory = null
                            }
                        }
                    }
                )

                StoryButton(
                    story = BuyerStory.STORY_5_COMPLETE_JOURNEY,
                    isRunning = isRunning,
                    onRunStory = { story ->
                        isRunning = true
                        currentStory = story.displayName
                        scope.launch {
                            try {
                                runner.runStory(story)
                            } catch (e: Exception) {
                                Log.e("DebugMainActivity", "Error running story", e)
                            } finally {
                                isRunning = false
                                currentStory = null
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))
                Divider()
                Spacer(modifier = Modifier.height(16.dp))

                // Run all stories button
                Button(
                    onClick = {
                        isRunning = true
                        currentStory = "All Stories"
                        scope.launch {
                            try {
                                Log.d("DebugMainActivity", "Starting all stories")
                                runner.runAllStories()
                                Log.d("DebugMainActivity", "Completed all stories")
                            } catch (e: Exception) {
                                Log.e("DebugMainActivity", "Error running all stories", e)
                            } finally {
                                isRunning = false
                                currentStory = null
                            }
                        }
                    },
                    enabled = !isRunning,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Run All Stories in Sequence")
                }
            }
        }
    }

    @Composable
    private fun StoryButton(
        story: BuyerStory,
        isRunning: Boolean,
        onRunStory: (BuyerStory) -> Unit,
    ) {
        OutlinedButton(
            onClick = { onRunStory(story) },
            enabled = !isRunning,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = story.displayName,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = story.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
