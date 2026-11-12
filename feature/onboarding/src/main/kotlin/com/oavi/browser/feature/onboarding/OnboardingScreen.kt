package com.oavi.browser.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.oavi.browser.model.SearchEngine
import com.oavi.browser.ui.ButtonKind
import com.oavi.browser.ui.LetterTile
import com.oavi.browser.ui.Oavi
import com.oavi.browser.ui.OaviButton
import com.oavi.browser.ui.OaviMotion
import com.oavi.browser.ui.R
import com.oavi.browser.ui.pressScale
import com.oavi.browser.ui.rememberHaptics

/**
 * First launch, in two short steps: choose a search engine (nothing is chosen for you),
 * then decide whether Oavi should open links from other apps.
 *
 * @param onFinish called with the chosen engine once both steps are done.
 */
@Composable
fun OnboardingScreen(
    isDefaultBrowser: Boolean,
    onRequestDefaultBrowser: () -> Unit,
    onFinish: (SearchEngine) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Oavi.colors
    var step by rememberSaveable { mutableIntStateOf(0) }
    var chosenId by rememberSaveable { mutableStateOf<String?>(null) }
    val chosen = SearchEngine.fromId(chosenId)

    BackHandler(enabled = step > 0) { step = 0 }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val direction = if (targetState > initialState) 1 else -1
                (slideInHorizontally(OaviMotion.spatial()) { it / 3 * direction } + fadeIn(OaviMotion.fade())) togetherWith
                    (slideOutHorizontally(OaviMotion.spatial()) { -it / 3 * direction } + fadeOut(OaviMotion.fade()))
            },
            label = "onboarding",
        ) { current ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState(), reverseScrolling = true)
                    .heightIn(min = this@BoxWithConstraints.maxHeight)
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.Bottom,
            ) {
                if (current == 0) {
                    ChooseSearchEngine(
                        chosen = chosen,
                        onChoose = { chosenId = it.id },
                        onContinue = { if (isDefaultBrowser) chosen?.let(onFinish) else step = 1 },
                    )
                } else {
                    DefaultBrowserStep(
                        onAccept = {
                            onRequestDefaultBrowser()
                            chosen?.let(onFinish)
                        },
                        onLater = { chosen?.let(onFinish) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChooseSearchEngine(
    chosen: SearchEngine?,
    onChoose: (SearchEngine) -> Unit,
    onContinue: () -> Unit,
) {
    val colors = Oavi.colors
    val haptics = rememberHaptics()
    Text(stringResource(R.string.onboarding_title), style = Oavi.type.display, color = colors.onSurface)
    Spacer(Modifier.height(12.dp))
    Text(stringResource(R.string.onboarding_body), style = Oavi.type.body, color = colors.muted, modifier = Modifier.widthIn(max = 420.dp))
    Spacer(Modifier.height(34.dp))
    Text(stringResource(R.string.onboarding_search_title), style = Oavi.type.headline, color = colors.onSurface)
    Spacer(Modifier.height(4.dp))
    Text(stringResource(R.string.onboarding_search_body), style = Oavi.type.caption, color = colors.muted)
    Spacer(Modifier.height(14.dp))
    for (engine in SearchEngine.entries) {
        EngineOption(engine, selected = engine == chosen) {
            haptics.tick()
            onChoose(engine)
        }
        Spacer(Modifier.height(8.dp))
    }
    Spacer(Modifier.height(16.dp))
    OaviButton(
        text = stringResource(R.string.onboarding_continue),
        onClick = onContinue,
        enabled = chosen != null,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun EngineOption(engine: SearchEngine, selected: Boolean, onClick: () -> Unit) {
    val colors = Oavi.colors
    val shape = RoundedCornerShape(22.dp)
    val container by animateColorAsState(if (selected) colors.accentSoft else colors.surface, OaviMotion.fade(), label = "engine")
    val outline by animateColorAsState(if (selected) colors.accent else colors.faint, OaviMotion.fade(), label = "engineOutline")
    val source = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(source, pressed = 0.97f)
            .clip(shape)
            .background(container)
            .border(if (selected) 2.dp else 1.dp, outline, shape)
            .clickable(interactionSource = source, indication = LocalIndication.current, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LetterTile(engine.displayName)
        Spacer(Modifier.width(14.dp))
        Text(engine.displayName, style = Oavi.type.headline, color = colors.onSurface, modifier = Modifier.weight(1f))
        if (selected) {
            Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = colors.accent, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun DefaultBrowserStep(onAccept: () -> Unit, onLater: () -> Unit) {
    val colors = Oavi.colors
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(colors.accentSoft),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_link), contentDescription = null, tint = colors.accent, modifier = Modifier.size(28.dp))
    }
    Spacer(Modifier.height(22.dp))
    Text(stringResource(R.string.onboarding_default_title), style = Oavi.type.display, color = colors.onSurface)
    Spacer(Modifier.height(12.dp))
    Text(stringResource(R.string.onboarding_default_body), style = Oavi.type.body, color = colors.muted, modifier = Modifier.widthIn(max = 420.dp))
    Spacer(Modifier.height(30.dp))
    OaviButton(stringResource(R.string.onboarding_default_yes), onClick = onAccept, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(6.dp))
    OaviButton(stringResource(R.string.onboarding_default_later), onClick = onLater, kind = ButtonKind.QUIET, modifier = Modifier.fillMaxWidth())
}
