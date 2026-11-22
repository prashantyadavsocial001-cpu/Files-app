package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.FolioApp
import com.example.ui.theme.FolioFlowTheme
import com.example.viewmodel.FolioViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: FolioViewModel by viewModels()
    private var incomingUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        extractIncomingUri(intent)

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            FolioFlowTheme(themeMode = settings.themeMode) {
                FolioApp(
                    viewModel = viewModel,
                    incomingUri = incomingUri,
                    onConsumeIncomingUri = { incomingUri = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractIncomingUri(intent)
    }

    private fun extractIncomingUri(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW && intent.data != null) {
            incomingUri = intent.data
        }
    }
}
