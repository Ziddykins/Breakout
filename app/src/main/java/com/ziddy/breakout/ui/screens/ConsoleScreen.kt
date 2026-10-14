package com.ziddy.breakout.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.widthIn
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ziddy.breakout.AppViewModel
import com.ziddy.breakout.model.LogKind
import com.ziddy.breakout.model.LogLine
import com.ziddy.breakout.ui.theme.AppColors
import com.ziddy.breakout.ui.theme.Mono
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val logTimeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

@Composable
fun ConsoleScreen(viewModel: AppViewModel) {
    val log by viewModel.log.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var promptText by remember { mutableStateOf("") }
    val risksAcknowledged = uiState.consoleRisksAcknowledged
    val submitPrompt = {
        val trimmed = promptText.trim()
        when {
            !risksAcknowledged && trimmed.equals("i understand the risks", ignoreCase = true) -> {
                viewModel.acknowledgeConsoleRisks()
                promptText = ""
            }
            risksAcknowledged && !uiState.isConsoleCommandRunning && trimmed.isNotBlank() -> {
                viewModel.runConsoleCommand(trimmed)
                promptText = ""
            }
        }
    }

    LaunchedEffect(log.size) {
        listState.animateScrollToItem(log.size)
    }

    Surface(Modifier.fillMaxSize(), color = AppColors.Console) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Terminal,
                    contentDescription = null,
                    tint = AppColors.Amber,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "console",
                    fontFamily = Mono,
                    color = AppColors.TextPrimary,
                    letterSpacing = 3.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = viewModel::clearLog) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = "Clear log",
                        tint = AppColors.TextSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(1.dp)
                    .background(AppColors.Outline.copy(alpha = 0.4f)),
            )
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(log, key = { i, line -> "${line.timestamp}-$i" }) { _, line ->
                    LogRow(line)
                }
                item {
                    PromptCursor(
                        text = promptText,
                        onTextChange = { promptText = it },
                        enabled = !uiState.isConsoleCommandRunning,
                        onSubmit = submitPrompt,
                    )
                }
            }
        }
    }
}

@Composable
private fun PromptCursor(
    text: String,
    onTextChange: (String) -> Unit,
    enabled: Boolean,
    onSubmit: () -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (enabled) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }
    val transition = rememberInfiniteTransition(label = "cursor")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(550), RepeatMode.Reverse),
        label = "blink",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 76.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("breakout", fontFamily = Mono, fontSize = 11.sp, color = AppColors.Green)
        Text("$ ", fontFamily = Mono, fontSize = 11.sp, color = AppColors.TextSecondary)
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(
                fontFamily = Mono,
                fontSize = 11.sp,
                color = AppColors.TextPrimary,
            ),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    onSubmit()
                    focusRequester.requestFocus()
                    keyboardController?.show()
                },
            ),
            enabled = enabled,
            modifier = Modifier
                .widthIn(min = 48.dp)
                .focusRequester(focusRequester)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) keyboardController?.show()
                },
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier.wrapContentWidth(unbounded = true),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    innerTextField()
                    Text(
                        "█",
                        fontFamily = Mono,
                        fontSize = 11.sp,
                        color = if (enabled) AppColors.Amber else AppColors.TextSecondary,
                        modifier = Modifier.alpha(if (enabled) alpha else alpha * 0.65f),
                    )
                }
            },
        )
    }
}

@Composable
private fun LogRow(line: LogLine) {
    val (symbol, color) = when (line.kind) {
        LogKind.INFO -> "·" to AppColors.Blue
        LogKind.COMMAND -> "$" to AppColors.Amber
        LogKind.OUTPUT -> ">" to AppColors.TextPrimary
        LogKind.ERROR -> "✕" to AppColors.Red
        LogKind.SUCCESS -> "✓" to AppColors.Green
        LogKind.WARN -> "!" to AppColors.Amber
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(
            logTimeFormat.format(Date(line.timestamp)),
            fontFamily = Mono,
            fontSize = 10.sp,
            color = AppColors.TextSecondary.copy(alpha = 0.7f),
            modifier = Modifier.width(62.dp),
        )
        Text(
            symbol,
            fontFamily = Mono,
            fontSize = 11.sp,
            color = color,
            modifier = Modifier.width(14.dp),
        )
        Text(
            line.text,
            fontFamily = Mono,
            fontSize = 11.sp,
            fontWeight = if (line.text == "i understand the risks") FontWeight.Bold else FontWeight.Normal,
            color = if (line.text == "i understand the risks") {
                AppColors.Red
            } else {
                when (line.kind) {
                    LogKind.OUTPUT -> AppColors.TextPrimary.copy(alpha = 0.85f)
                    else -> color
                }
            },
            modifier = if (line.text == "i understand the risks") {
                Modifier.shadow(
                    elevation = 8.dp,
                    ambientColor = AppColors.Red.copy(alpha = 0.28f),
                    spotColor = AppColors.Red.copy(alpha = 0.28f),
                )
            } else {
                Modifier
            },
        )
    }
}

@Composable
private fun PromptCursor() {
    val transition = rememberInfiniteTransition(label = "cursor")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(550), RepeatMode.Reverse),
        label = "blink",
    )
    Row(Modifier.padding(top = 6.dp)) {
        Text(
            "breakout",
            fontFamily = Mono,
            fontSize = 11.sp,
            color = AppColors.Green,
        )
        Text("$ ", fontFamily = Mono, fontSize = 11.sp, color = AppColors.TextSecondary)
        Text(
            "█",
            fontFamily = Mono,
            fontSize = 11.sp,
            color = AppColors.Amber,
            modifier = Modifier.alpha(alpha),
        )
    }
}
