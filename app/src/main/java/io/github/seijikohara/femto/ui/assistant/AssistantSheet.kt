package io.github.seijikohara.femto.ui.assistant

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.seijikohara.femto.data.carassistant.CarActionPayload
import io.github.seijikohara.femto.ui.common.ImmersiveSheetEffect
import io.github.seijikohara.femto.ui.common.rememberSheetHeight
import io.github.seijikohara.femto.ui.theme.FemtoDimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AssistantSheet(
    onLaunchOption: (AssistantOption) -> Unit,
    onSubmitQuery: (String) -> Unit,
    onExecuteAction: (CarActionPayload) -> Unit,
    onDismiss: () -> Unit,
    fullscreen: Boolean,
    modifier: Modifier = Modifier,
) {
    // Gunakan 0.88f (88% tinggi layar) untuk visibilitas optimal di layar landscape Mazda CX-5
    val sheetHeight = rememberSheetHeight(0.88f)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        ImmersiveSheetEffect(fullscreen)
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(sheetHeight),
        ) {
            AssistantRoute(
                onSubmitQuery = onSubmitQuery,
                onLaunchOption = onLaunchOption,
                onExecuteAction = onExecuteAction,
            )
        }
    }
}
