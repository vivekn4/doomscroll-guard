/* Doomscroll Guard — shorts-stripper.js
 * Injected at document_start into the wrapper's WKWebView (and mirrored by
 * the Safari Content Blocker rules in shared/rules/rules.json).
 *
 * Three jobs, driven by the rule bundle's ios.wrapper.js_rules:
 *  1. route-rewrite: /shorts/<id> -> /watch?v=<id> (deep links + in-page taps)
 *  2. route-block:   navigations into /reels/ are stopped, bounced to feed
 *  3. dom-remove:    hide Shorts shelves / nav icons / Reels tab via CSS
 *                    selectors, re-cleaned by a MutationObserver (SPAs mutate).
 *
 * Selectors rot when the sites update — keep them in sync with
 * shared/rules/rules.json and run tools/validate_rules.py before release.
 */
(function () {
  'use strict';
  if (window.__dsgInstalled) return;
  window.__dsgInstalled = true;

  var host = location.hostname;

  /* ---------- 1 + 2. route rules ---------- */
  function applyRouteRules() {
    var path = location.pathname;

    // YouTube Shorts -> regular watch page
    var m = path.match(/^\/shorts\/([A-Za-z0-9_-]{6,})/);
    if (m) {
      location.replace('https://www.youtube.com/watch?v=' + m[1]);
      return;
    }
    // Instagram reels route -> bounce to feed
    if (/^\/reels?\//.test(path)) {
      location.replace('https://www.instagram.com/');
      return;
    }
  }

  /* ---------- 3. DOM removal ---------- */
  var YT_SELECTORS = [
    'ytm-reel-shelf-renderer',
    'ytm-pivot-bar-item-renderer:has(.pivot-shorts)',
    'a[href^="/shorts"]',
    '[aria-label="Shorts"]',
    'ytd-reel-shelf-renderer',
    'ytd-rich-shelf-renderer[is-shorts]'
  ];
  var IG_SELECTORS = [
    'a[href="/reels/"]',
    'a[href^="/reels"]',
    'svg[aria-label="Reels"]',
    '[aria-label="Reels"]'
  ];

  var HIDE_CSS = '{display:none !important;}';

  function selectorsForHost() {
    if (host.indexOf('youtube.com') !== -1) return YT_SELECTORS;
    if (host.indexOf('instagram.com') !== -1) return IG_SELECTORS;
    return [];
  }

  function clean() {
    var sels = selectorsForHost();
    for (var i = 0; i < sels.length; i++) {
      var sel = sels[i];
      var els;
      try { els = document.querySelectorAll(sel); }
      catch (e) { continue; } // selector rot: skip, don't crash
      for (var j = 0; j < els.length; j++) {
        els[j].style.setProperty('display', 'none', 'important');
      }
    }
  }

  // Intercept in-page SPA navigations (YouTube/IG rarely do full loads).
  function hookHistory() {
    ['pushState', 'replaceState'].forEach(function (fn) {
      var orig = history[fn];
      history[fn] = function () {
        var r = orig.apply(this, arguments);
        applyRouteRules();
        setTimeout(clean, 50);
        return r;
      };
    });
    window.addEventListener('popstate', function () { applyRouteRules(); setTimeout(clean, 50); });
  }

  // Intercept taps on shorts links before the SPA router sees them.
  document.addEventListener('click', function (e) {
    var a = e.target && e.target.closest ? e.target.closest('a[href^="/shorts/"]') : null;
    if (a) {
      e.preventDefault(); e.stopPropagation();
      var id = a.getAttribute('href').split('/')[2];
      location.href = 'https://www.youtube.com/watch?v=' + id;
    }
  }, true);

  applyRouteRules();
  hookHistory();
  clean();
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', clean);
  }
  // Re-clean as the SPA mutates. Throttled: SPAs are chatty.
  var pending = false;
  new MutationObserver(function () {
    if (pending) return;
    pending = true;
    setTimeout(function () { pending = false; clean(); }, 300);
  }).observe(document.documentElement, { childList: true, subtree: true });
})();
