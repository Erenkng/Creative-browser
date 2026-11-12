package com.oavi.browser.feature.browser

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oavi.browser.engine.ConfirmKind
import com.oavi.browser.engine.Credentials
import com.oavi.browser.engine.DateTimeKind
import com.oavi.browser.engine.PageRequest
import com.oavi.browser.ui.ButtonKind
import com.oavi.browser.ui.Oavi
import com.oavi.browser.ui.OaviButton
import com.oavi.browser.ui.OaviPrompt
import com.oavi.browser.ui.OaviRow
import com.oavi.browser.ui.OaviSheet
import com.oavi.browser.ui.OaviTextField
import com.oavi.browser.ui.R
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields
import java.util.Locale

/** What the context menu on a link or image can lead to. */
internal class LinkActions(
    val onOpenInNewTab: (String) -> Unit,
    val onOpenInPrivateTab: (String) -> Unit,
    val onCopy: (String) -> Unit,
    val onShare: (String) -> Unit,
)

/**
 * Shows whatever the current page is asking for: an alert, a choice, a sign-in, a file,
 * a permission. One request at a time, always from the bottom edge.
 */
@Composable
internal fun PageRequestHost(request: PageRequest?, links: LinkActions) {
    val current by rememberUpdatedState(request)

    val pickOne = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        (current as? PageRequest.FilePick)?.onResult(listOfNotNull(uri))
    }
    val pickMany = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        (current as? PageRequest.FilePick)?.onResult(uris)
    }
    val askPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        (current as? PageRequest.AndroidPermissions)?.onResult(result.isNotEmpty() && result.values.all { it })
    }

    LaunchedEffect(request) {
        when (request) {
            is PageRequest.FilePick -> {
                val types = request.mimeTypes.filter { it.contains('/') }.ifEmpty { listOf("*/*") }.toTypedArray()
                if (request.multiple) pickMany.launch(types) else pickOne.launch(types)
            }
            is PageRequest.AndroidPermissions -> askPermissions.launch(request.permissions.toTypedArray())
            else -> Unit
        }
    }

    when (request) {
        is PageRequest.Alert -> OaviPrompt(
            visible = true,
            onDismiss = request.onDone,
            title = stringResource(R.string.request_page_says),
            message = request.message,
            confirmLabel = stringResource(R.string.request_ok),
            onConfirm = request.onDone,
        )

        is PageRequest.Confirm -> ConfirmRequest(request)
        is PageRequest.TextInput -> TextRequest(request)
        is PageRequest.Login -> LoginRequest(request)
        is PageRequest.Choice -> ChoiceRequest(request)
        is PageRequest.DateTime -> DateTimeRequest(request)
        is PageRequest.ContextMenu -> ContextMenuRequest(request, links)
        is PageRequest.FilePick, is PageRequest.AndroidPermissions, null -> Unit
    }
}

@Composable
private fun ConfirmRequest(request: PageRequest.Confirm) {
    val title: String
    var message: String? = null
    var confirm = stringResource(R.string.request_allow)
    var dismiss = stringResource(R.string.request_deny)
    when (request.kind) {
        ConfirmKind.PAGE -> {
            title = stringResource(R.string.request_page_says)
            message = request.message
            confirm = stringResource(R.string.request_ok)
            dismiss = stringResource(R.string.request_cancel)
        }
        ConfirmKind.LEAVE_PAGE -> {
            title = stringResource(R.string.request_leave_title)
            message = stringResource(R.string.request_leave_body)
            confirm = stringResource(R.string.request_leave_confirm)
            dismiss = stringResource(R.string.request_stay)
        }
        ConfirmKind.RESUBMIT_FORM -> {
            title = stringResource(R.string.request_resubmit_title)
            message = stringResource(R.string.request_resubmit_body)
            confirm = stringResource(R.string.request_resubmit_confirm)
            dismiss = stringResource(R.string.request_cancel)
        }
        ConfirmKind.OPEN_IN_APP -> {
            title = stringResource(R.string.request_open_app_title)
            message = stringResource(R.string.request_open_app_body)
            confirm = stringResource(R.string.request_open_app_confirm)
            dismiss = stringResource(R.string.request_stay)
        }
        ConfirmKind.LOCATION -> title = stringResource(R.string.request_location_title, request.host)
        ConfirmKind.CAMERA -> title = stringResource(R.string.request_camera_title, request.host)
        ConfirmKind.MICROPHONE -> title = stringResource(R.string.request_microphone_title, request.host)
        ConfirmKind.CAMERA_AND_MICROPHONE -> title = stringResource(R.string.request_camera_microphone_title, request.host)
    }
    OaviPrompt(
        visible = true,
        onDismiss = { request.onResult(false) },
        title = title,
        message = message,
        confirmLabel = confirm,
        onConfirm = { request.onResult(true) },
        dismissLabel = dismiss,
    )
}

@Composable
private fun TextRequest(request: PageRequest.TextInput) {
    var text by remember(request) { mutableStateOf(request.defaultValue) }
    OaviPrompt(
        visible = true,
        onDismiss = { request.onResult(null) },
        title = stringResource(R.string.request_page_says),
        message = request.message,
        confirmLabel = stringResource(R.string.request_ok),
        onConfirm = { request.onResult(text) },
        dismissLabel = stringResource(R.string.request_cancel),
    ) {
        OaviTextField(text, { text = it }, placeholder = "", modifier = Modifier.padding(horizontal = 24.dp))
    }
}

@Composable
private fun LoginRequest(request: PageRequest.Login) {
    var username by remember(request) { mutableStateOf(request.username) }
    var password by remember(request) { mutableStateOf("") }
    OaviPrompt(
        visible = true,
        onDismiss = { request.onResult(null) },
        title = stringResource(R.string.request_login_title, request.host),
        confirmLabel = stringResource(R.string.request_sign_in),
        onConfirm = { request.onResult(Credentials(username, password)) },
        dismissLabel = stringResource(R.string.request_cancel),
        confirmEnabled = password.isNotEmpty(),
    ) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            if (!request.passwordOnly) {
                OaviTextField(
                    value = username,
                    onValueChange = { username = it },
                    placeholder = stringResource(R.string.request_username),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                )
                Spacer(Modifier.height(10.dp))
            }
            OaviTextField(
                value = password,
                onValueChange = { password = it },
                placeholder = stringResource(R.string.request_password),
                password = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            )
        }
    }
}

@Composable
private fun ChoiceRequest(request: PageRequest.Choice) {
    val colors = Oavi.colors
    var chosen by remember(request) {
        mutableStateOf(request.options.filter { it.selected }.map { it.id }.toSet())
    }
    OaviSheet(visible = true, onDismiss = { request.onResult(null) }) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 440.dp)) {
            items(request.options) { option ->
                when {
                    option.isSeparator -> Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 6.dp)
                            .height(1.dp)
                            .background(colors.faint),
                    )
                    option.isHeader -> Text(
                        text = option.label,
                        style = Oavi.type.caption,
                        color = colors.muted,
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 4.dp),
                    )
                    else -> {
                        val selected = option.id in chosen
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 54.dp)
                                .clickable(enabled = option.enabled, role = Role.RadioButton) {
                                    if (request.multiple) {
                                        chosen = if (selected) chosen - option.id else chosen + option.id
                                    } else {
                                        request.onResult(listOf(option.id))
                                    }
                                }
                                .padding(horizontal = 24.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = option.label,
                                style = Oavi.type.body,
                                color = if (option.enabled) colors.onSurface else colors.muted,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (selected) {
                                Spacer(Modifier.width(12.dp))
                                Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                }
            }
        }
        if (request.multiple) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
                Spacer(Modifier.weight(1f))
                OaviButton(stringResource(R.string.request_done), onClick = { request.onResult(chosen.toList()) })
            }
        }
    }
}

@Composable
private fun ContextMenuRequest(request: PageRequest.ContextMenu, links: LinkActions) {
    val link = request.linkUrl
    val image = request.imageUrl
    fun act(action: () -> Unit) {
        request.onDone()
        action()
    }
    OaviSheet(visible = true, onDismiss = request.onDone) {
        Text(
            text = link ?: image.orEmpty(),
            style = Oavi.type.caption,
            color = Oavi.colors.muted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
        )
        if (link != null) {
            OaviRow(stringResource(R.string.context_open_new_tab), icon = R.drawable.ic_open_new, onClick = { act { links.onOpenInNewTab(link) } })
            OaviRow(stringResource(R.string.context_open_private_tab), icon = R.drawable.ic_private_mode, onClick = { act { links.onOpenInPrivateTab(link) } })
            OaviRow(stringResource(R.string.context_copy_link), icon = R.drawable.ic_copy, onClick = { act { links.onCopy(link) } })
            OaviRow(stringResource(R.string.context_share_link), icon = R.drawable.ic_share, onClick = { act { links.onShare(link) } })
        }
        if (image != null) {
            OaviRow(stringResource(R.string.context_open_image), icon = R.drawable.ic_image, onClick = { act { links.onOpenInNewTab(image) } })
        }
    }
}

/** Date and time fields use the system pickers people already know. */
@Composable
private fun DateTimeRequest(request: PageRequest.DateTime) {
    val context = LocalContext.current
    DisposableEffect(request) {
        var answered = false
        val startDate = parseDate(request.value) ?: LocalDate.now()
        val startTime = parseTime(request.value) ?: LocalTime.now().withSecond(0).withNano(0)

        fun answer(value: String?) {
            if (!answered) {
                answered = true
                request.onResult(value)
            }
        }

        fun timePicker(date: LocalDate?): TimePickerDialog = TimePickerDialog(
            context,
            { _, hour, minute ->
                val time = LocalTime.of(hour, minute)
                answer(
                    if (date == null) {
                        time.format(TIME_FORMAT)
                    } else {
                        LocalDateTime.of(date, time).format(DATE_TIME_FORMAT)
                    },
                )
            },
            startTime.hour,
            startTime.minute,
            true,
        ).apply { setOnCancelListener { answer(null) } }

        val dialog = if (request.kind == DateTimeKind.TIME) {
            timePicker(null)
        } else {
            DatePickerDialog(
                context,
                { _, year, month, day ->
                    val date = LocalDate.of(year, month + 1, day)
                    when (request.kind) {
                        DateTimeKind.DATE_TIME -> timePicker(date).show()
                        DateTimeKind.MONTH -> answer(String.format(Locale.ROOT, "%04d-%02d", date.year, date.monthValue))
                        DateTimeKind.WEEK -> answer(
                            String.format(
                                Locale.ROOT,
                                "%04d-W%02d",
                                date.get(IsoFields.WEEK_BASED_YEAR),
                                date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR),
                            ),
                        )
                        else -> answer(date.format(DateTimeFormatter.ISO_LOCAL_DATE))
                    }
                },
                startDate.year,
                startDate.monthValue - 1,
                startDate.dayOfMonth,
            ).apply { setOnCancelListener { answer(null) } }
        }
        dialog.show()
        onDispose {
            answered = true
            dialog.dismiss()
        }
    }
}

private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
private val DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm", Locale.ROOT)

private fun parseDate(value: String): LocalDate? = try {
    when {
        value.length >= 10 && value[4] == '-' && value[7] == '-' -> LocalDate.parse(value.substring(0, 10))
        value.length == 7 && value[4] == '-' -> LocalDate.parse("$value-01")
        else -> null
    }
} catch (_: Exception) {
    null
}

private fun parseTime(value: String): LocalTime? = try {
    val text = value.substringAfter('T', value)
    if (text.length >= 5 && text[2] == ':') LocalTime.parse(text.substring(0, 5)) else null
} catch (_: Exception) {
    null
}
