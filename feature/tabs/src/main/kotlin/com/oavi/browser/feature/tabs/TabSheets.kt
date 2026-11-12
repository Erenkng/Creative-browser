package com.oavi.browser.feature.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oavi.browser.engine.BrowserState
import com.oavi.browser.engine.TabState
import com.oavi.browser.engine.TabsController
import com.oavi.browser.model.GroupColor
import com.oavi.browser.model.TabRules
import com.oavi.browser.ui.Oavi
import com.oavi.browser.ui.OaviPrompt
import com.oavi.browser.ui.OaviRow
import com.oavi.browser.ui.OaviSheet
import com.oavi.browser.ui.OaviTextField
import com.oavi.browser.ui.R
import com.oavi.browser.ui.SectionTitle

/** What can be done with a tab after pressing and holding it. */
@Composable
internal fun TabActionsSheet(
    tab: TabState?,
    state: BrowserState,
    controller: TabsController,
    onNewGroup: (tabId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    // Keep showing the last tab while the sheet slides away.
    var last by remember { mutableStateOf(tab) }
    if (tab != null) last = tab
    val shown = last

    OaviSheet(visible = tab != null, onDismiss = onDismiss) {
        if (shown == null) return@OaviSheet
        fun act(action: () -> Unit) {
            onDismiss()
            action()
        }
        Text(
            text = shown.tab.label.ifBlank { stringResource(R.string.tabs_untitled) },
            style = Oavi.type.headline,
            color = Oavi.colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
        )
        if (shown.tab.archived) {
            OaviRow(stringResource(R.string.tab_restore), icon = R.drawable.ic_undo, onClick = { act { controller.setArchived(shown.id, false) } })
        } else if (!shown.isPrivate) {
            SectionTitle(stringResource(R.string.tab_add_to_group))
            for (group in state.groups) {
                val inGroup = shown.tab.groupId == group.id
                OaviRow(
                    title = group.name,
                    onClick = { act { controller.moveToGroup(shown.id, if (inGroup) null else group.id) } },
                    subtitle = if (inGroup) stringResource(R.string.tab_remove_from_group) else null,
                    trailing = {
                        Box(
                            Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(group.color.toColor()),
                        )
                    },
                )
            }
            OaviRow(stringResource(R.string.tab_new_group), icon = R.drawable.ic_group, onClick = { onNewGroup(shown.id) })
            Spacer(Modifier.height(6.dp))
            OaviRow(stringResource(R.string.tab_archive), icon = R.drawable.ic_archive, onClick = { act { controller.setArchived(shown.id, true) } })
        }
        if (!shown.tab.archived) {
            OaviRow(
                title = stringResource(R.string.tab_close_others),
                icon = R.drawable.ic_tab,
                onClick = {
                    act {
                        controller.closeAll(state.visibleTabs(shown.isPrivate).filter { it.id != shown.id }.map { it.id })
                    }
                },
            )
        }
        OaviRow(
            title = stringResource(R.string.tab_close),
            icon = R.drawable.ic_close,
            iconTint = Oavi.colors.danger,
            titleColor = Oavi.colors.danger,
            onClick = { act { controller.close(shown.id) } },
        )
    }
}

/** Names a new group and gives it a colour. The name is suggested from the tab's site. */
@Composable
internal fun NewGroupPrompt(
    tab: TabState?,
    onCreate: (name: String, color: GroupColor) -> Unit,
    onDismiss: () -> Unit,
) {
    val fallback = stringResource(R.string.group_default_name)
    var name by remember(tab?.id) {
        mutableStateOf(tab?.let { TabRules.suggestGroupName(listOf(it.tab)) }.orEmpty().ifBlank { fallback })
    }
    var color by remember(tab?.id) { mutableStateOf(GroupColor.AQUA) }

    OaviPrompt(
        visible = tab != null,
        onDismiss = onDismiss,
        title = stringResource(R.string.group_new_title),
        confirmLabel = stringResource(R.string.group_create),
        onConfirm = { onCreate(name, color) },
        dismissLabel = stringResource(R.string.request_cancel),
        confirmEnabled = name.isNotBlank(),
    ) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            OaviTextField(name, { name = it.take(40) }, placeholder = stringResource(R.string.group_name_hint))
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                for (option in GroupColor.entries) {
                    val chosen = option == color
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .border(
                                width = if (chosen) 2.5.dp else 0.dp,
                                color = if (chosen) Oavi.colors.onSurface else androidx.compose.ui.graphics.Color.Transparent,
                                shape = CircleShape,
                            )
                            .padding(5.dp)
                            .clip(CircleShape)
                            .background(option.toColor())
                            .clickable(role = Role.RadioButton) { color = option },
                    )
                }
            }
        }
    }
}
