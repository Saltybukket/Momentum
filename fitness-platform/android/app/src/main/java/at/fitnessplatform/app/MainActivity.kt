package at.fitnessplatform.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.fitnessplatform.core.designsystem.MomentumTheme
import at.fitnessplatform.feature.main.FitnessPlatformRoot
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var startupCoordinator: AppStartupCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MomentumTheme {
                val startupState by startupCoordinator.state.collectAsStateWithLifecycle()
                when (startupState) {
                    AppStartupState.CHECKING -> StartupCheckingScreen()
                    AppStartupState.BLOCKED -> StartupBlockedScreen(startupCoordinator::start)
                    AppStartupState.READY -> FitnessPlatformRoot()
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun StartupCheckingScreen() {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Text(stringResource(R.string.startup_checking), modifier = Modifier.padding(top = 16.dp))
    }
}

@androidx.compose.runtime.Composable
private fun StartupBlockedScreen(onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.startup_blocked_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.startup_blocked_message), modifier = Modifier.padding(vertical = 16.dp))
        Button(onClick = onRetry) { Text(stringResource(R.string.startup_retry)) }
    }
}
