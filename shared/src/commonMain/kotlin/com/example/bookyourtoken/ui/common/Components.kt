package com.example.bookyourtoken.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.bookyourtoken.ui.theme.BrandGradient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    title: String = "Daily reminder time",
    confirmLabel: String = "Save",
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit
) {
    val state = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute, is24Hour = false)
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp)
                )
                TimePicker(state = state)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text(confirmLabel) }
                }
            }
        }
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .padding(start = 4.dp, top = 8.dp, bottom = 8.dp)
            .semantics { heading() }
    )
}

/** A tinted circle holding an icon — used as the visual anchor for cards and headers. */
@Composable
fun IconBadge(
    painter: Painter,
    containerColor: Color,
    contentColor: Color,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(painter = painter, contentDescription = null, tint = contentColor, modifier = Modifier.size(iconSize))
    }
}

/**
 * The gradient header at the top of each tab. It runs under the status bar (white status-bar icons
 * are set by the platforms) and keeps white text readable on the gradient.
 */
@Composable
fun BrandHeader(
    title: String,
    subtitle: String? = null,
    eyebrow: String? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(BrandGradient)
            .statusBarsPadding()
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 8.dp)
            ) {
                eyebrow?.let {
                    Text(it, style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.88f))
                }
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    modifier = Modifier.semantics { heading() }
                )
                subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.92f))
                }
            }
            CompositionLocalProvider(LocalContentColor provides Color.White) {
                Row(content = actions)
            }
        }
    }
}
