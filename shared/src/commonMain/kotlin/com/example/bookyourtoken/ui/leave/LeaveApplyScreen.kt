package com.example.bookyourtoken.ui.leave

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bookyourtoken.data.DateUtils
import com.example.bookyourtoken.data.models.Approver
import com.example.bookyourtoken.data.models.LeaveType
import com.example.bookyourtoken.ui.common.BrandHeader
import com.example.bookyourtoken.ui.common.TimePickerDialog
import com.example.bookyourtoken.ui.common.formatTime
import com.example.bookyourtoken.ui.theme.successColor
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

private enum class Picker { FromDate, FromTime, ToDate, ToTime, Staff }

/**
 * The apply form. [onSent] fires whenever a request reached (or may have reached) the portal, so the
 * history reloads; [onClose] leaves the form.
 */
@Composable
fun LeaveApplyScreen(onSent: () -> Unit, onClose: () -> Unit, viewModel: LeaveApplyViewModel) {
    val options by viewModel.options.collectAsStateWithLifecycle()
    val form by viewModel.form.collectAsStateWithLifecycle()
    val applyState by viewModel.applyState.collectAsStateWithLifecycle()

    val sent = (applyState as? ApplyState.Done)?.sent == true
    LaunchedEffect(sent) { if (sent) onSent() }

    Scaffold(
        topBar = {
            BrandHeader(
                title = "Apply for leave",
                subtitle = "Goes to the staff member you pick",
                actions = {
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close") }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val state = options) {
                LeaveOptionsState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                is LeaveOptionsState.Error -> OptionsError(state.message, onRetry = viewModel::loadOptions)
                is LeaveOptionsState.Ready -> LeaveFormContent(state, form, viewModel)
            }
        }
    }

    when (val apply = applyState) {
        is ApplyState.Confirm -> ConfirmApplyDialog(apply.request, onConfirm = viewModel::confirmApply, onDismiss = viewModel::dismissApply)
        is ApplyState.Running -> SendingDialog()
        is ApplyState.Done -> ApplyResultDialog(
            apply,
            onDismiss = {
                viewModel.dismissApply()
                if (apply.success) onClose()
            }
        )
        null -> Unit
    }
}

@Composable
private fun LeaveFormContent(options: LeaveOptionsState.Ready, form: LeaveForm, viewModel: LeaveApplyViewModel) {
    var picker by remember { mutableStateOf<Picker?>(null) }
    val now = DateUtils.now()
    val today = now.date
    val problem = dateProblem(form, now)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        LeaveTypeField(options.types, form.typeId, onPick = viewModel::setType)

        PickerField(
            label = "Approving staff",
            value = options.approvers.firstOrNull { it.staffId == form.staffId }?.name,
            placeholder = "Choose who approves",
            onClick = { picker = Picker.Staff },
            trailing = true,
            modifier = Modifier.fillMaxWidth()
        )

        DateTimeRow(
            heading = "From",
            date = form.fromDate,
            time = form.fromTime,
            onDate = { picker = Picker.FromDate },
            onTime = { picker = Picker.FromTime }
        )
        DateTimeRow(
            heading = "To",
            date = form.toDate,
            time = form.toTime,
            onDate = { picker = Picker.ToDate },
            onTime = { picker = Picker.ToTime }
        )
        Text(
            problem ?: "Times go in 5-minute steps, like on the website.",
            style = MaterialTheme.typography.bodySmall,
            color = if (problem != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = form.reason,
            onValueChange = viewModel::setReason,
            label = { Text("Reason") },
            supportingText = { Text("Letters, numbers, spaces, full stops and commas only.") },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            minLines = 2,
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = viewModel::requestApply,
            enabled = isComplete(form, now),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("Apply")
        }
    }

    when (picker) {
        Picker.FromDate -> LeaveDatePickerDialog(
            title = "From date",
            initial = form.fromDate,
            earliest = today,
            onPick = { viewModel.setFromDate(it); picker = null },
            onDismiss = { picker = null }
        )
        Picker.ToDate -> LeaveDatePickerDialog(
            title = "To date",
            initial = form.toDate ?: form.fromDate,
            earliest = form.fromDate ?: today,
            onPick = { viewModel.setToDate(it); picker = null },
            onDismiss = { picker = null }
        )
        Picker.FromTime -> {
            val initial = form.fromTime ?: nextFiveMinutes(now)
            TimePickerDialog(
                initialHour = initial.hour,
                initialMinute = initial.minute,
                title = "From time",
                confirmLabel = "OK",
                onDismiss = { picker = null },
                onConfirm = { h, m -> viewModel.setFromTime(LocalTime(h, m)); picker = null }
            )
        }
        Picker.ToTime -> {
            val initial = form.toTime ?: form.fromTime ?: nextFiveMinutes(now)
            TimePickerDialog(
                initialHour = initial.hour,
                initialMinute = initial.minute,
                title = "To time",
                confirmLabel = "OK",
                onDismiss = { picker = null },
                onConfirm = { h, m -> viewModel.setToTime(LocalTime(h, m)); picker = null }
            )
        }
        Picker.Staff -> StaffPickerDialog(
            approvers = options.approvers,
            selectedId = form.staffId,
            onPick = { viewModel.setApprover(it.staffId); picker = null },
            onDismiss = { picker = null }
        )
        null -> Unit
    }
}

/** The first 5-minute mark after [now] — a sensible starting point for the clock. */
private fun nextFiveMinutes(now: LocalDateTime): LocalTime {
    val total = (now.hour * 60 + now.minute) / 5 * 5 + 5
    return if (total >= 24 * 60) LocalTime(23, 55) else LocalTime(total / 60, total % 60)
}

@Composable
private fun LeaveTypeField(types: List<LeaveType>, selectedId: String?, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val single = types.size == 1
    Box {
        PickerField(
            label = "Leave type",
            value = types.firstOrNull { it.id == selectedId }?.label,
            placeholder = "Choose a type",
            onClick = { open = true },
            enabled = !single,
            trailing = !single,
            modifier = Modifier.fillMaxWidth()
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            types.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.label) },
                    onClick = {
                        onPick(type.id)
                        open = false
                    }
                )
            }
        }
    }
}

@Composable
private fun DateTimeRow(heading: String, date: LocalDate?, time: LocalTime?, onDate: () -> Unit, onTime: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PickerField(
            label = "$heading date",
            value = date?.let { DateUtils.shortLabel(it) + yearSuffix(it) },
            placeholder = "Pick date",
            onClick = onDate,
            modifier = Modifier.weight(1.4f)
        )
        PickerField(
            label = "$heading time",
            value = time?.let { formatTime(it.hour, it.minute) },
            placeholder = "Pick time",
            onClick = onTime,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun yearSuffix(date: LocalDate): String =
    if (date.year != DateUtils.today().year) " ${date.year}" else ""

/** Looks like an outlined text field, but opens a picker when tapped. */
@Composable
private fun PickerField(
    label: String,
    value: String?,
    placeholder: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trailing: Boolean = false
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.extraSmall,
        color = Color.Transparent,
        border = BorderStroke(1.dp, if (value == null) scheme.outline else scheme.primary.copy(alpha = 0.6f)),
        modifier = modifier.heightIn(min = 56.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                Text(
                    value ?: placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (value == null) scheme.onSurfaceVariant else scheme.onSurface
                )
            }
            if (trailing) Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = scheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StaffPickerDialog(
    approvers: List<Approver>,
    selectedId: String?,
    onPick: (Approver) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val shown = approvers.filter { it.name.contains(query.trim(), ignoreCase = true) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(vertical = 20.dp)) {
                Text(
                    "Approving staff",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search by name") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(shown, key = { it.staffId }) { approver ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = approver.staffId == selectedId,
                                    role = Role.RadioButton,
                                    onClick = { onPick(approver) }
                                )
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            RadioButton(selected = approver.staffId == selectedId, onClick = null)
                            Spacer(Modifier.size(8.dp))
                            Text(approver.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    if (shown.isEmpty()) {
                        item {
                            Text(
                                "No staff match \"${query.trim()}\".",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                            )
                        }
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(end = 12.dp, top = 8.dp)
                ) { Text("Cancel") }
            }
        }
    }
}

/** A month view where only [earliest] onwards can be picked. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LeaveDatePickerDialog(
    title: String,
    initial: LocalDate?,
    earliest: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val earliestMillis = earliest.utcMillis()
    val years = earliest.year..earliest.plus(1, DateTimeUnit.YEAR).year
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial?.takeIf { it >= earliest }?.utcMillis(),
        initialDisplayedMonthMillis = (initial?.takeIf { it >= earliest } ?: earliest).utcMillis(),
        yearRange = years,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= earliestMillis
            override fun isSelectableYear(year: Int): Boolean = year in years
        }
    )
    // The picker reports UTC midnight of the chosen day.
    val picked = state.selectedDateMillis?.utcDate()?.takeIf { it >= earliest }
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { picked?.let(onPick) }, enabled = picked != null) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) {
        DatePicker(
            state = state,
            showModeToggle = false,
            title = { Text(title, modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp)) }
        )
    }
}

private fun LocalDate.utcMillis(): Long = atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

private fun Long.utcDate(): LocalDate = Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.UTC).date

@Composable
private fun OptionsError(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(16.dp))
        Text("Couldn't load the leave form", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRetry) { Text("Try again") }
    }
}

private fun LeaveRequest.fromLabel() = leaveDateTimeLabel(from, "")

private fun LeaveRequest.toLabel() = leaveDateTimeLabel(to, "")

@Composable
private fun ConfirmApplyDialog(request: LeaveRequest, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send leave request?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    buildAnnotatedString {
                        append("Send leave request to ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(request.approver.name) }
                        append("?")
                    }
                )
                Text("${request.fromLabel()} → ${request.toLabel()}", fontWeight = FontWeight.SemiBold)
                Text("Reason: ${request.reason}")
                Text(
                    "Type: ${request.type.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Send request") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Go back") } }
    )
}

@Composable
private fun SendingDialog() {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        icon = { CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp) },
        title = { Text("Sending leave request…") },
        confirmButton = {}
    )
}

@Composable
private fun ApplyResultDialog(state: ApplyState.Done, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            if (state.success) Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = successColor)
            else Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        },
        title = { Text(if (state.success) "Leave applied" else "Leave not applied") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(state.message)
                if (state.success) {
                    Text(
                        "Sent to ${state.request.approver.name}. It shows as Applied until they decide.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("OK") } }
    )
}
