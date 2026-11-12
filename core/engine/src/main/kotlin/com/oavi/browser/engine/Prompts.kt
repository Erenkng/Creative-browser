package com.oavi.browser.engine

import android.content.Context
import com.oavi.browser.model.UrlInput
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.PromptDelegate
import org.mozilla.geckoview.GeckoSession.PromptDelegate.PromptResponse

/**
 * Turns the engine's prompts (alerts, drop-downs, file pickers, sign-in boxes …) into
 * [PageRequest]s for the interface to show, and passes the answers back.
 */
internal class Prompts(
    private val context: Context,
    private val tabId: String,
    private val post: (PageRequest) -> Unit,
    private val finish: (PageRequest) -> Unit,
) : PromptDelegate {

    /** Posts the request built by [build] and completes the engine's result when it is answered. */
    private fun ask(build: (complete: (PromptResponse) -> Unit) -> PageRequest): GeckoResult<PromptResponse> {
        val result = GeckoResult<PromptResponse>()
        lateinit var request: PageRequest
        request = build { response ->
            finish(request)
            result.complete(response)
        }
        post(request)
        return result
    }

    override fun onAlertPrompt(
        session: GeckoSession,
        prompt: PromptDelegate.AlertPrompt,
    ): GeckoResult<PromptResponse>? = ask { complete ->
        PageRequest.Alert(tabId, prompt.message.orEmpty()) { complete(prompt.dismiss()) }
    }

    override fun onButtonPrompt(
        session: GeckoSession,
        prompt: PromptDelegate.ButtonPrompt,
    ): GeckoResult<PromptResponse>? = ask { complete ->
        PageRequest.Confirm(tabId, ConfirmKind.PAGE, prompt.message.orEmpty(), "") { agreed ->
            val choice = if (agreed) PromptDelegate.ButtonPrompt.Type.POSITIVE else PromptDelegate.ButtonPrompt.Type.NEGATIVE
            complete(prompt.confirm(choice))
        }
    }

    override fun onBeforeUnloadPrompt(
        session: GeckoSession,
        prompt: PromptDelegate.BeforeUnloadPrompt,
    ): GeckoResult<PromptResponse>? = ask { complete ->
        PageRequest.Confirm(tabId, ConfirmKind.LEAVE_PAGE, "", "") { agreed ->
            complete(prompt.confirm(if (agreed) AllowOrDeny.ALLOW else AllowOrDeny.DENY))
        }
    }

    override fun onRepostConfirmPrompt(
        session: GeckoSession,
        prompt: PromptDelegate.RepostConfirmPrompt,
    ): GeckoResult<PromptResponse>? = ask { complete ->
        PageRequest.Confirm(tabId, ConfirmKind.RESUBMIT_FORM, "", "") { agreed ->
            complete(prompt.confirm(if (agreed) AllowOrDeny.ALLOW else AllowOrDeny.DENY))
        }
    }

    override fun onTextPrompt(
        session: GeckoSession,
        prompt: PromptDelegate.TextPrompt,
    ): GeckoResult<PromptResponse>? = ask { complete ->
        PageRequest.TextInput(tabId, prompt.message.orEmpty(), prompt.defaultValue.orEmpty()) { value ->
            complete(if (value == null) prompt.dismiss() else prompt.confirm(value))
        }
    }

    override fun onAuthPrompt(
        session: GeckoSession,
        prompt: PromptDelegate.AuthPrompt,
    ): GeckoResult<PromptResponse>? {
        val options = prompt.authOptions
        val passwordOnly = options.flags and PromptDelegate.AuthPrompt.AuthOptions.Flags.ONLY_PASSWORD != 0
        val host = options.uri?.let { UrlInput.displayHost(it) }.orEmpty()
        return ask { complete ->
            PageRequest.Login(tabId, host, passwordOnly, options.username.orEmpty()) { credentials ->
                complete(
                    when {
                        credentials == null -> prompt.dismiss()
                        passwordOnly -> prompt.confirm(credentials.password)
                        else -> prompt.confirm(credentials.username, credentials.password)
                    },
                )
            }
        }
    }

    override fun onChoicePrompt(
        session: GeckoSession,
        prompt: PromptDelegate.ChoicePrompt,
    ): GeckoResult<PromptResponse>? {
        val multiple = prompt.type == PromptDelegate.ChoicePrompt.Type.MULTIPLE
        val options = ArrayList<ChoiceOption>()
        flatten(prompt.choices, options)
        return ask { complete ->
            PageRequest.Choice(tabId, multiple, options) { selected ->
                complete(
                    when {
                        selected == null -> prompt.dismiss()
                        multiple -> prompt.confirm(selected.toTypedArray())
                        selected.isEmpty() -> prompt.dismiss()
                        else -> prompt.confirm(selected.first())
                    },
                )
            }
        }
    }

    private fun flatten(choices: Array<PromptDelegate.ChoicePrompt.Choice>?, into: MutableList<ChoiceOption>) {
        for (choice in choices ?: return) {
            val children = choice.items
            when {
                choice.separator -> into += ChoiceOption(choice.id, "", isSeparator = true, enabled = false)
                children != null -> {
                    into += ChoiceOption(choice.id, choice.label, isHeader = true, enabled = false)
                    flatten(children, into)
                }
                else -> into += ChoiceOption(choice.id, choice.label, selected = choice.selected, enabled = !choice.disabled)
            }
        }
    }

    override fun onFilePrompt(
        session: GeckoSession,
        prompt: PromptDelegate.FilePrompt,
    ): GeckoResult<PromptResponse>? {
        val multiple = prompt.type == PromptDelegate.FilePrompt.Type.MULTIPLE
        val mimeTypes = prompt.mimeTypes.orEmpty().filterNotNull().filter { it.isNotBlank() }
        return ask { complete ->
            PageRequest.FilePick(tabId, multiple, mimeTypes) { uris ->
                if (uris.isEmpty()) {
                    complete(prompt.dismiss())
                } else {
                    Uploads.prepare(context, uris) { files ->
                        complete(
                            when {
                                files.isEmpty() -> prompt.dismiss()
                                multiple -> prompt.confirm(context, files.toTypedArray())
                                else -> prompt.confirm(context, files.first())
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onDateTimePrompt(
        session: GeckoSession,
        prompt: PromptDelegate.DateTimePrompt,
    ): GeckoResult<PromptResponse>? {
        val kind = when (prompt.type) {
            PromptDelegate.DateTimePrompt.Type.TIME -> DateTimeKind.TIME
            PromptDelegate.DateTimePrompt.Type.DATETIME_LOCAL -> DateTimeKind.DATE_TIME
            PromptDelegate.DateTimePrompt.Type.MONTH -> DateTimeKind.MONTH
            PromptDelegate.DateTimePrompt.Type.WEEK -> DateTimeKind.WEEK
            else -> DateTimeKind.DATE
        }
        return ask { complete ->
            PageRequest.DateTime(tabId, kind, prompt.defaultValue.orEmpty()) { value ->
                complete(if (value == null) prompt.dismiss() else prompt.confirm(value))
            }
        }
    }

    /** Pop-up windows a page opens on its own are refused; links the user taps still open. */
    override fun onPopupPrompt(
        session: GeckoSession,
        prompt: PromptDelegate.PopupPrompt,
    ): GeckoResult<PromptResponse>? = GeckoResult.fromValue(prompt.confirm(AllowOrDeny.DENY))
}
