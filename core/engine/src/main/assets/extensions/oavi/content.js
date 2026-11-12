"use strict";

// Runs in every top-level page. Talks to the app over a native port to:
//  - report the site's theme colour and whether the page reads as an article,
//  - switch the page into reader view and back,
//  - darken pages that do not offer a dark theme of their own.
// Styling lives in page.css and is switched with attributes on <html>, so it is not
// affected by the page's content security policy.

(() => {
  if (window.top !== window) {
    return;
  }

  let port = null;
  let forceDark = false;
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
    send({ type: "page", themeColor: themeColor(), readerable, reader: readerOn });
  }

  function applyReaderStyle() {
    const root = document.documentElement;
    root.setAttribute("data-oavi-reader", readerTheme);
    root.style.setProperty("--oavi-scale", String(readerScale));
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
    // Readability returns markup with scripts and event handlers already removed.
    content.innerHTML = article.content;
    main.appendChild(content);

    body.appendChild(main);
    applyReaderStyle();
    window.scrollTo(0, 0);
    send({ type: "page", themeColor: null, readerable: false, reader: true });
  }

  function onMessage(message) {
    if (!message) {
      return;
    }
    if (message.type === "config") {
      forceDark = !!message.forceDark;
      applyDark();
    } else if (message.type === "reader") {
      readerTheme = message.theme || "auto";
      readerScale = Number(message.fontScale) || 1;
      if (message.on && !readerOn) {
        enterReader();
      } else if (message.on && readerOn) {
        applyReaderStyle();
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
  }

  connect();
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", ready, { once: true });
  } else {
    ready();
  }
  window.addEventListener("load", ready, { once: true });
})();
