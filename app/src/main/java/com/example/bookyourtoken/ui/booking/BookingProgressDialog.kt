package com.example.bookyourtoken.ui.booking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.bookyourtoken.R
import com.example.bookyourtoken.ui.common.prettyName
import com.example.bookyourtoken.ui.theme.successColor
import com.example.bookyourtoken.ui.tokens.BookingLine
import com.example.bookyourtoken.ui.tokens.BookingUiState
import com.example.bookyourtoken.ui.tokens.LineStatus

@Composable
fun BookingProgressDialog(
    state: BookingUiState,
    onDismiss: () -> Unit
) {
    val finished = state.finished
    AlertDialog(
        onDismissRequest = { if (finished) onDismiss() },
        properties = DialogProperties(dismissOnBackPress = finished, dismissOnClickOutside = finished),
        icon = {
            when {
                !finished -> CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                state.failedCount == 0 -> Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = successColor)
                state.bookedCount == 0 -> Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                else -> Icon(Icons.Filled.Info, contentDescription = null)
            }
        },
        title = {
            Text(
                when {
                    !finished -> "Booking…"
                    state.failedCount == 0 -> "All booked"
                    state.bookedCount == 0 -> "Booking failed"
                    else -> "Partly booked"
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                state.lines.forEach { LineRow(it) }
                if (finished) {
                    Text(
                        "${state.bookedCount} booked, ${state.failedCount} failed",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            if (finished) Button(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
private fun LineRow(line: BookingLine) {
    val status = line.status
    Row(verticalAlignment = Alignment.Top) {
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            when (status) {
                LineStatus.Pending -> Icon(
                    painterResource(R.drawable.ic_schedule),
                    contentDescription = "Waiting",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                LineStatus.Sending -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                is LineStatus.Done -> Icon(
                    if (status.success) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                    contentDescription = if (status.success) "Booked" else "Failed",
                    tint = if (status.success) successColor else MaterialTheme.colorScheme.error
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(prettyName(line.item.name), style = MaterialTheme.typography.bodyLarge)
            Text(
                "${line.meal} × ${line.quantity}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (status is LineStatus.Done) {
                Text(
                    status.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (status.success) successColor else MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
