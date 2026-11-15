"use strict";

// Runs for the whole browser. Cancels third-party requests to the networks in hosts.js,
// unless the user switched protection off for the site being viewed, and reports how many
// requests were stopped so the app can show it. It also leaves images out when the data
// saver is on, and takes tracking parameters (utm_source, fbclid …) off pages being opened.

let adBlock = true;
let dataSaver = false;
let cleanLinks = true;
let allowedSites = new Set();
let pendingCount = 0;
let port = null;

// Parameters that only exist to follow people from link to link. Kept in step with
// LinkCleaner.kt, which cleans links that are copied or shared.
const TRACKING_PARAMETERS = new Set([
  "fbclid", "gclid", "gclsrc", "dclid", "gbraid", "wbraid", "msclkid", "twclid", "ttclid",
  "li_fat_id", "igshid", "igsh", "yclid", "ysclid", "mc_cid", "mc_eid", "_hsenc", "_hsmi",
  "mkt_tok", "oly_anon_id", "oly_enc_id", "vero_id", "vero_conv", "__s", "rb_clickid",
  "s_cid", "srsltid", "_openstat", "wickedid", "epik", "ndclid", "sc_cid", "irclickid",
]);
const TRACKING_PREFIXES = ["utm_", "pk_", "mtm_", "hsa_", "stm_"];

/** The address without its tracking parameters, or null when there is nothing to remove. */
function cleanUrl(url) {
  let parsed;
  try {
    parsed = new URL(url);
  } catch (e) {
    return null;
  }
  if (!parsed.search) {
    return null;
  }
  const kept = [];
  let removed = false;
  for (const part of parsed.search.slice(1).split("&")) {
    if (!part) {
      continue;
    }
    const raw = part.split("=")[0];
    let name = raw;
    try {
      name = decodeURIComponent(raw);
    } catch (e) {
      // A malformed escape; compare the parameter as written.
    }
    name = name.toLowerCase();
    if (TRACKING_PARAMETERS.has(name) || TRACKING_PREFIXES.some(prefix => name.startsWith(prefix))) {
      removed = true;
    } else {
      kept.push(part);
    }
  }
  if (!removed) {
    return null;
  }
  parsed.search = kept.length ? "?" + kept.join("&") : "";
  return parsed.toString();
}

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
    if (details.type === "main_frame") {
      if (cleanLinks && details.method !== "POST") {
        const clean = cleanUrl(details.url);
        if (clean) {
          return { redirectUrl: clean };
        }
      }
      return {};
    }
    if (dataSaver && (details.type === "image" || details.type === "imageset")) {
      return { cancel: true };
    }
    if (!adBlock || details.thirdParty === false) {
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
      dataSaver = !!message.dataSaver;
      cleanLinks = message.cleanLinks !== false;
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
