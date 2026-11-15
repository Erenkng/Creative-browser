"use strict";

// Runs in every top-level page. Talks to the app over a native port to:
//  - report the site's theme colour and whether the page reads as an article,
//  - switch the page into reader view and back,
//  - darken pages that do not offer a dark theme of their own,
//  - answer cookie consent pop-ups with "reject" when the user wants that,
//  - hand over the article's text to be read aloud.
// Styling lives in page.css and is switched with attributes on <html>, so it is not
// affected by the page's content security policy.

(() => {
  if (window.top !== window) {
    return;
  }

  let port = null;
  let forceDark = false;
  let rejectCookies = false;
  let cookieWatch = null;
  let readerOn = false;
  let readerTheme = "auto";
  let readerScale = 1;

  function send(message) {
    try {
      if (port) {
        port.postMessage(message);
      }
    } catch (e) {
      // The tab was closed or the app is restarting; nothing to report to.
    }
  }

  function parseRgb(text) {
    const match = /rgba?\(\s*(\d+)[,\s]+(\d+)[,\s]+(\d+)(?:[,\s/]+([\d.]+))?/.exec(text || "");
    if (!match) {
      return null;
    }
    if (match[4] !== undefined && parseFloat(match[4]) < 0.5) {
      return null;
    }
    return [Number(match[1]), Number(match[2]), Number(match[3])];
  }

  function luminance(rgb) {
    const channel = value => {
      const v = value / 255;
      return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    };
    return 0.2126 * channel(rgb[0]) + 0.7152 * channel(rgb[1]) + 0.0722 * channel(rgb[2]);
  }

  // A page counts as already dark when it declares a dark colour scheme or paints a dark
  // background. Filters do not change computed colours, so this stays true while inverted.
  function pageIsAlreadyDark() {
    const scheme = document.querySelector('meta[name="color-scheme"]');
    if (scheme && /dark/i.test(scheme.content || "")) {
      return true;
    }
    const root = document.documentElement;
    const body = document.body;
    const background =
      (body && parseRgb(getComputedStyle(body).backgroundColor)) ||
      (root && parseRgb(getComputedStyle(root).backgroundColor));
    return background ? luminance(background) < 0.2 : false;
  }

  function applyDark() {
    const root = document.documentElement;
    if (!root) {
      return;
    }
    if (forceDark && !readerOn && !pageIsAlreadyDark()) {
      root.setAttribute("data-oavi-dark", "");
    } else {
      root.removeAttribute("data-oavi-dark");
    }
  }

  // The colour at the very top of the visible page, so the strip under the status bar can
  // continue it instead of showing a seam. Forced-dark pages are inverted on screen, so their
  // colour is inverted here too.
  function topColor() {
    let node = null;
    try {
      node = document.elementFromPoint(Math.floor(window.innerWidth / 2), 1);
    } catch (e) {
      node = null;
    }
    let rgb = null;
    while (node && node.nodeType === 1 && !rgb) {
      rgb = parseRgb(getComputedStyle(node).backgroundColor);
      node = node.parentElement;
    }
    if (!rgb && document.body) {
      rgb = parseRgb(getComputedStyle(document.body).backgroundColor);
    }
    if (!rgb && document.documentElement) {
      rgb = parseRgb(getComputedStyle(document.documentElement).backgroundColor);
    }
    if (!rgb) {
      const scheme = document.documentElement ? getComputedStyle(document.documentElement).colorScheme || "" : "";
      const dark = /dark/.test(scheme) && window.matchMedia("(prefers-color-scheme: dark)").matches;
      rgb = dark ? [28, 27, 34] : [255, 255, 255];
    }
    if (document.documentElement && document.documentElement.hasAttribute("data-oavi-dark")) {
      rgb = rgb.map(value => 255 - value);
    }
    return `rgb(${rgb[0]}, ${rgb[1]}, ${rgb[2]})`;
  }

  let lastTop = null;
  let topTimer = 0;

  function reportTop() {
    topTimer = 0;
    const color = topColor();
    if (color !== lastTop) {
      lastTop = color;
      send({ type: "top", color });
    }
  }

  // The site's icon: a bitmap close to 180px if the page offers one (Android cannot draw
  // SVG icons as bitmaps), otherwise the traditional /favicon.ico.
  function iconUrl() {
    let best = null;
    let bestScore = -1;
    const links = document.querySelectorAll(
      'link[rel~="icon"], link[rel~="apple-touch-icon"], link[rel~="apple-touch-icon-precomposed"]'
    );
    for (const link of links) {
      const href = link.href;
      if (!href || /^data:/i.test(href)) {
        continue;
      }
      if ((link.getAttribute("type") || "").includes("svg") || /\.svg($|[?#])/i.test(href)) {
        continue;
      }
      const rel = (link.getAttribute("rel") || "").toLowerCase();
      const sizes = (link.getAttribute("sizes") || "")
        .split(/\s+/)
        .map(value => parseInt(value, 10))
        .filter(value => value > 0);
      const size = sizes.length ? Math.max(...sizes) : rel.includes("apple-touch") ? 180 : 32;
      const score = size >= 64 ? 1000 - Math.abs(size - 180) : size;
      if (score > bestScore) {
        bestScore = score;
        best = href;
      }
    }
    if (best) {
      return best;
    }
    try {
      return new URL("/favicon.ico", window.location.href).href;
    } catch (e) {
      return null;
    }
  }

  function themeColor() {
    for (const meta of document.querySelectorAll('meta[name="theme-color"]')) {
      const media = meta.getAttribute("media");
      if (!media || window.matchMedia(media).matches) {
        return meta.content || null;
      }
    }
    return null;
  }

  function report() {
    let readerable = false;
    if (!readerOn) {
      try {
        readerable = isProbablyReaderable(document);
      } catch (e) {
        readerable = false;
      }
    }
    lastTop = topColor();
    send({
      type: "page",
      themeColor: themeColor(),
      readerable,
      reader: readerOn,
      topColor: lastTop,
      icon: readerOn ? null : iconUrl(),
      lang: document.documentElement ? document.documentElement.lang || "" : "",
    });
  }

  function applyReaderStyle() {
    const root = document.documentElement;
    root.setAttribute("data-oavi-reader", readerTheme);
    root.style.setProperty("--oavi-scale", String(readerScale));
  }

  // Readability extracts the article but does not make it safe. The markup is parsed inside
  // a <template>, where nothing runs or loads, and stripped of anything that could execute
  // before it is shown.
  function sanitize(html) {
    const template = document.createElement("template");
    template.innerHTML = html;
    const fragment = template.content;
    for (const node of fragment.querySelectorAll("script, style, link, object, embed, form, input, button, select, textarea")) {
      node.remove();
    }
    for (const node of fragment.querySelectorAll("*")) {
      for (const attribute of Array.from(node.attributes)) {
        const name = attribute.name.toLowerCase();
        const scripted = /^\s*javascript:/i.test(attribute.value);
        if (name.startsWith("on") || name === "style" || name === "srcdoc" || scripted) {
          node.removeAttribute(attribute.name);
        }
      }
    }
    return fragment;
  }

  function enterReader() {
    let article = null;
    try {
      article = new Readability(document.cloneNode(true)).parse();
    } catch (e) {
      article = null;
    }
    if (!article || !article.content) {
      send({ type: "page", themeColor: themeColor(), readerable: false, reader: false });
      return;
    }

    readerOn = true;
    const root = document.documentElement;
    root.removeAttribute("data-oavi-dark");
    root.removeAttribute("class");
    root.removeAttribute("style");

    // Drop the page's own styling so only the reader stylesheet applies.
    for (const node of document.querySelectorAll('style, link[rel~="stylesheet"]')) {
      node.remove();
    }

    const body = document.body;
    body.removeAttribute("class");
    body.removeAttribute("style");
    body.textContent = "";

    const main = document.createElement("main");
    main.className = "oavi-article";

    const title = document.createElement("h1");
    title.className = "oavi-title";
    title.textContent = article.title || document.title || "";
    main.appendChild(title);

    const byline = [article.byline, article.siteName].filter(Boolean).join(" · ");
    if (byline) {
      const line = document.createElement("p");
      line.className = "oavi-byline";
      line.textContent = byline;
      main.appendChild(line);
    }

    const content = document.createElement("div");
    content.className = "oavi-content";
    content.appendChild(sanitize(article.content));
    main.appendChild(content);

    body.appendChild(main);
    applyReaderStyle();
    window.scrollTo(0, 0);
    lastTop = topColor();
    send({ type: "page", themeColor: null, readerable: false, reader: true, topColor: lastTop });
  }

  // ---- cookie consent pop-ups

  // The "reject" buttons of the consent tools most sites use.
  const REJECT_SELECTORS = [
    "#onetrust-reject-all-handler",
    ".ot-pc-refuse-all-handler",
    "#CybotCookiebotDialogBodyButtonDecline",
    "#CybotCookiebotDialogBodyLevelButtonLevelOptinDeclineAll",
    "#didomi-notice-disagree-button",
    ".didomi-continue-without-agreeing",
    "button.sp_choice_type_REJECT_ALL",
    ".qc-cmp2-summary-buttons button[mode='secondary']",
    "#truste-consent-required",
    ".cmplz-btn.cmplz-deny",
    ".cky-btn-reject",
    ".osano-cm-denyAll",
    ".iubenda-cs-reject-btn",
    "[data-testid='uc-deny-all-button']",
    ".fc-cta-do-not-consent",
    ".cc-deny",
    "#cookiescript_reject",
    ".js-cookie-consent-reject",
  ];

  // What a reject button says, in the languages Oavi speaks and a few common others.
  const REJECT_WORDS = [
    "reject all", "reject", "decline all", "decline", "deny all", "deny", "refuse all",
    "only necessary", "necessary only", "only essential", "essential only",
    "use necessary cookies only", "continue without accepting", "do not consent",
    "tümünü reddet", "hepsini reddet", "reddet", "kabul etme", "sadece gerekli",
    "yalnızca gerekli", "yalnızca zorunlu", "sadece zorunlu", "zorunlu çerezler",
    "gerekli çerezler", "kabul etmeden devam et",
    "alle ablehnen", "ablehnen", "nur notwendige", "tout refuser", "refuser",
    "continuer sans accepter", "rechazar todo", "rechazar", "rifiuta tutto", "rifiuta",
  ];

  const CONSENT_HINT = /cookie|consent|gdpr|kvkk|privacy|cmp|çerez|cerez|banner|notice/i;

  function visible(element) {
    const box = element.getBoundingClientRect ? element.getBoundingClientRect() : null;
    if (box && box.width === 0 && box.height === 0 && element.offsetParent === null) {
      return false;
    }
    const style = window.getComputedStyle ? window.getComputedStyle(element) : null;
    return !style || (style.visibility !== "hidden" && style.display !== "none");
  }

  function insideConsent(element) {
    for (let node = element; node && node !== document.body; node = node.parentElement) {
      const label = [node.id, node.className && node.className.baseVal === undefined ? node.className : "",
        node.getAttribute && node.getAttribute("aria-label"), node.getAttribute && node.getAttribute("role")]
        .filter(Boolean).join(" ");
      if (CONSENT_HINT.test(label)) {
        return true;
      }
    }
    return false;
  }

  /** Clicks a reject button if one is showing. Returns true when it did. */
  function rejectCookieBanner() {
    for (const selector of REJECT_SELECTORS) {
      let button = null;
      try {
        button = document.querySelector(selector);
      } catch (e) {
        button = null;
      }
      if (button && visible(button)) {
        button.click();
        return true;
      }
    }
    const candidates = document.querySelectorAll("button, [role='button'], a, input[type='button'], input[type='submit']");
    for (const element of candidates) {
      const text = (element.value || element.textContent || "").replace(/\s+/g, " ").trim().toLowerCase();
      if (!text || text.length > 48 || !REJECT_WORDS.includes(text)) {
        continue;
      }
      if (insideConsent(element) && visible(element)) {
        element.click();
        return true;
      }
    }
    return false;
  }

  /** Watches the page for a consent pop-up for a while after it loads, since most appear late. */
  function watchCookieBanners() {
    if (!rejectCookies || cookieWatch || !document.body) {
      return;
    }
    if (rejectCookieBanner()) {
      return;
    }
    let tries = 0;
    cookieWatch = new MutationObserver(() => {
      if (tries++ > 400 || rejectCookieBanner()) {
        cookieWatch.disconnect();
      }
    });
    cookieWatch.observe(document.body, { childList: true, subtree: true });
    setTimeout(() => {
      if (cookieWatch) {
        cookieWatch.disconnect();
      }
    }, 12000);
  }

  // ---- reading aloud

  /** The article's own text when the page reads as one, otherwise the page's visible text. */
  function articleText() {
    let title = document.title || "";
    let text = "";
    try {
      const article = new Readability(document.cloneNode(true)).parse();
      if (article && article.textContent && article.textContent.trim().length > 200) {
        title = article.title || title;
        text = article.textContent;
      }
    } catch (e) {
      text = "";
    }
    if (!text && document.body) {
      text = document.body.innerText || document.body.textContent || "";
    }
    text = text.replace(/[ \t\u00a0]+/g, " ").replace(/\n\s*\n+/g, "\n\n").trim();
    const lang = (document.documentElement.getAttribute("lang") || "").trim();
    return { type: "article", title: title.trim(), text: text.slice(0, 200000), lang };
  }

  function onMessage(message) {
    if (!message) {
      return;
    }
    if (message.type === "config") {
      forceDark = !!message.forceDark;
      rejectCookies = !!message.rejectCookies;
      applyDark();
      reportTop();
      if (document.readyState !== "loading") {
        watchCookieBanners();
      }
    } else if (message.type === "article") {
      send(articleText());
    } else if (message.type === "reader") {
      readerTheme = message.theme || "auto";
      readerScale = Number(message.fontScale) || 1;
      if (message.on && !readerOn) {
        enterReader();
      } else if (message.on && readerOn) {
        applyReaderStyle();
        reportTop();
      } else if (!message.on && readerOn) {
        window.location.reload();
      }
    }
  }

  function connect() {
    try {
      port = browser.runtime.connectNative("oaviPage");
      port.onMessage.addListener(onMessage);
      port.onDisconnect.addListener(() => {
        port = null;
      });
    } catch (e) {
      port = null;
    }
  }

  function ready() {
    applyDark();
    report();
    watchCookieBanners();
  }

  connect();
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", ready, { once: true });
  } else {
    ready();
  }
  window.addEventListener("load", ready, { once: true });
  window.addEventListener(
    "scroll",
    () => {
      if (!topTimer) {
        topTimer = setTimeout(reportTop, 200);
      }
    },
    { passive: true }
  );
})();
