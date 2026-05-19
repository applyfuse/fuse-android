package com.applyfuse.fuse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.applyfuse.fuse.features.feed.FeedScreen
import dagger.hilt.android.AndroidEntryPoint

// FUSE: @AndroidEntryPoint is required for hiltViewModel() to
// resolve inside this Activity's composition (FeedScreen uses it).
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // FUSE: Phase 2 navigation host. The Feed feature is the
            // furthest-along screen, so it is the app's start
            // destination for now. A real NavHost (Auth → Feed)
            // arrives with the Profile feature (Phase 3); a single
            // start screen is the minimal correct wiring per
            // fuse-docs ARCHITECTURE.md §5 step 8 ("build the UI")
            // — FeedScreen() pulls FeedViewModel via hiltViewModel()
            // exactly as AuthScreen does.
            MaterialTheme {
                Surface {
                    FeedScreen()
                }
            }
        }
    }
}
