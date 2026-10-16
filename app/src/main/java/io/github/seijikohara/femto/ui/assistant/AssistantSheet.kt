package io.github.seijikohara.femto.ui.assistant

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
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
    // Gunakan 0.92f dan skipPartiallyExpanded agar langsung terbuka penuh dan tidak terpotong di layar Mazda CX-5
    val sheetHeight = rememberSheetHeight(0.92f)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetMaxWidth = Dp.Unspecified,
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
