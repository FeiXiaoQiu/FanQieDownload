/**
 * GitHub Pages / 纯静态：固定节点 + CORS 代理
 *
 * 加速建议（强烈推荐）：部署仓库里的 cors-worker.js 到 Cloudflare Workers，然后：
 *   localStorage.setItem('fq_cors_proxy', 'https://xxx.workers.dev')
 * 或页面注入 window.FQ_CORS_PROXY
 */
(function (global) {
  "use strict";

  const FIXED_HOSTS = [
    "http://110.42.57.146:4018",
    "http://81.70.223.143:6897",
    "http://43.143.149.30:8008",
    "http://59.110.160.171:5007",
    "http://103.43.9.59",
  ];

  const CODE_RANGES = [
    [58344, 58715],
    [58345, 58716],
  ];

  const CACHE_PREFIX = "fq_ch_";
  const SEARCH_CACHE_PREFIX = "fq_search_";
  const PROBE_ITEM = "7580458932431225368";
  const LS_PROXY = "fq_cors_proxy";
  const LS_PREF_PROXY = "fq_pref_proxy";
  const LS_PREF_HOST = "fq_pref_host";

  let charset = null;
  /** @type {{host:string,ok:boolean,latency:number}[]} */
  let hostState = FIXED_HOSTS.map(function (h) {
    return { host: h, ok: true, latency: 9999 };
  });
  let cancelFlag = false;
  let preferredProxy =
    (typeof localStorage !== "undefined" && localStorage.getItem(LS_PREF_PROXY)) ||
    "custom";
  let preferredHost =
    (typeof localStorage !== "undefined" && localStorage.getItem(LS_PREF_HOST)) ||
    FIXED_HOSTS[0];

  let sameOriginProxyTried = false;
  let sameOriginProxyOk = false;

  function readCustomProxyBase() {
    try {
      if (global.FQ_CORS_PROXY) return String(global.FQ_CORS_PROXY).trim();
      if (typeof localStorage !== "undefined") {
        const v = localStorage.getItem(LS_PROXY);
        if (v) return String(v).trim();
      }
      if (typeof location !== "undefined" && location.search) {
        const m = /[?&]proxy=([^&]+)/.exec(location.search);
        if (m) return decodeURIComponent(m[1]).trim();
      }
      // 同源 Vercel/Node 部署时自动用 /api/proxy
      if (
        sameOriginProxyOk &&
        typeof location !== "undefined" &&
        (location.protocol === "http:" || location.protocol === "https:")
      ) {
        return String(location.origin).replace(/\/$/, "") + "/api/proxy";
      }
    } catch (e) {
      /* ignore */
    }
    return "";
  }

  async function detectSameOriginProxy() {
    if (sameOriginProxyTried) return sameOriginProxyOk;
    sameOriginProxyTried = true;
    try {
      if (typeof location === "undefined") return false;
      if (location.protocol !== "http:" && location.protocol !== "https:") return false;
      // 用户已配置其它代理则不抢
      try {
        const existing = localStorage.getItem(LS_PROXY);
        if (existing && existing.indexOf(location.host) === -1) {
          return false;
        }
      } catch (e) { /* ignore */ }
      const ctrl = new AbortController();
      const t = setTimeout(function () {
        ctrl.abort();
      }, 2500);
      const res = await fetch(location.origin + "/api/proxy", {
        signal: ctrl.signal,
        cache: "no-store",
      });
      clearTimeout(t);
      if (!res.ok) return false;
      const data = await res.json();
      if (data && (data.ok || data.usage)) {
        sameOriginProxyOk = true;
        preferredProxy = "custom";
        try {
          localStorage.setItem(LS_PROXY, location.origin + "/api/proxy");
        } catch (e) { /* ignore */ }
        return true;
      }
    } catch (e) {
      sameOriginProxyOk = false;
    }
    return false;
  }

  function buildProxyDefs() {
    const defs = [];
    const custom = readCustomProxyBase().replace(/\/$/, "");
    if (custom) {
      defs.push({
        id: "custom",
        build: function (url) {
          return custom + "/?url=" + encodeURIComponent(url);
        },
        parse: function (text) {
          return JSON.parse(text || "{}");
        },
      });
    }
    // 公共代理不稳定，仅作兜底；优先 custom Worker
    defs.push(
      {
        id: "allorigins-json",
        build: function (url) {
          return "https://api.allorigins.win/json?url=" + encodeURIComponent(url);
        },
        parse: function (text) {
          const wrap = JSON.parse(text || "{}");
          if (wrap && typeof wrap.contents === "string") {
            return JSON.parse(wrap.contents || "{}");
          }
          if (wrap && wrap.contents && typeof wrap.contents === "object") {
            return wrap.contents;
          }
          return wrap;
        },
      },
      {
        id: "allorigins-get",
        build: function (url) {
          return "https://api.allorigins.win/get?url=" + encodeURIComponent(url);
        },
        parse: function (text) {
          const wrap = JSON.parse(text || "{}");
          if (wrap && typeof wrap.contents === "string") {
            return JSON.parse(wrap.contents || "{}");
          }
          if (wrap && wrap.contents && typeof wrap.contents === "object") {
            return wrap.contents;
          }
          return wrap;
        },
      }
    );
    return defs;
  }

  function orderedProxies() {
    const defs = buildProxyDefs();
    return defs.slice().sort(function (a, b) {
      if (a.id === preferredProxy) return -1;
      if (b.id === preferredProxy) return 1;
      if (a.id === "custom") return -1;
      if (b.id === "custom") return 1;
      return 0;
    });
  }

  function rememberProxy(id) {
    preferredProxy = id;
    try {
      localStorage.setItem(LS_PREF_PROXY, id);
    } catch (e) {
      /* ignore */
    }
  }

  function rememberHost(host) {
    preferredHost = host;
    try {
      localStorage.setItem(LS_PREF_HOST, host);
    } catch (e) {
      /* ignore */
    }
  }

  function fetchViaProxy(proxy, url, timeout, signal) {
    const ctrl = new AbortController();
    const timer = setTimeout(function () {
      ctrl.abort();
    }, timeout);
    if (signal) {
      if (signal.aborted) ctrl.abort();
      else
        signal.addEventListener("abort", function () {
          ctrl.abort();
        });
    }
    return fetch(proxy.build(url), {
      signal: ctrl.signal,
      headers: { Accept: "application/json" },
      cache: "no-store",
    })
      .then(function (res) {
        if (!res.ok) throw new Error(proxy.id + " HTTP " + res.status);
        return res.text();
      })
      .then(function (text) {
        if (!text || text.charAt(0) === "<") {
          throw new Error(proxy.id + " 非 JSON");
        }
        const data = proxy.parse(text);
        rememberProxy(proxy.id);
        return data;
      })
      .finally(function () {
        clearTimeout(timer);
      });
  }

  /** 直连节点（装 CORS 扩展 / 本机 server 时可用） */
  function fetchDirect(url, timeout, signal) {
    const ctrl = new AbortController();
    const timer = setTimeout(function () {
      ctrl.abort();
    }, timeout);
    if (signal) {
      if (signal.aborted) ctrl.abort();
      else
        signal.addEventListener("abort", function () {
          ctrl.abort();
        });
    }
    return fetch(url, {
      signal: ctrl.signal,
      headers: { Accept: "application/json" },
      cache: "no-store",
      mode: "cors",
    })
      .then(function (res) {
        if (!res.ok) throw new Error("direct HTTP " + res.status);
        return res.text();
      })
      .then(function (text) {
        if (!text || text.charAt(0) === "<") throw new Error("direct 非 JSON");
        const data = JSON.parse(text || "{}");
        rememberProxy("direct");
        return data;
      })
      .finally(function () {
        clearTimeout(timer);
      });
  }

  /** 先直连节点，再走代理（扩展/本机场景最快） */
  async function fetchJsonSerial(pathBuilder, opts) {
    opts = opts || {};
    const timeout = opts.timeout || 18000;
    const hosts = orderHosts(opts.hosts || FIXED_HOSTS.slice());
    const proxies = orderedProxies();
    const ok =
      opts.ok ||
      function () {
        return true;
      };
    let lastErr = null;
    const preferDirect = preferredProxy === "direct" || !readCustomProxyBase();

    if (preferDirect) {
      for (let hi = 0; hi < hosts.length; hi++) {
        const host = hosts[hi];
        try {
          const data = await fetchDirect(pathBuilder(host), Math.min(timeout, 8000), null);
          if (!ok(data, host)) {
            lastErr = new Error("节点无有效数据: " + host);
            continue;
          }
          markOk(host, 0);
          rememberHost(host);
          return { data: data, host: host, proxy: "direct" };
        } catch (e) {
          lastErr = e;
        }
      }
    }

    for (let pi = 0; pi < proxies.length; pi++) {
      const proxy = proxies[pi];
      for (let hi = 0; hi < hosts.length; hi++) {
        const host = hosts[hi];
        try {
          const data = await fetchViaProxy(
            proxy,
            pathBuilder(host),
            timeout,
            null
          );
          if (!ok(data, host)) {
            lastErr = new Error("节点无有效数据: " + host);
            continue;
          }
          markOk(host, 0);
          rememberHost(host);
          return { data: data, host: host, proxy: proxy.id };
        } catch (e) {
          lastErr = e;
          if (hi < hosts.length - 1) markFailSoft(host);
        }
      }
    }
    throw lastErr || new Error("全部节点/代理失败");
  }

  /** 轻量竞速：直连 + 首选代理 × 前 2 节点 */
  async function fetchJsonRace2(pathBuilder, opts) {
    opts = opts || {};
    const timeout = opts.timeout || 16000;
    const hosts = orderHosts(opts.hosts || FIXED_HOSTS.slice()).slice(0, 2);
    const proxy = orderedProxies()[0];
    const ok =
      opts.ok ||
      function () {
        return true;
      };
    if (!hosts.length) throw new Error("无节点");

    return new Promise(function (resolve, reject) {
      const ctrl = new AbortController();
      let pending = 0;
      let lastErr = null;
      let settled = false;

      function finishOk(data, host, proxyId) {
        if (settled) return;
        if (!ok(data, host)) {
          lastErr = new Error("节点无有效数据: " + host);
          return;
        }
        settled = true;
        ctrl.abort();
        markOk(host, 0);
        rememberHost(host);
        resolve({ data: data, host: host, proxy: proxyId });
      }

      function oneDone() {
        pending--;
        if (!settled && pending <= 0) {
          reject(lastErr || new Error("竞速失败"));
        }
      }

      hosts.forEach(function (host) {
        pending++;
        fetchDirect(pathBuilder(host), Math.min(timeout, 7000), ctrl.signal)
          .then(function (data) {
            finishOk(data, host, "direct");
          })
          .catch(function (e) {
            lastErr = e;
          })
          .finally(oneDone);

        if (proxy) {
          pending++;
          fetchViaProxy(proxy, pathBuilder(host), timeout, ctrl.signal)
            .then(function (data) {
              finishOk(data, host, proxy.id);
            })
            .catch(function (e) {
              lastErr = e;
            })
            .finally(oneDone);
        }
      });
    });
  }

  async function raceHosts(pathBuilder, opts) {
    try {
      return await fetchJsonRace2(pathBuilder, opts);
    } catch (e1) {
      return fetchJsonSerial(pathBuilder, opts);
    }
  }

  function orderHosts(hosts) {
    const list = hosts.slice();
    list.sort(function (a, b) {
      if (a === preferredHost) return -1;
      if (b === preferredHost) return 1;
      const sa = hostState.find(function (x) {
        return x.host === a;
      });
      const sb = hostState.find(function (x) {
        return x.host === b;
      });
      const la = sa && sa.ok ? sa.latency : 99999;
      const lb = sb && sb.ok ? sb.latency : 99999;
      return la - lb;
    });
    return list;
  }

  async function loadCharset() {
    if (charset) return charset;
    const res = await fetch("charset.json");
    charset = await res.json();
    return charset;
  }

  function decodeContent(content, mode, table) {
    const lohi = CODE_RANGES[mode];
    const lo = lohi[0];
    const hi = lohi[1];
    let out = "";
    for (const char of content) {
      const uni = char.codePointAt(0);
      if (uni >= lo && uni <= hi) {
        const bias = uni - lo;
        if (bias >= 0 && bias < table.length && table[bias] !== "?") out += table[bias];
        else out += char;
      } else out += char;
    }
    return out;
  }

  function htmlToText(html) {
    let s = String(html || "")
      .replace(/<br\s*\/?>/gi, "\n")
      .replace(/<\/p\s*>/gi, "\n")
      .replace(/<[^>]+>/g, "");
    s = s
      .replace(/&nbsp;/g, " ")
      .replace(/&lt;/g, "<")
      .replace(/&gt;/g, ">")
      .replace(/&amp;/g, "&")
      .replace(/&quot;/g, '"')
      .replace(/&#(\d+);/g, function (_, n) {
        return String.fromCharCode(Number(n));
      })
      .replace(/&#x([0-9a-f]+);/gi, function (_, n) {
        return String.fromCharCode(parseInt(n, 16));
      });
    return s
      .split(/\r?\n/)
      .map(function (ln) {
        return ln.trim();
      })
      .filter(Boolean)
      .join("\n");
  }

  function pickMode(raw, tables) {
    const score = function (s) {
      let cjk = 0;
      let priv = 0;
      for (const c of s) {
        const code = c.codePointAt(0);
        if (code >= 0x4e00 && code <= 0x9fff) cjk++;
        if (
          (code >= 0xe000 && code <= 0xf8ff) ||
          (code >= 58344 && code <= 58716)
        )
          priv++;
      }
      return cjk * 2 - priv * 5;
    };
    const d0 = decodeContent(raw, 0, tables[0]);
    const d1 = decodeContent(raw, 1, tables[1] || tables[0]);
    return score(d0) >= score(d1) ? 0 : 1;
  }

  function decodeBody(raw) {
    if (!raw) return "";
    const tables = charset || [[], []];
    const mode = pickMode(raw, tables);
    return htmlToText(decodeContent(raw, mode, tables[mode] || tables[0]));
  }

  function aliveHosts() {
    const ok = hostState
      .filter(function (h) {
        return h.ok;
      })
      .sort(function (a, b) {
        return a.latency - b.latency;
      });
    if (ok.length) return ok.map(function (h) {
      return h.host;
    });
    return FIXED_HOSTS.slice();
  }

  function markOk(host, latency) {
    const n = hostState.find(function (x) {
      return x.host === host;
    });
    if (n) {
      n.ok = true;
      if (latency) n.latency = latency;
    }
  }

  function markFailSoft(host) {
    const n = hostState.find(function (x) {
      return x.host === host;
    });
    if (n) n.latency = Math.min(99999, (n.latency || 1000) + 2000);
  }

  function markFail(host) {
    const n = hostState.find(function (x) {
      return x.host === host;
    });
    if (n) n.ok = false;
  }

  async function probeHosts() {
    await loadCharset();
    await detectSameOriginProxy();
    const proxy = orderedProxies()[0];
    const hasCustom = !!readCustomProxyBase();
    // 只探 2 个节点，缩短首屏等待
    const sample = orderHosts(FIXED_HOSTS.slice()).slice(0, hasCustom ? 3 : 2);
    await Promise.all(
      sample.map(async function (host) {
        const n = hostState.find(function (x) {
          return x.host === host;
        });
        if (!n || !proxy) return;
        const t0 = Date.now();
        try {
          const data = await fetchViaProxy(
            proxy,
            host + "/content?item_id=" + PROBE_ITEM,
            hasCustom ? 8000 : 12000,
            null
          );
          const content =
            (data.data && (data.data.content || data.data.text)) || "";
          if (
            (data.code === 0 || data.code === "0" || data.code == null) &&
            String(content).length > 30
          ) {
            n.ok = true;
            n.latency = Date.now() - t0;
            rememberHost(host);
          } else {
            n.ok = false;
            n.latency = 9999;
          }
        } catch (e) {
          n.ok = false;
          n.latency = 9999;
        }
      })
    );
    if (!hostState.some(function (h) {
      return h.ok;
    })) {
      hostState.forEach(function (h) {
        h.ok = true;
        h.latency = 5000;
      });
    }
    return {
      total: hostState.length,
      alive: hostState.filter(function (h) {
        return h.ok;
      }).length,
      nodes: hostState.map(function (h) {
        return { host: h.host, ok: h.ok, latency: h.latency };
      }),
      proxy: preferredProxy,
      customProxy: readCustomProxyBase() || "",
    };
  }

  function parseSearch(data) {
    const books = [];
    const seen = new Set();
    const push = function (it) {
      if (!it || typeof it !== "object") return;
      if (Array.isArray(it.book_data) && it.book_data.length) {
        for (const bd of it.book_data) {
          push(
            Object.assign({}, bd, {
              book_id: bd.book_id || it.book_id || it.search_result_id,
            })
          );
        }
        return;
      }
      const book_id = String(
        it.book_id || it.search_result_id || it.bookId || ""
      );
      if (!/^\d{10,}$/.test(book_id) || seen.has(book_id)) return;
      const title = it.book_name || it.title || "";
      if (!title && !it.author) return;
      seen.add(book_id);
      books.push({
        book_id: book_id,
        title: title,
        author: it.author || "",
        abstract: it.abstract || "",
        thumb_url:
          it.thumb_uri ||
          it.audio_thumb_uri ||
          it.thumb_url ||
          it.cover_url ||
          "",
        score: it.score || "",
        category: it.category || "",
      });
    };
    const tabs = data.search_tabs || [];
    const bookTab =
      tabs.find(function (t) {
        return t && (t.title === "书籍" || t.tab_type === 3);
      }) || tabs[0];
    if (bookTab && Array.isArray(bookTab.data)) {
      for (const cell of bookTab.data) push(cell);
      if (books.length) return books;
    }
    const walk = function (node, depth) {
      if (!node || depth > 8) return;
      if (Array.isArray(node)) {
        for (const x of node) walk(x, depth + 1);
        return;
      }
      if (typeof node === "object") {
        if (node.book_data || node.book_name || node.book_id) push(node);
        for (const v of Object.values(node)) walk(v, depth + 1);
      }
    };
    walk(data, 0);
    return books;
  }

  function getSearchCache(query) {
    try {
      const raw = localStorage.getItem(SEARCH_CACHE_PREFIX + query);
      if (!raw) return null;
      const obj = JSON.parse(raw);
      if (!obj || !obj.books || !obj.t) return null;
      if (Date.now() - obj.t > 30 * 60 * 1000) return null;
      return obj.books;
    } catch (e) {
      return null;
    }
  }

  function setSearchCache(query, books) {
    try {
      localStorage.setItem(
        SEARCH_CACHE_PREFIX + query,
        JSON.stringify({ t: Date.now(), books: books })
      );
    } catch (e) {
      /* ignore */
    }
  }

  async function search(query) {
    const cached = getSearchCache(query);
    if (cached && cached.length) {
      return { code: 0, books: cached, source: "cache", proxy: preferredProxy };
    }
    await detectSameOriginProxy();
    try {
      const raced = await raceHosts(
        function (host) {
          return (
            host +
            "/search?query=" +
            encodeURIComponent(query) +
            "&page=0"
          );
        },
        {
          timeout: readCustomProxyBase() ? 12000 : 20000,
          hosts: FIXED_HOSTS.slice(),
          ok: function (data) {
            if (data.code !== 0 && data.code !== "0") return false;
            return parseSearch(data).length > 0;
          },
        }
      );
      const books = parseSearch(raced.data);
      setSearchCache(query, books);
      return {
        code: 0,
        books: books,
        source: raced.host,
        proxy: raced.proxy,
      };
    } catch (e) {
      const hint = readCustomProxyBase()
        ? e.message || String(e)
        : (e.message || String(e)) +
          "。公共 CORS 拥堵时请部署 cors-worker.js（Cloudflare 免费）并 localStorage.setItem('fq_cors_proxy', 'https://你的.workers.dev')";
      return { code: -1, message: "搜索失败: " + hint, books: [] };
    }
  }

  async function getInfo(bookId) {
    try {
      const raced = await raceHosts(
        function (host) {
          return host + "/info?book_id=" + bookId;
        },
        {
          timeout: 15000,
          ok: function (data) {
            const d = data.data || data;
            return !!(d && (d.book_name || d.title || d.book_id));
          },
        }
      );
      const d = raced.data.data || raced.data;
      return {
        book_id: bookId,
        title: d.book_name || d.title || "小说" + bookId,
        author: d.author || d.author_name || "未知",
        abstract: d.book_abstract_v2 || d.book_abstract || d.abstract || "",
      };
    } catch (e) {
      return {
        book_id: bookId,
        title: "小说" + bookId,
        author: "未知",
        abstract: "",
      };
    }
  }

  function extractCatalog(data) {
    const d = data.data || data || {};
    return d.item_data_list || d.itemDataList || d.chapter_list || [];
  }

  function extractContent(data, itemId) {
    if (!(data.code === 0 || data.code === "0" || data.code == null)) {
      return null;
    }
    let payload = data.data || {};
    if (payload && typeof payload === "object" && payload[itemId]) {
      payload = payload[itemId];
    }
    let raw = "";
    let title = "";
    if (payload && typeof payload === "object") {
      raw = payload.content || payload.text || "";
      title = payload.title || payload.chapter_title || "";
    } else if (typeof payload === "string") {
      raw = payload;
    }
    if (!raw || raw.length < 30) return null;
    return { raw: raw, title: title };
  }

  async function getCatalog(bookId) {
    try {
      const raced = await raceHosts(
        function (host) {
          return host + "/catalog?book_id=" + bookId;
        },
        {
          timeout: 20000,
          ok: function (data) {
            const list = extractCatalog(data);
            return Array.isArray(list) && list.length > 0;
          },
        }
      );
      const list = extractCatalog(raced.data);
      return list.map(function (ch) {
        return {
          item_id: String(ch.item_id || ch.itemId || ch.id || ""),
          title: ch.title || "",
        };
      });
    } catch (e) {
      throw new Error("目录获取失败: " + (e.message || String(e)));
    }
  }

  async function getContent(itemId) {
    try {
      const hosts = orderHosts(
        aliveHosts().length ? aliveHosts() : FIXED_HOSTS.slice()
      ).slice(0, 3);
      const raced = await raceHosts(
        function (host) {
          return host + "/content?item_id=" + itemId;
        },
        {
          timeout: 14000,
          hosts: hosts,
          ok: function (data) {
            return !!extractContent(data, itemId);
          },
        }
      );
      const got = extractContent(raced.data, itemId);
      await loadCharset();
      const text = decodeBody(got.raw);
      if (text.length < 30) throw new Error("decode short");
      return { title: got.title, text: text };
    } catch (e) {
      throw new Error(e.message || "正文获取失败");
    }
  }

  function cacheKey(bookId, itemId) {
    return CACHE_PREFIX + bookId + "_" + itemId;
  }

  function loadCache(bookId, itemId) {
    try {
      const raw = localStorage.getItem(cacheKey(bookId, itemId));
      if (!raw) return null;
      const data = JSON.parse(raw);
      if (data && data.text && data.text.length > 20) return data;
    } catch (e) {
      /* ignore */
    }
    return null;
  }

  function saveCache(bookId, itemId, payload) {
    try {
      localStorage.setItem(
        cacheKey(bookId, itemId),
        JSON.stringify({
          title: payload.title || "",
          text: payload.text || "",
          t: Date.now(),
        })
      );
    } catch (e) {
      /* quota */
    }
  }

  function safeName(name) {
    return (
      String(name || "novel")
        .replace(/[\\/:*?"<>|]+/g, "_")
        .trim()
        .slice(0, 80) || "novel"
    );
  }

  function requestCancel() {
    cancelFlag = true;
  }

  function resetCancel() {
    cancelFlag = false;
  }

  async function downloadBook(opts) {
    resetCancel();
    await loadCharset();
    const bookId = opts.bookId;
    const resume = opts.resume !== false;
    const onProgress = opts.onProgress || function () {};

    onProgress(2, "获取书籍信息...");
    const meta = await getInfo(bookId);
    if (cancelFlag) throw new Error("任务已取消");

    onProgress(5, "获取目录...");
    let chapters = await getCatalog(bookId);
    const start = Math.max(1, Number(opts.start_chapter || 0) || 0);
    const end = Math.max(0, Number(opts.end_chapter || 0) || 0);
    if (start > 1 || end > 0) {
      const from = start > 0 ? start - 1 : 0;
      const to = end > 0 ? end : chapters.length;
      chapters = chapters.slice(from, to);
    }
    const total = chapters.length;
    if (!total) throw new Error("章节列表为空");

    const results = new Array(total);
    let done = 0;
    let cached = 0;
    const errors = [];
    // 公共代理并发过高会全挂；有自建代理可 3，否则 1
    const concurrency = readCustomProxyBase() ? 3 : 1;

    async function worker(idx) {
      if (cancelFlag) return;
      const ch = chapters[idx];
      let title = ch.title || "第" + (idx + 1) + "章";
      try {
        if (resume) {
          const c = loadCache(bookId, ch.item_id);
          if (c) {
            results[idx] = [c.title || title, c.text];
            cached++;
            return;
          }
        }
        const got = await getContent(ch.item_id);
        if (cancelFlag) return;
        if (got.title) title = got.title;
        results[idx] = [title, got.text];
        if (resume) saveCache(bookId, ch.item_id, { title: title, text: got.text });
      } catch (e) {
        results[idx] = [title, "【本章获取失败: " + (e.message || e) + "】"];
        errors.push(String(e.message || e));
      } finally {
        done++;
        const pct = 5 + Math.floor((done / total) * 90);
        onProgress(
          pct,
          "下载中 " +
            done +
            "/" +
            total +
            (cached ? "（缓存 " + cached + "）" : "")
        );
      }
    }

    let next = 0;
    async function runPool() {
      const runners = [];
      for (let i = 0; i < concurrency; i++) {
        runners.push(
          (async function () {
            while (!cancelFlag) {
              const idx = next++;
              if (idx >= total) break;
              await worker(idx);
            }
          })()
        );
      }
      await Promise.all(runners);
    }

    await runPool();

    const lines = [
      meta.title,
      "作者：" + meta.author,
      "书籍ID：" + bookId,
      "章节数：" + total,
      "模式：GitHub 静态 / 浏览器直连",
    ];
    if (meta.abstract) lines.push("简介：" + String(meta.abstract).slice(0, 500));
    lines.push("", "=".repeat(40), "");
    for (const item of results) {
      if (!item) continue;
      lines.push(item[0], "", item[1], "", "-".repeat(30), "");
    }
    const content = lines.join("\n");
    const filename =
      safeName(meta.title) +
      "-" +
      bookId +
      (cancelFlag ? "-partial" : "") +
      ".txt";
    const blob = new Blob([content], { type: "text/plain;charset=utf-8" });
    const url = URL.createObjectURL(blob);

    if (cancelFlag) {
      onProgress(
        Math.min(99, 5 + Math.floor((done / total) * 90)),
        "已取消（可再次下载续传）"
      );
      return {
        status: "cancelled",
        filename: filename,
        url: url,
        title: meta.title,
        author: meta.author,
        done: done,
        total: total,
        cached: cached,
        message: "已取消（已完成 " + done + "/" + total + " 章）",
        error_count: errors.length,
      };
    }

    onProgress(
      100,
      cached ? "下载完成（其中 " + cached + " 章来自缓存）" : "下载完成"
    );
    return {
      status: "done",
      filename: filename,
      url: url,
      title: meta.title,
      author: meta.author,
      done: total,
      total: total,
      cached: cached,
      message: cached
        ? "下载完成（其中 " + cached + " 章来自缓存）"
        : "下载完成",
      error_count: errors.length,
    };
  }

  global.FanqieBrowserClient = {
    FIXED_HOSTS: FIXED_HOSTS,
    detectSameOriginProxy: detectSameOriginProxy,
    probeHosts: probeHosts,
    search: search,
    getInfo: getInfo,
    getCatalog: getCatalog,
    downloadBook: downloadBook,
    requestCancel: requestCancel,
    resetCancel: resetCancel,
    setCorsProxy: function (base) {
      try {
        if (base) localStorage.setItem(LS_PROXY, String(base).replace(/\/$/, ""));
        else localStorage.removeItem(LS_PROXY);
      } catch (e) {
        /* ignore */
      }
    },
    getCorsProxy: readCustomProxyBase,
    nodesSnapshot: function () {
      return {
        total: hostState.length,
        alive: hostState.filter(function (h) {
          return h.ok;
        }).length,
        nodes: hostState.slice(),
        proxy: preferredProxy,
        customProxy: readCustomProxyBase() || "",
      };
    },
  };
})(typeof window !== "undefined" ? window : globalThis);
