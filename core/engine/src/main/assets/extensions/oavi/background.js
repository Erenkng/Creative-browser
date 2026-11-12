"use strict";

// Runs for the whole browser. Cancels third-party requests to the networks in hosts.js,
// unless the user switched protection off for the site being viewed, and reports how many
// requests were stopped so the app can show it.

let adBlock = true;
let allowedSites = new Set();
let pendingCount = 0;
let port = null;

function hostOf(url) {
  try {
    return new URL(url).hostname;
  } catch (e) {
    return "";
  }
}

function inSet(set, host) {
  let candidate = host;
  for (;;) {
    if (set.has(candidate)) {
      return true;
    }
    const dot = candidate.indexOf(".");
    if (dot < 0) {
      return false;
    }
    candidate = candidate.slice(dot + 1);
  }
}

browser.webRequest.onBeforeRequest.addListener(
  details => {
    if (!adBlock || details.type === "main_frame" || details.thirdParty === false) {
      return {};
    }
    const host = hostOf(details.url);
    if (!host || !inSet(OAVI_AD_HOSTS, host)) {
      return {};
    }
    const site = hostOf(details.documentUrl || details.originUrl || "");
    if (site && inSet(allowedSites, site.replace(/^www\./, ""))) {
      return {};
    }
    pendingCount++;
    return { cancel: true };
  },
  { urls: ["http://*/*", "https://*/*", "ws://*/*", "wss://*/*"] },
  ["blocking"]
);

function connect() {
  try {
    port = browser.runtime.connectNative("oaviBackground");
  } catch (e) {
    port = null;
    setTimeout(connect, 2000);
    return;
  }
  port.onMessage.addListener(message => {
    if (message && message.type === "config") {
      adBlock = !!message.adBlock;
      allowedSites = new Set(message.allowHosts || []);
    }
  });
  port.onDisconnect.addListener(() => {
    port = null;
    setTimeout(connect, 2000);
  });
}

connect();

setInterval(() => {
  if (pendingCount > 0 && port) {
    try {
      port.postMessage({ type: "blocked", count: pendingCount });
      pendingCount = 0;
    } catch (e) {
      // The app will receive the count on the next tick.
    }
  }
}, 1500);
