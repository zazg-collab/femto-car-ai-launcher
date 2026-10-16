package io.github.seijikohara.femto.ui.assistant

import android.Manifest
import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.seijikohara.femto.data.carassistant.CarActionPayload
import io.github.seijikohara.femto.data.common.hasRecordAudioPermission
import io.github.seijikohara.femto.data.voice.VoiceState

@Composable
internal fun AssistantRoute(
    onSubmitQuery: (String) -> Unit,
    onLaunchOption: (AssistantOption) -> Unit,
    onExecuteAction: (CarActionPayload) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val currentOnExecuteAction by rememberUpdatedState(onExecuteAction)
    val viewModel: AssistantViewModel =
        viewModel(factory = AssistantViewModelFactory(context.applicationContext as Application))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) viewModel.onAction(AssistantAction.StartListening)
        }

    // Otomatis mulai merekam saat sheet terbuka jika permission sudah ada
    LaunchedEffect(Unit) {
        if (context.hasRecordAudioPermission()) {
            viewModel.onAction(AssistantAction.StartListening)
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Auto-eksekusi aksi mobil (Navigasi / Kontrol Musik) secara hands-free tanpa perlu klik layar
    LaunchedEffect(uiState.pendingAction) {
        val action = uiState.pendingAction ?: return@LaunchedEffect
        // Beri jeda 1.2 detik agar suara AI sempat mulai berbicara sebelum berpindah layar
        kotlinx.coroutines.delay(1200)
        currentOnExecuteAction(action)
    }

    val onMicTap: () -> Unit = {
        if (uiState.voice is VoiceState.Listening) {
            viewModel.onAction(AssistantAction.StopListening)
        } else if (context.hasRecordAudioPermission()) {
            viewModel.onAction(AssistantAction.StartListening)
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    AssistantScreen(
        uiState = uiState,
        onMicTap = onMicTap,
        onReset = { viewModel.onAction(AssistantAction.Reset) },
        onSubmitQuery = { query ->
            viewModel.onAction(AssistantAction.SubmitQuery(query))
        },
        onStopAudio = { viewModel.onAction(AssistantAction.StopAudio) },
        onExecuteAction = onExecuteAction,
        onLaunchOption = onLaunchOption,
        modifier = modifier,
    )
}
