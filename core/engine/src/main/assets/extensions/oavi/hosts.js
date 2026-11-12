"use strict";

// Advertising and behavioural-tracking networks blocked when they are loaded by another site.
// Matching is by domain suffix, so "doubleclick.net" also covers "ad.doubleclick.net".
// Deliberately leaves out sign-in SDKs, tag managers and CDNs that sites need to function.
const OAVI_AD_HOSTS = new Set([
  // Google advertising and measurement
  "doubleclick.net", "doubleclick.com", "googlesyndication.com", "googleadservices.com",
  "googletagservices.com", "adservice.google.com", "2mdn.net", "google-analytics.com",
  "app-measurement.com",
  // Large exchanges and supply/demand platforms
  "adnxs.com", "adsrvr.org", "amazon-adsystem.com", "criteo.com", "criteo.net", "pubmatic.com",
  "rubiconproject.com", "openx.net", "casalemedia.com", "indexww.com", "bidswitch.net",
  "media.net", "33across.com", "sharethrough.com", "triplelift.com", "3lift.com", "yieldmo.com",
  "gumgum.com", "sovrn.com", "lijit.com", "contextweb.com", "advertising.com", "adtechus.com",
  "adform.net", "smartadserver.com", "teads.tv", "improvedigital.com", "smaato.net",
  "onetag-sys.com", "adkernel.com", "undertone.com", "emxdgt.com", "districtm.io",
  "e-planning.net", "betweendigital.com", "appier.net", "admixer.net", "adocean.pl",
  "richaudience.com", "adyoulike.com", "omnitagjs.com", "smilewanted.com", "seedtag.com",
  "serving-sys.com", "sizmek.com", "flashtalking.com", "mathtag.com", "turn.com",
  "adsymptotic.com", "adgrx.com", "zedo.com", "adblade.com", "adsnative.com", "nativo.com",
  "ntv.io", "buysellads.com", "bidvertiser.com", "yandexadexchange.net", "adfox.ru",
  // Content recommendation widgets
  "taboola.com", "taboolasyndication.com", "outbrain.com", "outbrainimg.com", "zemanta.com",
  "mgid.com", "revcontent.com", "disqusads.com",
  // Video advertising
  "spotxchange.com", "spotx.tv", "springserve.com", "freewheel.tv", "fwmrm.net", "innovid.com",
  "tremorhub.com", "stickyadstv.com", "lkqd.net", "aniview.com", "connatix.com", "vidazoo.com",
  "primis.tech", "sekindo.com",
  // In-app and mobile networks
  "adcolony.com", "applovin.com", "inmobi.com", "mopub.com", "chartboost.com", "vungle.com",
  "liftoff.io",
  // Pop-ups and aggressive formats
  "popads.net", "popcash.net", "propellerads.com", "exoclick.com", "juicyads.com",
  "trafficjunky.net", "adsterra.com", "hilltopads.net", "clickadu.com", "onclickads.net",
  "adsco.re",
  // Data brokers and identity sync
  "bluekai.com", "exelator.com", "demdex.net", "everesttech.net", "krxd.net", "rlcdn.com",
  "tapad.com", "agkn.com", "crwdcntrl.net", "liadm.com", "pippio.com", "eyeota.net",
  "id5-sync.com", "ad.gt", "adroll.com", "omtrdc.net", "2o7.net",
  // Audience measurement and session recording
  "scorecardresearch.com", "quantserve.com", "quantcount.com", "moatads.com",
  "adsafeprotected.com", "doubleverify.com", "imrworldwide.com", "chartbeat.com",
  "chartbeat.net", "hotjar.com", "mixpanel.com", "amplitude.com", "fullstory.com",
  "mouseflow.com", "crazyegg.com", "clarity.ms", "gemius.pl", "mc.yandex.ru", "an.yandex.ru",
  // Social and search ad pixels
  "ads-twitter.com", "analytics.twitter.com", "ads.linkedin.com", "bat.bing.com",
  "ads.yahoo.com", "analytics.yahoo.com", "addthis.com", "sharethis.com",
  // Networks common on Turkish sites
  "admatic.com.tr", "reklamstore.com", "virgul.com", "adhood.com", "medyanet.net"
]);
