package com.oavi.browser.model

import java.util.Base64

enum class LoadErrorKind {
    OFFLINE,
    NOT_FOUND,
    TIMEOUT,
    SECURITY,
    CONNECTION,

    /** A device on the user's network, which Oavi has not been allowed to reach. */
    LOCAL_NETWORK,
    UNKNOWN,
}

data class ErrorPageText(
    val title: String,
    val message: String,
    val retry: String,
)

/** The page shown when a site cannot be loaded. Self-contained: no scripts, no network. */
object ErrorPages {
    fun html(text: ErrorPageText, failedUrl: String): String {
        val retryHref = escape(failedUrl)
        return """
            <!DOCTYPE html>
            <html><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <meta name="color-scheme" content="light dark">
            <title>${escape(text.title)}</title>
            <style>
              :root { --bg:#F6F3EE; --fg:#1B1A1F; --muted:#6B6872; --accent:#0E9F94; --on:#FFFFFF; }
              @media (prefers-color-scheme: dark) {
                :root { --bg:#121114; --fg:#EFECF4; --muted:#A09CAB; --accent:#4FD8CB; --on:#04201D; }
              }
              html,body { height:100%; margin:0; }
              body { background:var(--bg); color:var(--fg); font:16px/1.5 system-ui,sans-serif;
                     display:flex; align-items:center; justify-content:center; }
              main { max-width:26rem; padding:2rem 1.75rem 8rem; }
              h1 { font-size:1.6rem; line-height:1.2; margin:0 0 .75rem; letter-spacing:-.01em; text-wrap:balance; }
              p { margin:0 0 1.75rem; color:var(--muted); text-wrap:pretty; }
              .host { font-size:.85rem; color:var(--muted); margin:0 0 1rem; word-break:break-all; }
              a { display:inline-block; background:var(--accent); color:var(--on); text-decoration:none;
                  font-weight:600; padding:.8rem 1.6rem; border-radius:999px; }
            </style></head>
            <body><main>
              <div class="host">${escape(UrlInput.displayHost(failedUrl))}</div>
              <h1>${escape(text.title)}</h1>
              <p>${escape(text.message)}</p>
              <a href="$retryHref">${escape(text.retry)}</a>
            </main></body></html>
        """.trimIndent()
    }

    fun dataUri(html: String): String =
        "data:text/html;charset=utf-8;base64," + Base64.getEncoder().encodeToString(html.toByteArray(Charsets.UTF_8))

    private fun escape(text: String): String = buildString(text.length) {
        for (c in text) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(c)
            }
        }
    }
}
