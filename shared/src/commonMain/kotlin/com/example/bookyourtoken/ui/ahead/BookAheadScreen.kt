package com.example.bookyourtoken.ui.ahead

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bookyourtoken.data.DateUtils
import com.example.bookyourtoken.data.HostelClient
import com.example.bookyourtoken.data.models.TokenItem
import com.example.bookyourtoken.ui.booking.BookingLine
import com.example.bookyourtoken.ui.booking.BookingProgressDialog
import com.example.bookyourtoken.ui.common.AppIcons
import com.example.bookyourtoken.ui.common.BrandHeader
import com.example.bookyourtoken.ui.common.FoodBadge
import com.example.bookyourtoken.ui.common.LocalPlatformActions
import com.example.bookyourtoken.ui.common.formatRupees
import com.example.bookyourtoken.ui.common.prettyName
import com.example.bookyourtoken.ui.common.priceValue
import com.example.bookyourtoken.ui.theme.successColor
import com.example.bookyourtoken.ui.tokens.BookedSummaryCard
import com.example.bookyourtoken.ui.tokens.ErrorContent
import com.example.bookyourtoken.ui.tokens.LoadingContent
import com.example.bookyourtoken.ui.tokens.OpenPortalButton
import com.example.bookyourtoken.ui.tokens.Selection
import com.example.bookyourtoken.ui.tokens.TokenItemRow
import com.example.bookyourtoken.ui.tokens.estimatedTotal
import kotlinx.coroutines.delay
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Book any upcoming date. [onSelectionCount] reports how many items are picked (so leaving can ask
 * first); [onBackWithSelections] is called instead of going back while some are picked.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun BookAheadScreen(
    onOpenMyTokens: () -> Unit,
    onUpcomingCount: (Int) -> Unit,
    onSelectionCount: (Int) -> Unit,
    onBackWithSelections: () -> Unit,
    onBooked: () -> Unit,
    viewModel: BookAheadViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val bookingState by viewModel.bookingState.collectAsStateWithLifecycle()
    val showConfirm by viewModel.showConfirmDialog.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val bookedRuns by viewModel.bookedRuns.collectAsStateWithLifecycle()
    val platform = LocalPlatformActions.current
    val openPortal = { platform.openUrl(HostelClient.BOOKING_PAGE_URL) }
    val snackbar = remember { SnackbarHostState() }
    var showCalendar by rememberSaveable { mutableStateOf(false) }

    // The hostel's clock, ticking each minute, so "Likely closed" appears on time while the screen is open.
    val now by produceState(DateUtils.now()) {
        while (true) {
            delay(60_000)
            value = DateUtils.now()
        }
    }
    val today = now.date

    val loaded = uiState as? BookAheadUiState.Loaded
    val pickedCount = loaded?.selections?.itemCount ?: 0

    LaunchedEffect(loaded?.upcomingCount) { loaded?.upcomingCount?.let(onUpcomingCount) }
    LaunchedEffect(pickedCount) { onSelectionCount(pickedCount) }
    DisposableEffect(Unit) { onDispose { onSelectionCount(0) } }
    LaunchedEffect(bookedRuns) { if (bookedRuns > 0) onBooked() }
    LaunchedEffect(notice) {
        notice?.let {
            snackbar.showSnackbar(it)
            viewModel.noticeShown()
        }
    }

    BackHandler(enabled = pickedCount > 0, onBack = onBackWithSelections)

    val current = loaded?.current
    Scaffold(
        topBar = {
            BrandHeader(
                eyebrow = "Plan your week",
                title = "Book ahead",
                subtitle = current?.let { cutoffHint(it.date, today) },
                actions = {
                    if (loaded != null && loaded.dates.isNotEmpty()) {
                        IconButton(onClick = { showCalendar = true }) {
                            Icon(AppIcons.CalendarMonth, contentDescription = "Pick a date from a calendar")
                        }
                    }
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh upcoming tokens")
                    }
                }
            )
        },
        bottomBar = {
            if (loaded != null && loaded.dates.isNotEmpty()) {
                AheadBookingBar(state = loaded, onClear = viewModel::clearAll, onBook = viewModel::requestConfirm)
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val state = uiState) {
                is BookAheadUiState.Loading -> LoadingContent(state.stage)
                is BookAheadUiState.Error -> ErrorContent(state.message, onRetry = viewModel::refresh, onOpenPortal = openPortal)
                is BookAheadUiState.Loaded -> PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (state.dates.isEmpty()) {
                        NoDatesContent(openPortal)
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            DateChipRow(state, today, now, onSelect = viewModel::selectDate)
                            DateContent(
                                state = state,
                                today = today,
                                now = now,
                                onToggle = viewModel::toggle,
                                onMeal = viewModel::setMeal,
                                onQuantity = viewModel::setQuantity,
                                onOpenMyTokens = onOpenMyTokens
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCalendar && loaded != null && loaded.dates.isNotEmpty()) {
        CalendarDialog(
            dates = loaded.dates,
            selected = loaded.current,
            onPick = {
                viewModel.selectDate(it)
                showCalendar = false
            },
            onDismiss = { showCalendar = false }
        )
    }

    if (showConfirm && loaded != null) {
        ConfirmAheadDialog(state = loaded, today = today, now = now, onConfirm = viewModel::confirmBooking, onDismiss = viewModel::dismissConfirm)
    }

    bookingState?.let { BookingProgressDialog(state = it, onDismiss = viewModel::dismissBooking) }
}

private fun itemsLabel(count: Int) = if (count == 1) "1 item" else "$count items"

@Composable
private fun NoDatesContent(onOpenPortal: () -> Unit) {
    // A list, so pull-to-refresh still works on an empty screen.
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Spacer(Modifier.height(48.dp))
            FoodBadge("", size = 72.dp)
            Spacer(Modifier.height(16.dp))
            Text("Nothing to book ahead", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "No tokens are offered on any upcoming date. Pull down to check again later.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            OpenPortalButton(onOpenPortal)
        }
    }
}

@Composable
private fun DateChipRow(
    state: BookAheadUiState.Loaded,
    today: LocalDate,
    now: LocalDateTime,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .height(IntrinsicSize.Min)
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        state.dates.forEach { day ->
            DateChip(
                day = day,
                today = today,
                now = now,
                selected = day.raw == state.selectedDate,
                pickedCount = state.selections[day.raw]?.size ?: 0,
                hasBooking = state.bookedOn(day.raw).isNotEmpty(),
                onClick = { onSelect(day.raw) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DateChip(
    day: UpcomingDate,
    today: LocalDate,
    now: LocalDateTime,
    selected: Boolean,
    pickedCount: Int,
    hasBooking: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val isToday = day.date == today
    val isTomorrow = day.date == today.plus(1, DateTimeUnit.DAY)
    // "Today" or "Tomorrow" on top, with the weekday moved down beside the month.
    val relative = if (isToday) "Today" else if (isTomorrow) "Tomorrow" else null
    val closed = isLikelyClosed(day.date, now)
    val content = if (selected) scheme.onPrimary else scheme.onSurface
    val shape = RoundedCornerShape(20.dp)

    // Picking a date in the calendar scrolls its chip into view.
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(selected) { if (selected) requester.bringIntoView() }

    val description = buildString {
        relative?.let { append(it).append(", ") }
        append(DateUtils.spokenLabel(day.date))
        append(", ").append(itemsLabel(day.items.size))
        if (hasBooking) append(", you have bookings")
        if (pickedCount > 0) append(", $pickedCount selected")
        if (closed) append(", booking likely closed")
    }

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .bringIntoViewRequester(requester)
    ) {
        Surface(
            shape = shape,
            color = if (selected) scheme.primary else scheme.surfaceContainerLow,
            contentColor = content,
            border = if (selected) null else BorderStroke(1.dp, scheme.outlineVariant),
            modifier = Modifier
                // Room for the selected-count badge in the corner.
                .padding(top = 6.dp, end = 6.dp)
                .fillMaxHeight()
                .widthIn(min = 72.dp)
                .clip(shape)
                .selectable(selected = selected, role = Role.Tab, onClick = onClick)
                .semantics { contentDescription = description }
        ) {
            Column(
                modifier = Modifier
                    .alpha(if (closed && !selected) 0.55f else 1f)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .clearAndSetSemantics {},
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    relative ?: DateUtils.weekdayShort(day.date),
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    day.date.day.toString().padStart(2, '0'),
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    (if (relative != null) DateUtils.weekdayShort(day.date) + " · " else "") + DateUtils.monthShort(day.date),
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    itemsLabel(day.items.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = content.copy(alpha = 0.8f)
                )
                if (closed) {
                    Text(
                        "Likely closed",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) scheme.onPrimary else scheme.error
                    )
                }
            }
        }
        if (hasBooking) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 9.dp, top = 15.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (selected) scheme.onPrimary else successColor)
                    .clearAndSetSemantics {}
            )
        }
        if (pickedCount > 0) {
            Badge(
                containerColor = scheme.tertiary,
                contentColor = scheme.onTertiary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clearAndSetSemantics {}
            ) { Text("$pickedCount") }
        }
    }
}

@Composable
private fun DateContent(
    state: BookAheadUiState.Loaded,
    today: LocalDate,
    now: LocalDateTime,
    onToggle: (TokenItem) -> Unit,
    onMeal: (TokenItem, String) -> Unit,
    onQuantity: (TokenItem, Int) -> Unit,
    onOpenMyTokens: () -> Unit
) {
    val day = state.current ?: return
    val label = DateUtils.shortLabel(day.date)
    val picks = state.selections[day.raw].orEmpty()
    val booked = state.bookedOn(day.raw)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isLikelyClosed(day.date, now)) {
            item(key = "closed-${day.raw}") { LikelyClosedCard() }
        }
        if (booked.isNotEmpty()) {
            item(key = "booked-${day.raw}") {
                BookedSummaryCard("Already booked on $label", booked, onManage = onOpenMyTokens)
            }
        }
        item(key = "header-${day.raw}") {
            Text(
                when (day.date) {
                    today -> "Available today, $label"
                    today.plus(1, DateTimeUnit.DAY) -> "Available tomorrow, $label"
                    else -> "Available on $label"
                } + " · ${day.items.size}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(start = 4.dp, top = 4.dp)
                    .semantics { heading() }
            )
        }
        // Keyed by date too, so switching dates doesn't carry a card's expanded state across.
        items(day.items, key = { "${day.raw}-${it.ptokenId}" }) { item ->
            TokenItemRow(
                item = item,
                selection = picks[item.ptokenId],
                booked = state.bookedFor(item, day.raw),
                onToggle = { onToggle(item) },
                onMeal = { onMeal(item, it) },
                onQuantity = { onQuantity(item, it) },
                likelyClosedMeals = item.meals.filter { isMealLikelyClosed(day.date, it, now) }.toSet()
            )
        }
    }
}

@Composable
private fun LikelyClosedCard() {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        )
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(AppIcons.Schedule, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Booking has likely closed", style = MaterialTheme.typography.titleSmall)
                Text(
                    "The portal usually stops at 5:30 PM the day before. You can still try — it will say if it's too late.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun AheadBookingBar(state: BookAheadUiState.Loaded, onClear: () -> Unit, onBook: () -> Unit) {
    val items = state.selections.itemCount
    val dates = state.selections.dateCount
    val total = estimatedTotal(bookingLines(state.dates, state.selections).map { it.item to Selection(it.meal, it.quantity) })
    val summary = "${itemsLabel(items)}, " + if (dates == 1) "1 date" else "$dates dates"
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(48.dp)) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
                ) {
                    if (items == 0) {
                        Text(
                            "Tap items on any date to select them",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text("$summary selected", style = MaterialTheme.typography.titleSmall)
                        total?.let {
                            Text(
                                "Estimated ${formatRupees(it)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (items > 0) {
                    TextButton(onClick = onClear) { Text("Clear all") }
                }
            }
            Button(
                onClick = onBook,
                enabled = items > 0,
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 8.dp)
            ) {
                Text(
                    if (items == 0) "Book selected" else "Book selected ($summary)",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

/** Every pick, grouped under its date in date order — nothing is sent until "Book now". */
@Composable
private fun ConfirmAheadDialog(
    state: BookAheadUiState.Loaded,
    today: LocalDate,
    now: LocalDateTime,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val lines = bookingLines(state.dates, state.selections)
    val groups = lines.groupBy { it.date }
    val total = estimatedTotal(lines.map { it.item to Selection(it.meal, it.quantity) })
    val anyClosed = state.dates.any { day ->
        val dayLines = groups[day.raw] ?: return@any false
        isLikelyClosed(day.date, now) || dayLines.any { isMealLikelyClosed(day.date, it.meal, now) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(AppIcons.CalendarMonth, contentDescription = null) },
        title = { Text("Confirm booking") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                groups.forEach { (raw, dayLines) ->
                    Text(
                        dateHeading(raw, today),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .semantics { heading() }
                    )
                    dayLines.forEach { ConfirmLine(it) }
                }
                if (total != null) {
                    HorizontalDivider()
                    Row {
                        Text("Estimated total", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(formatRupees(total), style = MaterialTheme.typography.titleSmall)
                    }
                }
                if (anyClosed) {
                    Text(
                        "Booking has likely closed for some of these dates. The portal will say if it's too late.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Text(
                    "This books real tokens on the hostel portal, one at a time in date order.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { Button(onClick = onConfirm) { Text("Book now") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ConfirmLine(line: BookingLine) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(prettyName(line.item.name), style = MaterialTheme.typography.bodyLarge)
            Text(
                "${line.meal} × ${line.quantity}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        priceValue(line.item.price)?.let {
            Text(formatRupees(it * line.quantity), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** A month view where only dates with something on offer can be picked. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarDialog(
    dates: List<UpcomingDate>,
    selected: UpcomingDate?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val byMillis = remember(dates) { dates.associateBy { it.date.utcMillis() } }
    val years = dates.first().date.year..dates.last().date.year
    val state = rememberDatePickerState(
        initialSelectedDateMillis = selected?.date?.utcMillis(),
        yearRange = years,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis in byMillis
            override fun isSelectableYear(year: Int): Boolean = year in years
        }
    )
    // The picker reports UTC midnight; map it back to the date's own dropdown string.
    val picked = state.selectedDateMillis?.let { byMillis[it] ?: byMillis[it.utcDate().utcMillis()] }
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { picked?.let { onPick(it.raw) } }, enabled = picked != null) { Text("Show") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) {
        DatePicker(
            state = state,
            showModeToggle = false,
            title = {
                Text(
                    "Dates with tokens on offer",
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp)
                )
            }
        )
    }
}

private fun LocalDate.utcMillis(): Long = atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

private fun Long.utcDate(): LocalDate = Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.UTC).date
