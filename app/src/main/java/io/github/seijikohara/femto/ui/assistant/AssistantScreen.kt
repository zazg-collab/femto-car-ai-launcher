package io.github.seijikohara.femto.ui.assistant

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Activity
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Mic
import com.composables.icons.lucide.Navigation
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.Volume2
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.carassistant.CarActionPayload
import io.github.seijikohara.femto.data.voice.VoiceState
import io.github.seijikohara.femto.ui.common.ModalSheetHost
import io.github.seijikohara.femto.ui.home.components.FemtoHorizontalDivider
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoIcon
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark

internal enum class AssistantOption {
    ASSISTANT,
    VOICE_COMMAND,
    VOICE_SEARCH,
}

@Composable
internal fun AssistantScreen(
    uiState: AssistantUiState,
    onMicTap: () -> Unit,
    onReset: () -> Unit,
    onSubmitQuery: (String) -> Unit,
    onStopAudio: () -> Unit,
    onExecuteAction: (CarActionPayload) -> Unit,
    onLaunchOption: (AssistantOption) -> Unit,
    modifier: Modifier = Modifier,
) = Surface(
    modifier = modifier.fillMaxWidth(),
    color = MaterialTheme.colorScheme.background,
) {
    Column(
        modifier = Modifier.padding(FemtoDimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 1. Header with Persona Badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "Bernadya x Raisha AI",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "Mazda CX-5 In-Car Voice Copilot",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            PersonaBadge(personaUsed = uiState.personaUsed)
        }

        FemtoHorizontalDivider()

        // 2. Main In-Car Voice Surface
        CarVoiceSurface(
            uiState = uiState,
            onMicTap = onMicTap,
            onReset = onReset,
            onSubmitQuery = onSubmitQuery,
            onStopAudio = onStopAudio,
            onExecuteAction = onExecuteAction,
        )

        // 3. Quick Action Chips for Fast Tap while Driving
        QuickChipsRow(onSelectPrompt = onSubmitQuery)

        // 4. Secondary Fallback Options
        FemtoHorizontalDivider()
        Text(
            text = "Asisten Cadangan Sistem",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DelegationRows(onLaunchOption = onLaunchOption)
    }
}

@Composable
private fun PersonaBadge(
    personaUsed: String,
    modifier: Modifier = Modifier,
) {
    val (label, icon) =
        when (personaUsed) {
            "raisha" -> "💼 Raisha Executive" to Lucide.Activity
            "bernadya" -> "🌸 Bernadya Companion" to Lucide.Sparkles
            else -> "✨ Hybrid AI Copilot" to Lucide.Sparkles
        }

    Row(
        modifier =
            modifier
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FemtoIcon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun CarVoiceSurface(
    uiState: AssistantUiState,
    onMicTap: () -> Unit,
    onReset: () -> Unit,
    onSubmitQuery: (String) -> Unit,
    onStopAudio: () -> Unit,
    onExecuteAction: (CarActionPayload) -> Unit,
    modifier: Modifier = Modifier,
) = Column(
    modifier = modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(16.dp),
) {
    val listening = uiState.voice is VoiceState.Listening

    // Mic Button
    MicButton(
        listening = listening,
        isThinking = uiState.isThinking,
        onClick = onMicTap,
    )

    // Status or Prompt text
    if (uiState.isThinking) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(
                text = "Bernadya & Raisha sedang menganalisis data...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    } else if (uiState.replyText.isNotBlank()) {
        // AI Reply Bubble (Large glanceable text for automotive distance)
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        FemtoIcon(
                            imageVector = if (uiState.isSpeaking) Lucide.Volume2 else Lucide.Sparkles,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = if (uiState.isSpeaking) "Sedang berbicara..." else "Jawaban Asisten",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    if (uiState.isSpeaking) {
                        Text(
                            text = "Hentikan Suara",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.clickable(onClick = onStopAudio),
                        )
                    }
                }

                Text(
                    text = uiState.replyText,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp, lineHeight = 26.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        // Action CTA if present (e.g. Navigate to Google Maps)
        uiState.pendingAction?.let { action ->
            if (action.type == "NAVIGATE" && !action.query.isNullOrBlank()) {
                PrimaryAction(
                    icon = Lucide.Navigation,
                    label = "Arahkan ke ${action.query}",
                    onClick = { onExecuteAction(action) },
                )
            }
        }
    } else {
        val prompt =
            when (val v = uiState.voice) {
                is VoiceState.Listening -> v.partial.ifBlank { "Mendengarkan suara Bossfren..." }
                is VoiceState.Result -> v.text
                is VoiceState.Failed -> stringResource(v.messageRes)
                else -> "Tekan mic atau tombol setir Mazda untuk mulai berbicara"
            }
        Text(
            text = prompt,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
            color =
                if (uiState.voice is VoiceState.Failed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onBackground
                },
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun QuickChipsRow(
    onSelectPrompt: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val quickPrompts =
        listOf(
            "📊 Omset & closing hari ini" to "Raisha, update closing CS dan omset hari ini",
            "🚀 Performa iklan Meta" to "Raisha, bagaimana performa spend dan ROAS Meta Ads hari ini?",
            "📈 Cek saham IDX" to "Bernadya, cek analisa IHSG dan radar saham hari ini",
            "☕ Curhat santai" to "Bernadya, temenin ngobrol dong macet banget di jalan",
        )

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        quickPrompts.forEach { (label, fullQuery) ->
            Box(
                modifier =
                    Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onSelectPrompt(fullQuery) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MicButton(
    listening: Boolean,
    isThinking: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "mic-pulse")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (listening) 1.15f else 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "mic-scale",
    )
    Box(
        modifier =
            modifier
                .size(FemtoDimens.MinTouchTarget + 24.dp)
                .scale(if (listening) pulse else 1f)
                .clip(CircleShape)
                .background(
                    if (listening) {
                        MaterialTheme.colorScheme.primary
                    } else if (isThinking) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                ).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        FemtoIcon(
            imageVector = if (isThinking) Lucide.Sparkles else Lucide.Mic,
            contentDescription = stringResource(R.string.assistant_voice_mic),
            tint = if (listening) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(34.dp),
        )
    }
}

@Composable
private fun PrimaryAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = Row(
    modifier =
        modifier
            .defaultMinSize(minHeight = FemtoDimens.MinTouchTarget)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
) {
    FemtoIcon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.size(FemtoDimens.InlineIconSize),
    )
    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onPrimary,
    )
}

@Composable
private fun DelegationRows(
    onLaunchOption: (AssistantOption) -> Unit,
    modifier: Modifier = Modifier,
) = Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(FemtoDimens.GridGutter),
) {
    AssistantOptionRow(
        option = AssistantOption.ASSISTANT,
        icon = Lucide.Sparkles,
        label = stringResource(R.string.assistant_option_assistant),
        onLaunchOption = onLaunchOption,
    )
    AssistantOptionRow(
        option = AssistantOption.VOICE_SEARCH,
        icon = Lucide.Search,
        label = stringResource(R.string.assistant_option_voice_search),
        onLaunchOption = onLaunchOption,
    )
}

@Composable
private fun AssistantOptionRow(
    option: AssistantOption,
    icon: ImageVector,
    label: String,
    onLaunchOption: (AssistantOption) -> Unit,
    modifier: Modifier = Modifier,
) = Row(
    modifier =
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = FemtoDimens.MinTouchTarget)
            .clickable { onLaunchOption(option) }
            .padding(horizontal = FemtoDimens.GridGutter),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(FemtoDimens.GridGutter),
) {
    FemtoIcon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.size(FemtoDimens.InlineIconSize),
    )
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onBackground,
    )
}
