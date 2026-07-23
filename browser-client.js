/**
 * GitHub Pages / 纯静态模式：浏览器直连第三方番茄 API（固定 5 节点 + CORS 代理）
 * 有 Node 后端时 index.html 不会调用本模块。
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
  const PROBE_ITEM = "7580458932431225368";

  let charset = null;
  let hostIdx = 0;
  /** @type {{host:string,ok:boolean,latency:number}[]} */
  let hostState = FIXED_HOSTS.map((h) => ({ host: h, ok: true, latency: 9999 }));
  let cancelFlag = false;
  /** 最近成功的代理 id，下次优先 */
  let preferredProxy = "allorigins-get";

  /**
   * 公共 CORS 代理（纯静态站必需）。
   * 节点本身无 ACAO，浏览器不能直连；优先 allorigins /get（目前最稳）。
   */
  const PROXY_DEFS = [
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
    },
    {
      id: "allorigins-raw",
      build: function (url) {
        return "https://api.allorigins.win/raw?url=" + encodeURIComponent(url);
      },
      parse: function (text) {
        return JSON.parse(text || "{}");
      },
    },
    {
      id: "corsproxy-io",
      build: function (url) {
        return "https://corsproxy.io/?" + encodeURIComponent(url);
      },
      parse: function (text) {
        return JSON.parse(text || "{}");
      },
    },
  ];

  function orderedProxies() {
    return PROXY_DEFS.slice().sort(function (a, b) {
      if (a.id === preferredProxy) return -1;
      if (b.id === preferredProxy) return 1;
      return 0;
    });
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
        preferredProxy = proxy.id;
        return data;
      })
      .finally(function () {
        clearTimeout(timer);
      });
  }

  /** 同一目标 URL：多代理竞速，先成功者胜 */
  async function fetchJson(url, timeout) {
    timeout = timeout || 14000;
    const proxies = orderedProxies();
    const ctrl = new AbortController();
    let lastErr = null;
    let settled = false;

    return new Promise(function (resolve, reject) {
      let pending = proxies.length;
      if (!pending) {
        reject(new Error("无可用 CORS 代理"));
        return;
      }
      proxies.forEach(function (proxy) {
        fetchViaProxy(proxy, url, timeout, ctrl.signal)
          .then(function (data) {
            if (settled) return;
            settled = true;
            ctrl.abort();
            resolve(data);
          })
          .catch(function (e) {
            lastErr = e;
            pending--;
            if (!settled && pending <= 0) {
              reject(lastErr || new Error("CORS proxy failed"));
            }
          });
      });
    });
  }

  function raceWithProxies(pathBuilder, hosts, proxies, timeout, ok) {
    const ctrl = new AbortController();
    let lastErr = null;
    let pending = 0;
    let settled = false;

    return new Promise(function (resolve, reject) {
      hosts.forEach(function (host) {
        proxies.forEach(function (proxy) {
          pending++;
          fetchViaProxy(proxy, pathBuilder(host), timeout, ctrl.signal)
            .then(function (data) {
              if (settled) return;
              if (!ok(data, host)) {
                lastErr = new Error("节点无有效数据: " + host);
                return;
              }
              settled = true;
              ctrl.abort();
              markOk(host, 0);
              resolve({ data: data, host: host, proxy: proxy.id });
            })
            .catch(function (e) {
              lastErr = e;
            })
            .finally(function () {
              pending--;
              if (!settled && pending <= 0) {
                reject(lastErr || new Error("全部节点失败"));
              }
            });
        });
      });
      if (!pending) reject(new Error("无节点"));
    });
  }

  /**
   * 多节点竞速：先只走「最近成功」的代理（少打慢代理），失败再全代理兜底
   */
  async function raceHosts(pathBuilder, opts) {
    opts = opts || {};
    const timeout = opts.timeout || 14000;
    const hosts = opts.hosts || pickHosts();
    const ok =
      opts.ok ||
      function () {
        return true;
      };
    const ordered = orderedProxies();
    try {
      return await raceWithProxies(pathBuilder, hosts, [ordered[0]], timeout, ok);
    } catch (e1) {
      if (ordered.length <= 1) throw e1;
      return raceWithProxies(
        pathBuilder,
        hosts,
        ordered.slice(1),
        timeout,
        ok
      );
    }
  }

  async function loadCharset() {
    if (charset) return charset;
    const res = await fetch("charset.json");
    charset = await res.json();
    return charset;
  }

  function decodeContent(content, mode, table) {
    const [lo, hi] = CODE_RANGES[mode];
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
      .replace(/&#(\d+);/g, (_, n) => String.fromCharCode(Number(n)))
      .replace(/&#x([0-9a-f]+);/gi, (_, n) => String.fromCharCode(parseInt(n, 16)));
    return s
      .split(/\r?\n/)
      .map((ln) => ln.trim())
      .filter(Boolean)
      .join("\n");
  }

  function pickMode(raw, tables) {
    const score = (s) => {
      let cjk = 0,
        priv = 0;
      for (const c of s) {
        const code = c.codePointAt(0);
        if (code >= 0x4e00 && code <= 0x9fff) cjk++;
        if ((code >= 0xe000 && code <= 0xf8ff) || (code >= 58344 && code <= 58716)) priv++;
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
    const ok = hostState.filter((h) => h.ok).sort((a, b) => a.latency - b.latency);
    if (ok.length) return ok.map((h) => h.host);
    return FIXED_HOSTS.slice();
  }

  function pickHosts() {
    const hosts = aliveHosts();
    const start = hostIdx % hosts.length;
    hostIdx++;
    return hosts.slice(start).concat(hosts.slice(0, start));
  }

  function markOk(host, latency) {
    const n = hostState.find((x) => x.host === host);
    if (n) {
      n.ok = true;
      n.latency = latency || n.latency;
    }
  }

  function markFail(host) {
    const n = hostState.find((x) => x.host === host);
    if (n) n.ok = false;
  }

  async function probeHosts() {
    await loadCharset();
    // 首屏探活：只走首选代理 + 短超时，失败则默认全节点可用（避免卡死）
    const proxy = orderedProxies()[0];
    await Promise.all(
      hostState.map(async function (n) {
        const t0 = Date.now();
        try {
          const data = await fetchViaProxy(
            proxy,
            n.host + "/content?item_id=" + PROBE_ITEM,
            7000,
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
          } else {
            n.ok = false;
            n.latency = 9999;
          }
        } catch {
          n.ok = false;
          n.latency = 9999;
        }
      })
    );
    if (!hostState.some(function (h) { return h.ok; })) {
      hostState.forEach(function (h) {
        h.ok = true;
        h.latency = 5000;
      });
    }
    return {
      total: hostState.length,
      alive: hostState.filter((h) => h.ok).length,
      nodes: hostState.map((h) => ({
        host: h.host,
        ok: h.ok,
        latency: h.latency,
      })),
      proxy: preferredProxy,
    };
  }

  function parseSearch(data) {
    const books = [];
    const seen = new Set();
    const push = (it) => {
      if (!it || typeof it !== "object") return;
      if (Array.isArray(it.book_data) && it.book_data.length) {
        for (const bd of it.book_data) {
          push({ ...bd, book_id: bd.book_id || it.book_id || it.search_result_id });
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
        book_id,
        title,
        author: it.author || "",
        abstract: it.abstract || "",
        thumb_url: it.thumb_url || it.thumb_uri || "",
        score: it.score || "",
        category: it.category || "",
      });
    };
    const tabs = data.search_tabs || [];
    const bookTab =
      tabs.find((t) => t && (t.title === "书籍" || t.tab_type === 3)) || tabs[0];
    if (bookTab && Array.isArray(bookTab.data)) {
      for (const cell of bookTab.data) push(cell);
      if (books.length) return books;
    }
    const walk = (node, depth) => {
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

  async function search(query) {
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
          timeout: 16000,
          hosts: FIXED_HOSTS.slice(),
          ok: function (data) {
            if (data.code !== 0 && data.code !== "0") return false;
            return parseSearch(data).length > 0;
          },
        }
      );
      const books = parseSearch(raced.data);
      return {
        code: 0,
        books: books,
        source: raced.host,
        proxy: raced.proxy,
      };
    } catch (e) {
      return {
        code: -1,
        message: "搜索失败: " + (e.message || String(e)),
        books: [],
      };
    }
  }

  async function getInfo(bookId) {
    try {
      const raced = await raceHosts(
        function (host) {
          return host + "/info?book_id=" + bookId;
        },
        {
          timeout: 12000,
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
    } catch {
      /* fallthrough sequential */
    }
    for (const host of pickHosts()) {
      try {
        const data = await fetchJson(host + "/info?book_id=" + bookId, 12000);
        const d = data.data || data;
        if (d && (d.book_name || d.title || d.book_id)) {
          markOk(host, 0);
          return {
            book_id: bookId,
            title: d.book_name || d.title || "小说" + bookId,
            author: d.author || d.author_name || "未知",
            abstract: d.book_abstract_v2 || d.book_abstract || d.abstract || "",
          };
        }
      } catch {
        markFail(host);
      }
    }
    return { book_id: bookId, title: "小说" + bookId, author: "未知", abstract: "" };
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
          timeout: 18000,
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
      const raced = await raceHosts(
        function (host) {
          return host + "/content?item_id=" + itemId;
        },
        {
          timeout: 14000,
          hosts: (aliveHosts().length ? aliveHosts() : FIXED_HOSTS.slice()).slice(0, 4),
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
    } catch {
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
    } catch {
      /* quota */
    }
  }

  function safeName(name) {
    return String(name || "novel")
      .replace(/[\\/:*?"<>|]+/g, "_")
      .trim()
      .slice(0, 80) || "novel";
  }

  function requestCancel() {
    cancelFlag = true;
  }

  function resetCancel() {
    cancelFlag = false;
  }

  /**
   * @param {object} opts
   * @param {string} opts.bookId
   * @param {number} [opts.start_chapter]
   * @param {number} [opts.end_chapter]
   * @param {boolean} [opts.resume]
   * @param {(pct:number,msg:string)=>void} [opts.onProgress]
   */
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
    const concurrency = 3;

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
        if (resume) saveCache(bookId, ch.item_id, { title, text: got.text });
      } catch (e) {
        results[idx] = [title, "【本章获取失败: " + (e.message || e) + "】"];
        errors.push(String(e.message || e));
      } finally {
        done++;
        const pct = 5 + Math.floor((done / total) * 90);
        onProgress(
          pct,
          "下载中 " + done + "/" + total + (cached ? "（缓存 " + cached + "）" : "")
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
    const filename = safeName(meta.title) + "-" + bookId + (cancelFlag ? "-partial" : "") + ".txt";
    const blob = new Blob([content], { type: "text/plain;charset=utf-8" });
    const url = URL.createObjectURL(blob);

    if (cancelFlag) {
      onProgress(Math.min(99, 5 + Math.floor((done / total) * 90)), "已取消（可再次下载续传）");
      return {
        status: "cancelled",
        filename,
        url,
        title: meta.title,
        author: meta.author,
        done,
        total,
        cached,
        message: "已取消（已完成 " + done + "/" + total + " 章）",
        error_count: errors.length,
      };
    }

    onProgress(100, cached ? "下载完成（其中 " + cached + " 章来自缓存）" : "下载完成");
    return {
      status: "done",
      filename,
      url,
      title: meta.title,
      author: meta.author,
      done: total,
      total,
      cached,
      message: cached ? "下载完成（其中 " + cached + " 章来自缓存）" : "下载完成",
      error_count: errors.length,
    };
  }

  global.FanqieBrowserClient = {
    FIXED_HOSTS,
    probeHosts,
    search,
    getInfo,
    getCatalog,
    downloadBook,
    requestCancel,
    resetCancel,
    nodesSnapshot() {
      return {
        total: hostState.length,
        alive: hostState.filter((h) => h.ok).length,
        nodes: hostState.slice(),
      };
    },
  };
})(typeof window !== "undefined" ? window : globalThis);
