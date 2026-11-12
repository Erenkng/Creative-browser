// Exercises the bundled page-tools extension (ad blocking, reader view, dark pages) against
// a real DOM. Run with: npm install && npm test
const fs = require("fs");
const vm = require("vm");
const { JSDOM } = require("jsdom");
const path = require("path");
const EXT = path.join(__dirname, "../../core/engine/src/main/assets/extensions/oavi/");
const read = name => fs.readFileSync(EXT + name, "utf8");
let failed = 0;
const check = (name, ok, detail) => { console.log((ok ? "  ok   " : "  FAIL ") + name + (ok ? "" : "  -> " + detail)); if (!ok) failed++; };

// ---------- background.js: request blocking ----------
{
  let listener = null, portListener = null;
  const posted = [];
  const browser = {
    webRequest: { onBeforeRequest: { addListener: fn => { listener = fn; } } },
    runtime: { connectNative: () => ({
      onMessage: { addListener: fn => { portListener = fn; } },
      onDisconnect: { addListener: () => {} },
      postMessage: m => posted.push(m),
    }) },
  };
  let tick = null;
  const ctx = { browser, URL, setTimeout: () => {}, setInterval: fn => { tick = fn; }, console };
  vm.createContext(ctx);
  vm.runInContext(read("hosts.js") + "\n" + read("background.js"), ctx);
  const req = (url, type, documentUrl, thirdParty = true) => listener({ url, type, documentUrl, thirdParty });
  console.log("background.js");
  check("blocks a third-party ad script", req("https://securepubads.g.doubleclick.net/tag/js/gpt.js", "script", "https://news.example/").cancel === true);
  check("blocks subdomains by suffix", req("https://a.b.taboola.com/x.js", "script", "https://news.example/").cancel === true);
  check("allows ordinary third-party requests", !req("https://cdn.jsdelivr.net/lib.js", "script", "https://news.example/").cancel);
  check("never blocks a page the user opens", !req("https://www.taboola.com/", "main_frame", undefined).cancel);
  check("allows first-party requests", !req("https://www.criteo.com/app.js", "script", "https://www.criteo.com/", false).cancel);
  check("does not match look-alike hosts", !req("https://notdoubleclick.net/x.js", "script", "https://news.example/").cancel);
  portListener({ type: "config", adBlock: true, allowHosts: ["news.example"] });
  check("respects the per-site switch", !req("https://ad.doubleclick.net/x.js", "script", "https://www.news.example/story").cancel);
  check("per-site switch does not leak to other sites", req("https://ad.doubleclick.net/x.js", "script", "https://other.example/").cancel === true);
  portListener({ type: "config", adBlock: false, allowHosts: [] });
  check("off means off", !req("https://ad.doubleclick.net/x.js", "script", "https://other.example/").cancel);
  tick();
  check("reports the blocked count once", posted.length === 1 && posted[0].type === "blocked" && posted[0].count === 3, JSON.stringify(posted));
  tick();
  check("does not report zero", posted.length === 1);
}

// ---------- content.js: theme colour, dark pages, reader ----------
function page(html, { dark = false } = {}) {
  const dom = new JSDOM(html, { url: "https://news.example/story", runScripts: "outside-only", pretendToBeVisual: true });
  const w = dom.window;
  const sent = [];
  let onMessage = null;
  w.browser = { runtime: { connectNative: () => ({
    onMessage: { addListener: fn => { onMessage = fn; } },
    onDisconnect: { addListener: () => {} },
    postMessage: m => sent.push(m),
  }) } };
  w.matchMedia = q => ({ matches: dark && /dark/.test(q) });
  w.scrollTo = () => {};
  let reloaded = 0;
  // jsdom cannot navigate; record the call instead.
  const run = code => w.eval(code);
  run(read("Readability-readerable.js"));
  run(read("Readability.js"));
  run(read("content.js").replace("window.location.reload()", "window.__reloaded = (window.__reloaded || 0) + 1"));
  w.document.dispatchEvent(new w.Event("DOMContentLoaded"));
  return { w, sent, send: m => onMessage(m), last: () => sent[sent.length - 1] };
}

const paragraph = "Oavi is a browser built around a single floating control that changes shape instead of adding bars. ".repeat(6);
const article = `<!doctype html><html><head><title>A long story</title>
<meta name="theme-color" content="#1da1f2">
<style>body{background:#fff}</style></head><body>
<nav><a href="/">Home</a><a href="/a">A</a></nav>
<article><h1>A long story</h1><p class="byline">By Someone</p>
${Array.from({ length: 8 }, () => `<p>${paragraph}</p>`).join("")}
<script>window.evil = 1</script><img src="x.png" onerror="window.evil=2"><a href="javascript:window.evil=3" style="color:red">link</a></article>
<aside>Sidebar junk</aside></body></html>`;

console.log("content.js");
{
  const p = page(article);
  const first = p.sent.find(m => m.type === "page");
  check("reports the site's theme colour", first && first.themeColor === "#1da1f2", JSON.stringify(first));
  check("recognises an article", first && first.readerable === true && first.reader === false, JSON.stringify(first));

  p.send({ type: "config", forceDark: true });
  check("darkens a light page", p.w.document.documentElement.hasAttribute("data-oavi-dark"));
  p.send({ type: "config", forceDark: false });
  check("lifts the darkening again", !p.w.document.documentElement.hasAttribute("data-oavi-dark"));

  p.send({ type: "config", forceDark: true });
  p.send({ type: "reader", on: true, theme: "sepia", fontScale: 1.3 });
  const root = p.w.document.documentElement;
  check("enters reader view", root.getAttribute("data-oavi-reader") === "sepia" && p.last().reader === true, JSON.stringify(p.last()));
  check("applies the text size", root.style.getPropertyValue("--oavi-scale") === "1.3");
  check("reader replaces forced dark", !root.hasAttribute("data-oavi-dark"));
  check("keeps the article text", p.w.document.querySelector(".oavi-content").textContent.includes("single floating control"));
  check("shows the title", p.w.document.querySelector(".oavi-title").textContent === "A long story");
  check("drops navigation and sidebars", !p.w.document.body.textContent.includes("Sidebar junk") && !p.w.document.querySelector("nav"));
  check("carries no scripts", !p.w.document.querySelector(".oavi-content script"));
  check("carries no event handlers", !p.w.document.querySelector("[onerror]"), p.w.document.querySelector(".oavi-content").innerHTML.slice(-200));
  check("keeps the image itself", !!p.w.document.querySelector(".oavi-content img"));
  check("nothing from the page ran", p.w.evil === undefined, String(p.w.evil));
  check("drops javascript: links and inline styles", !p.w.document.querySelector('.oavi-content a[href^="javascript"]') && !p.w.document.querySelector(".oavi-content [style]"));
  check("removes the page's own styles", p.w.document.querySelectorAll("style").length === 0);

  p.send({ type: "reader", on: true, theme: "dark", fontScale: 1 });
  check("restyles without re-parsing", root.getAttribute("data-oavi-reader") === "dark" && root.style.getPropertyValue("--oavi-scale") === "1");
  p.send({ type: "reader", on: false });
  check("leaving reader reloads the page", p.w.__reloaded === 1);
}
{
  const p = page(`<!doctype html><html><head><meta name="color-scheme" content="dark light"></head><body><p>hi</p></body></html>`);
  p.send({ type: "config", forceDark: true });
  check("leaves pages with their own dark theme alone", !p.w.document.documentElement.hasAttribute("data-oavi-dark"));
  const first = p.sent.find(m => m.type === "page");
  check("short pages are not offered reader view", first && first.readerable === false && first.themeColor === null, JSON.stringify(first));
}
{
  const p = page(`<!doctype html><html><head></head><body style="background-color: rgb(18, 18, 20)"><p>hi</p></body></html>`);
  p.send({ type: "config", forceDark: true });
  check("leaves pages that are already dark alone", !p.w.document.documentElement.hasAttribute("data-oavi-dark"));
}
{
  const p = page(`<!doctype html><html><head>
    <meta name="theme-color" media="(prefers-color-scheme: dark)" content="#000000">
    <meta name="theme-color" media="(prefers-color-scheme: light)" content="#ffcc00"></head><body></body></html>`, { dark: true });
  check("picks the theme colour for the current scheme", p.sent.find(m => m.type === "page").themeColor === "#000000");
}
{
  const p = page(`<!doctype html><html><head></head><body><p>too short</p></body></html>`);
  p.send({ type: "reader", on: true, theme: "auto", fontScale: 1 });
  check("refuses reader view when nothing can be extracted", !p.w.document.documentElement.hasAttribute("data-oavi-reader") || p.last().reader === true);
}
console.log(failed === 0 ? "\nALL EXTENSION TESTS PASSED" : `\n${failed} FAILED`);
process.exit(failed ? 1 : 0);
