#!/usr/bin/env node
"use strict";

const http = require("http");
const https = require("https");
const fs = require("fs");
const path = require("path");
const { URL } = require("url");
const { randomUUID } = require("crypto");

const ROOT = __dirname;
const CHARSET_PATH = path.join(ROOT, "charset.json");
const INDEX_PATH = path.join(ROOT, "index.html");
const DOWNLOADS_DIR = path.join(ROOT, "downloads");
const CACHE_DIR = path.join(DOWNLOADS_DIR, "cache");
const PORT = Number(process.env.PORT || 8787);
const HOST = process.env.HOST || "0.0.0.0";
const MAX_WORKERS = Math.max(1, Number(process.env.MAX_WORKERS || 6));
const CHAPTER_DELAY = Number(process.env.CHAPTER_DELAY || 0.05) * 1000;
const NODE_PROBE_INTERVAL = Number(process.env.NODE_PROBE_INTERVAL || 5 * 60 * 1000);
const NODE_DISCOVER_INTERVAL = Number(process.env.NODE_DISCOVER_INTERVAL || 30 * 60 * 1000);
const PROBE_ITEM_ID = process.env.PROBE_ITEM_ID || "7580458932431225368";

const UA =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
  "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

const SEARCH_URLS = [
  "https://api-lf.fanqiesdk.com/api/novel/channel/homepage/search/search/v1/",
  "https://novel.snssdk.com/api/novel/channel/homepage/search/search/v1/",
];

const DEFAULT_SEED_HOSTS = [
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

const CHARSET = JSON.parse(fs.readFileSync(CHARSET_PATH, "utf8"));
fs.mkdirSync(DOWNLOADS_DIR, { recursive: true });
fs.mkdirSync(CACHE_DIR, { recursive: true });

/** @type {Map<string, any>} */
const JOBS = new Map();

let cookieState = { value: null, ts: 0 };
let contentHostIdx = 0;

/** @type {Map<string, {host:string,ok:boolean,latency:number,lastCheck:number,failCount:number,source:string,msg:string}>} */
const NODE_STATE = new Map();

function seedHostsFromEnv() {
  const raw = process.env.CONTENT_API_HOSTS || DEFAULT_SEED_HOSTS.join(",");
  return raw
    .split(",")
    .map((h) => normalizeHost(h))
    .filter(Boolean);
}

function normalizeHost(h) {
  if (!h) return "";
  let s = String(h).trim().replace(/\/+$/, "");
  if (!s) return "";
  if (!/^https?:\/\//i.test(s)) s = "http://" + s;
  try {
    const u = new URL(s);
    if (!u.hostname) return "";
    // drop default :80
    if (u.port === "80" && u.protocol === "http:") {
      return `http://${u.hostname}`;
    }
    return `${u.protocol}//${u.host}`;
  } catch {
    return "";
  }
}

function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

function httpGet(url, headers = {}, timeout = 20000) {
  return new Promise((resolve, reject) => {
    let u;
    try {
      u = new URL(url);
    } catch (e) {
      reject(e);
      return;
    }
    const lib = u.protocol === "https:" ? https : http;
    const req = lib.request(
      {
        protocol: u.protocol,
        hostname: u.hostname,
        port: u.port || (u.protocol === "https:" ? 443 : 80),
        path: u.pathname + u.search,
        method: "GET",
        headers: {
          "User-Agent": UA,
          Accept: "*/*",
          "Accept-Encoding": "identity",
          Referer: "https://fanqienovel.com/",
          ...headers,
        },
        timeout,
      },
      (res) => {
        const chunks = [];
        res.on("data", (c) => chunks.push(c));
        res.on("end", () => {
          resolve({
            status: res.statusCode || 0,
            headers: res.headers,
            body: Buffer.concat(chunks),
          });
        });
      }
    );
    req.on("timeout", () => {
      req.destroy(new Error("timeout"));
    });
    req.on("error", reject);
    req.end();
  });
}

function decodeContent(content, mode = 0) {
  const [lo, hi] = CODE_RANGES[mode];
  const table = CHARSET[mode] || [];
  let out = "";
  for (const char of content) {
    const uni = char.codePointAt(0);
    if (uni >= lo && uni <= hi) {
      const bias = uni - lo;
      if (bias >= 0 && bias < table.length && table[bias] !== "?") {
        out += table[bias];
      } else {
        out += char;
      }
    } else {
      out += char;
    }
  }
  return out;
}

function htmlToText(html) {
  let s = html.replace(/<br\s*\/?>/gi, "\n").replace(/<\/p\s*>/gi, "\n");
  s = s.replace(/<[^>]+>/g, "");
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

function pickDecodeMode(content) {
  const score = (s) => {
    let cjk = 0;
    let priv = 0;
    for (const c of s) {
      const code = c.codePointAt(0);
      if (code >= 0x4e00 && code <= 0x9fff) cjk++;
      if ((code >= 0xe000 && code <= 0xf8ff) || (code >= 58344 && code <= 58716)) priv++;
    }
    return cjk * 2 - priv * 5;
  };
  const d0 = decodeContent(content, 0);
  const d1 = decodeContent(content, 1);
  return score(d0) >= score(d1) ? 0 : 1;
}

function extractContentFromReaderHtml(html) {
  const meta = { locked: false, word_number: 0, preview_only: false };
  meta.locked = /"isChapterLock"\s*:\s*true/.test(html);
  const wn = html.match(/"chapterWordNumber"\s*:\s*"?(\d+)"?/);
  if (wn) meta.word_number = Number(wn[1]);

  let title = "";
  let mt = html.match(/muye-reader-title[^>]*>([\s\S]*?)<\//);
  if (mt) title = mt[1].replace(/<[^>]+>/g, "").trim();
  if (!title) {
    mt = html.match(/"chapterData"\s*:\s*\{[^}]*?"title"\s*:\s*"([^"]+)"/);
    if (mt) title = mt[1];
  }
  if (!title) {
    mt = html.match(/"title"\s*:\s*"(第[^"]+)"/);
    if (mt) title = mt[1];
  }

  let raw = "";
  const m = html.match(/"content"\s*:\s*"((?:\\.|[^"\\])*)"/);
  if (m) {
    try {
      raw = JSON.parse('"' + m[1] + '"');
    } catch {
      try {
        raw = m[1].replace(/\\u([0-9a-fA-F]{4})/g, (_, h) =>
          String.fromCharCode(parseInt(h, 16))
        );
      } catch {
        raw = m[1];
      }
    }
  }
  if (!raw) {
    const parts = html.match(
      /class="[^"]*muye-reader-content[^"]*"[^>]*>([\s\S]*?)<\/div>/
    );
    if (parts) {
      const paras = [...parts[1].matchAll(/<p[^>]*>([\s\S]*?)<\/p>/g)].map((p) =>
        p[1].replace(/<[^>]+>/g, "").trim()
      );
      raw = paras.join("\n");
    }
  }
  if (!raw) return { title, text: "", meta };

  const mode = pickDecodeMode(raw);
  const text = htmlToText(decodeContent(raw, mode));
  if (
    meta.locked ||
    (meta.word_number > 800 && text.length < Math.max(300, meta.word_number * 0.25))
  ) {
    meta.preview_only = true;
  }
  return { title, text, meta };
}

function getCookie(force = false) {
  if (!force && cookieState.value && Date.now() - cookieState.ts < 3600_000) {
    return cookieState.value;
  }
  const lo = 6 * 10 ** 18;
  const hi = 9 * 10 ** 18 - 1;
  const n = BigInt(lo) + BigInt(Math.floor(Math.random() * Number(hi - lo)));
  cookieState = { value: `novel_web_id=${n.toString()}`, ts: Date.now() };
  return cookieState.value;
}

function aliveHosts() {
  const ok = [...NODE_STATE.values()]
    .filter((n) => n.ok)
    .sort((a, b) => a.latency - b.latency)
    .map((n) => n.host);
  if (ok.length) return ok;
  // fallback seeds even if probe stale
  return seedHostsFromEnv();
}

function pickContentHosts() {
  const hosts = aliveHosts();
  if (!hosts.length) return [];
  const start = contentHostIdx % hosts.length;
  contentHostIdx += 1;
  return hosts.slice(start).concat(hosts.slice(0, start));
}

async function fetchChapterViaThirdParty(itemId) {
  let lastErr = null;
  for (const host of pickContentHosts()) {
    const url = `${host}/content?item_id=${itemId}`;
    try {
      const res = await httpGet(url, { Accept: "application/json", Referer: host + "/" }, 18000);
      if (!res.body || !res.body.length) {
        lastErr = "empty body";
        markHostFail(host, lastErr);
        continue;
      }
      const data = JSON.parse(res.body.toString("utf8") || "{}");
      if (!(data.code === 0 || data.code === "0" || data.code == null)) {
        lastErr = data.msg || data.message || String(data.code);
        markHostFail(host, lastErr);
        continue;
      }
      let payload = data.data || {};
      if (payload && typeof payload === "object" && payload[itemId]) {
        payload = payload[itemId] || {};
      }
      let raw = "";
      let title = "";
      if (payload && typeof payload === "object") {
        raw = payload.content || payload.text || "";
        title = payload.title || payload.chapter_title || "";
      } else if (typeof payload === "string") {
        raw = payload;
      }
      if (!raw || raw.length < 30) {
        lastErr = "short content";
        markHostFail(host, lastErr);
        continue;
      }
      const mode = pickDecodeMode(raw);
      const text = htmlToText(decodeContent(raw, mode));
      if (text.length < 30) {
        lastErr = "decode short";
        continue;
      }
      markHostOk(host, 0);
      return { title, text };
    } catch (e) {
      lastErr = String(e.message || e);
      markHostFail(host, lastErr);
    }
  }
  throw new Error(lastErr || "第三方正文接口全部失败");
}

async function fetchChapterViaWeb(itemId, bookId = "") {
  let cookie = getCookie();
  const url = `https://fanqienovel.com/reader/${itemId}`;
  let lastErr = null;
  for (let attempt = 0; attempt < 3; attempt++) {
    try {
      const res = await httpGet(
        url,
        {
          Accept: "text/html,application/xhtml+xml",
          Cookie: cookie,
          Referer: bookId
            ? `https://fanqienovel.com/page/${bookId}`
            : "https://fanqienovel.com/",
        },
        20000
      );
      const html = res.body.toString("utf8");
      const { title, text, meta } = extractContentFromReaderHtml(html);
      if (text.length > 20) return { title, text, meta };
      cookie = getCookie(true);
      lastErr = "empty content";
    } catch (e) {
      lastErr = String(e.message || e);
      await sleep(300 * (attempt + 1));
    }
  }
  throw new Error(`网页 reader 失败: ${lastErr}`);
}

async function fetchChapter(itemId, bookId = "") {
  try {
    const { title, text } = await fetchChapterViaThirdParty(itemId);
    return {
      title,
      text,
      meta: { locked: false, preview_only: false, source: "third_party" },
    };
  } catch {
    // fall through
  }
  const { title, text, meta } = await fetchChapterViaWeb(itemId, bookId);
  meta.source = "web";
  if (meta.preview_only) {
    const tip =
      `\n\n【本章为网页端试读/锁定章节，仅获取到部分正文` +
      `（约 ${text.length} 字` +
      `${meta.word_number ? "，官方字数 " + meta.word_number : ""}）。` +
      `第三方正文接口暂不可用。】`;
    return { title, text: text + tip, meta };
  }
  return { title, text, meta };
}

async function searchBooks(query, offset = 0) {
  const q = encodeURIComponent(query);
  let lastErr = null;

  // prefer healthy third-party search (no Origin issues)
  for (const host of pickContentHosts()) {
    try {
      const url = `${host}/search?query=${q}&page=${Math.floor(offset / 10)}`;
      const res = await httpGet(url, { Accept: "application/json" }, 15000);
      const data = JSON.parse(res.body.toString("utf8") || "{}");
      if (data.code !== 0 && data.code !== "0") {
        lastErr = data.message || data.msg || String(data.code);
        continue;
      }
      const books = parseThirdPartySearch(data);
      if (books.length) {
        markHostOk(host, 0);
        return {
          code: 0,
          query,
          offset,
          next_offset: offset + books.length,
          has_more: books.length >= 10,
          books,
          source: host,
        };
      }
    } catch (e) {
      lastErr = String(e.message || e);
      markHostFail(host, lastErr);
    }
  }

  for (const base of SEARCH_URLS) {
    const url = `${base}?aid=1967&offset=${offset}&q=${q}`;
    try {
      const res = await httpGet(url, { Accept: "application/json" }, 15000);
      const data = JSON.parse(res.body.toString("utf8") || "{}");
      if (data.code !== 0) {
        lastErr = data.message || String(data.code);
        continue;
      }
      const ret = (data.data && data.data.ret_data) || [];
      const books = ret.map((it) => ({
        book_id: String(it.book_id || ""),
        title: it.title || "",
        author: it.author || "",
        abstract: it.abstract || "",
        thumb_url: it.thumb_url || "",
        score: it.score || "",
        category: it.category || "",
        creation_status: it.creation_status,
      }));
      return {
        code: 0,
        query,
        offset,
        next_offset: (data.data && data.data.offset) || offset + books.length,
        has_more: Boolean(data.data && data.data.has_more),
        books,
        source: "official",
      };
    } catch (e) {
      lastErr = String(e.message || e);
    }
  }
  return { code: -1, message: `搜索失败: ${lastErr}`, books: [] };
}

function parseThirdPartySearch(data) {
  const books = [];
  const seen = new Set();
  const push = (it) => {
    if (!it || typeof it !== "object") return;
    // nested book_data: [{ book_name, author, ... }]
    if (Array.isArray(it.book_data) && it.book_data.length) {
      for (const bd of it.book_data) {
        push({
          ...bd,
          book_id: bd.book_id || it.book_id || it.search_result_id,
        });
      }
      return;
    }
    const book_id = String(
      it.book_id || it.bookId || it.search_result_id || it.novel_id || it.novelId || ""
    );
    if (!/^\d{10,}$/.test(book_id) || seen.has(book_id)) return;
    const title = it.book_name || it.title || it.name || "";
    if (!title && !it.author) return;
    seen.add(book_id);
    books.push({
      book_id,
      title,
      author: it.author || it.author_name || "",
      abstract: it.abstract || it.book_abstract || it.desc || it.intro || "",
      thumb_url: it.thumb_url || it.cover || it.thumb_url_hd || it.thumb_uri || "",
      score: it.score || it.grade || "",
      category: it.category || it.category_v2 || "",
      creation_status: it.creation_status,
    });
  };

  // prefer 书籍 tab
  const tabs = data.search_tabs || data.data?.search_tabs;
  if (Array.isArray(tabs)) {
    const bookTab =
      tabs.find((t) => t && (t.title === "书籍" || t.tab_type === 3)) || tabs[0];
    if (bookTab && Array.isArray(bookTab.data)) {
      for (const cell of bookTab.data) push(cell);
      if (books.length) return books;
    }
  }

  const walk = (node, depth = 0) => {
    if (!node || depth > 8) return;
    if (Array.isArray(node)) {
      for (const x of node) walk(x, depth + 1);
      return;
    }
    if (typeof node === "object") {
      if (Array.isArray(node.book_data) || node.book_name || node.book_id) {
        push(node);
      }
      for (const v of Object.values(node)) walk(v, depth + 1);
    }
  };
  walk(data);
  return books;
}

async function getDirectory(bookId) {
  // third-party catalog first
  for (const host of pickContentHosts()) {
    try {
      const res = await httpGet(
        `${host}/catalog?book_id=${bookId}`,
        { Accept: "application/json" },
        20000
      );
      const data = JSON.parse(res.body.toString("utf8") || "{}");
      if (data.code !== 0 && data.code !== "0" && !data.data) continue;
      const d = data.data || {};
      const list = d.item_data_list || d.itemDataList || d.chapter_list || [];
      if (Array.isArray(list) && list.length) {
        const chapters = list.map((ch) => ({
          item_id: String(ch.item_id || ch.itemId || ch.id || ""),
          title: ch.title || "",
          volume_name: ch.volume_name || "",
          need_pay: ch.need_pay || ch.needPay || 0,
        }));
        markHostOk(host, 0);
        return {
          book_id: bookId,
          chapter_count: chapters.length,
          chapters,
          volume_names: d.volume_name_list || d.volumeNameList || [],
          source: host,
        };
      }
    } catch (e) {
      markHostFail(host, String(e.message || e));
    }
  }

  const url = `https://fanqienovel.com/api/reader/directory/detail?bookId=${bookId}`;
  const res = await httpGet(url, { Accept: "application/json" }, 20000);
  const data = JSON.parse(res.body.toString("utf8") || "{}");
  if (data.code != null && data.code !== 0 && !data.data) {
    throw new Error(data.message || "目录获取失败");
  }
  const d = data.data || {};
  const chapters = [];
  const cl = d.chapterListWithVolume || [];
  if (cl.length && Array.isArray(cl[0])) {
    for (const vol of cl) {
      for (const ch of vol) {
        chapters.push({
          item_id: String(ch.itemId || ch.item_id || ""),
          title: ch.title || "",
          volume_name: ch.volume_name || "",
          need_pay: ch.needPay || 0,
        });
      }
    }
  } else {
    for (const itemId of d.allItemIds || []) {
      chapters.push({ item_id: String(itemId), title: "", volume_name: "", need_pay: 0 });
    }
  }
  return {
    book_id: bookId,
    chapter_count: chapters.length,
    chapters,
    volume_names: d.volumeNameList || [],
    source: "official",
  };
}

async function getBookMeta(bookId) {
  const meta = {
    book_id: bookId,
    title: `小说${bookId}`,
    author: "未知",
    abstract: "",
  };

  for (const host of pickContentHosts()) {
    try {
      const res = await httpGet(
        `${host}/info?book_id=${bookId}`,
        { Accept: "application/json" },
        15000
      );
      const data = JSON.parse(res.body.toString("utf8") || "{}");
      const d = data.data || data;
      if (d && (d.book_name || d.title || d.book_id)) {
        meta.title = d.book_name || d.title || meta.title;
        meta.author = d.author || d.author_name || meta.author;
        meta.abstract =
          d.book_abstract_v2 || d.book_abstract || d.abstract || d.intro || "";
        markHostOk(host, 0);
        return meta;
      }
    } catch {
      // continue
    }
  }

  try {
    const res = await httpGet(`https://fanqienovel.com/page/${bookId}`, {}, 20000);
    const html = res.body.toString("utf8");
    const m = html.match(
      /<script type="application\/ld\+json">([\s\S]*?)<\/script>/
    );
    if (m) {
      try {
        const ld = JSON.parse(m[1]);
        meta.title = ld.name || meta.title;
        const authors = ld.author || [];
        if (authors.length) {
          if (typeof authors[0] === "object") meta.author = authors[0].name || meta.author;
          else if (typeof authors[0] === "string") meta.author = authors[0];
        }
        meta.abstract = ld.description || "";
      } catch {
        // ignore
      }
    }
    const t = html.match(/<title>(.*?)<\/title>/);
    if (t) {
      let title = t[1].split("_")[0].trim().replace(/完整版在线免费阅读$/, "");
      if (title && (meta.title.startsWith("小说") || title.length < meta.title.length + 5)) {
        meta.title = title;
      }
    }
    if (meta.author === "未知") {
      const am = html.match(/author["']?\s*[:=]\s*["']([^"']+)["']/);
      if (am) meta.author = am[1];
    }
    if (!meta.abstract) {
      const dm = html.match(/<meta name="description" content="([^"]+)"/);
      if (dm) meta.abstract = dm[1];
    }
  } catch {
    // ignore
  }
  return meta;
}

function safeFilename(name) {
  const cleaned = String(name || "novel")
    .replace(/[\\/:*?"<>|]+/g, "_")
    .trim();
  return (cleaned || "novel").slice(0, 80);
}

function mapLimit(items, limit, worker, shouldStop) {
  return new Promise((resolve) => {
    let i = 0;
    let active = 0;
    let done = 0;
    let stopped = false;
    const n = items.length;
    if (!n) return resolve();
    const next = () => {
      if (stopped) return;
      if (shouldStop && shouldStop()) {
        stopped = true;
        return resolve();
      }
      if (done === n) return resolve();
      while (active < limit && i < n) {
        if (shouldStop && shouldStop()) {
          stopped = true;
          return resolve();
        }
        const idx = i++;
        active++;
        Promise.resolve()
          .then(() => worker(items[idx], idx))
          .then(() => {
            active--;
            done++;
            next();
          })
          .catch(() => {
            active--;
            done++;
            next();
          });
      }
    };
    next();
  });
}

function cacheDirFor(bookId) {
  const dir = path.join(CACHE_DIR, String(bookId));
  fs.mkdirSync(dir, { recursive: true });
  return dir;
}

function cachePathFor(bookId, itemId) {
  return path.join(cacheDirFor(bookId), `${itemId}.json`);
}

function loadCachedChapter(bookId, itemId) {
  try {
    const p = cachePathFor(bookId, itemId);
    if (!fs.existsSync(p)) return null;
    const data = JSON.parse(fs.readFileSync(p, "utf8"));
    if (data && data.text && String(data.text).length > 20) return data;
  } catch {
    // ignore
  }
  return null;
}

function saveCachedChapter(bookId, itemId, payload) {
  try {
    fs.writeFileSync(
      cachePathFor(bookId, itemId),
      JSON.stringify({
        item_id: itemId,
        title: payload.title || "",
        text: payload.text || "",
        preview_only: Boolean(payload.preview_only),
        source: payload.source || "",
        saved_at: Date.now(),
      }),
      "utf8"
    );
  } catch {
    // ignore
  }
}

function sliceChapters(chapters, opts) {
  let list = chapters.slice();
  const start = Math.max(1, Number(opts.start_chapter || 0) || 0);
  const end = Math.max(0, Number(opts.end_chapter || 0) || 0);
  const maxChapters = Math.max(0, Number(opts.max_chapters || 0) || 0);

  if (start > 1 || end > 0) {
    const from = start > 0 ? start - 1 : 0;
    const to = end > 0 ? end : list.length;
    list = list.slice(from, to);
  } else if (maxChapters > 0) {
    list = list.slice(0, maxChapters);
  }
  return list;
}

function isJobCancelled(job) {
  return Boolean(job && (job.cancel_requested || job.status === "cancelled"));
}

function buildTxt(meta, bookId, results, total, previewCount, rangeNote) {
  const lines = [
    meta.title,
    `作者：${meta.author}`,
    `书籍ID：${bookId}`,
    `章节数：${total}`,
  ];
  if (rangeNote) lines.push(rangeNote);
  if (meta.abstract) lines.push(`简介：${meta.abstract.slice(0, 500)}`);
  if (previewCount) {
    lines.push(
      `说明：有 ${previewCount} 章在网页端为锁定/试读，仅含部分正文；完整正文依赖第三方节点。`
    );
  }
  lines.push("", "=".repeat(40), "");
  for (const item of results) {
    if (!item) continue;
    const [title, text] = item;
    lines.push(title, "", text, "", "-".repeat(30), "");
  }
  return lines.join("\n");
}

async function runDownloadJob(jobId, bookId, opts = {}) {
  const job = JOBS.get(jobId);
  if (!job) return;
  job.status = "running";
  job.message = "获取书籍信息...";
  job.progress = 1;
  job.cancel_requested = false;

  try {
    if (isJobCancelled(job)) throw new Error("任务已取消");

    const meta = await getBookMeta(bookId);
    job.title = meta.title;
    job.author = meta.author;
    job.message = "获取目录...";
    job.progress = 3;

    if (isJobCancelled(job)) throw new Error("任务已取消");

    const directory = await getDirectory(bookId);
    const chapters = sliceChapters(directory.chapters, opts);
    const total = chapters.length;
    if (!total) throw new Error("章节列表为空，可能书籍不存在或暂不支持");

    const startChapter = Number(opts.start_chapter || 0) || 0;
    const endChapter = Number(opts.end_chapter || 0) || 0;
    const rangeNote =
      startChapter || endChapter
        ? `下载范围：第 ${startChapter || 1} 章 至 第 ${endChapter || directory.chapter_count} 章`
        : opts.max_chapters
          ? `下载范围：前 ${opts.max_chapters} 章`
          : "";

    job.total = total;
    job.done = 0;
    job.cached = 0;
    job.range_note = rangeNote;
    job.message = `开始下载，共 ${total} 章${opts.resume === false ? "" : "（支持断点缓存）"}`;
    job.progress = 5;

    const results = new Array(total);
    const errors = [];
    let previewCount = 0;
    let doneCount = 0;
    let cachedCount = 0;
    const useResume = opts.resume !== false;

    await mapLimit(
      chapters,
      MAX_WORKERS,
      async (ch, idx) => {
        if (isJobCancelled(job)) return;
        let title = ch.title || `第${idx + 1}章`;
        try {
          if (useResume) {
            const cached = loadCachedChapter(bookId, ch.item_id);
            if (cached) {
              title = cached.title || title;
              results[idx] = [title, cached.text];
              if (cached.preview_only) {
                previewCount++;
                errors.push(`${idx + 1}:${ch.item_id}:网页端仅试读`);
              }
              cachedCount++;
              return;
            }
          }
          const got = await fetchChapter(ch.item_id, bookId);
          if (isJobCancelled(job)) return;
          if (got.title) title = got.title;
          if (!got.text) throw new Error("空正文");
          results[idx] = [title, got.text];
          if (got.meta && got.meta.preview_only) {
            previewCount++;
            errors.push(`${idx + 1}:${ch.item_id}:网页端仅试读`);
          } else if (useResume) {
            saveCachedChapter(bookId, ch.item_id, {
              title,
              text: got.text,
              preview_only: false,
              source: (got.meta && got.meta.source) || "",
            });
          }
        } catch (e) {
          if (isJobCancelled(job)) return;
          results[idx] = [title, `【本章获取失败: ${e.message || e}】`];
          errors.push(`${idx + 1}:${ch.item_id}:${e.message || e}`);
        } finally {
          doneCount++;
          job.done = doneCount;
          job.cached = cachedCount;
          job.progress = 5 + Math.floor((doneCount / total) * 90);
          job.message = `下载中 ${doneCount}/${total}` + (cachedCount ? `（缓存 ${cachedCount}）` : "");
          if (CHAPTER_DELAY) await sleep(CHAPTER_DELAY);
        }
      },
      () => isJobCancelled(job)
    );

    if (isJobCancelled(job)) {
      // partial file for convenience
      const partial = buildTxt(meta, bookId, results, total, previewCount, rangeNote);
      const filename = `${safeFilename(meta.title)}-${bookId}-partial.txt`;
      const outPath = path.join(DOWNLOADS_DIR, `${jobId}.txt`);
      fs.writeFileSync(outPath, partial, "utf8");
      job.status = "cancelled";
      job.progress = Math.min(99, job.progress || 0);
      job.message = `已取消（已完成 ${doneCount}/${total} 章，可再次下载续传）`;
      job.filename = filename;
      job.filepath = outPath;
      job.size = fs.statSync(outPath).size;
      job.errors = errors.slice(0, 30);
      job.error_count = errors.filter((e) => !e.includes("仅试读")).length;
      job.preview_count = previewCount;
      job.full_count = total - previewCount;
      job.cached = cachedCount;
      return;
    }

    const content = buildTxt(meta, bookId, results, total, previewCount, rangeNote);
    const filename = `${safeFilename(meta.title)}-${bookId}.txt`;
    const outPath = path.join(DOWNLOADS_DIR, `${jobId}.txt`);
    fs.writeFileSync(outPath, content, "utf8");

    const hardErrors = errors.filter((e) => !e.includes("仅试读"));
    job.status = "done";
    job.progress = 100;
    if (previewCount && !hardErrors.length) {
      job.message = `下载完成（完整 ${total - previewCount}/${total} 章，${previewCount} 章为网页试读）`;
    } else if (hardErrors.length) {
      job.message = `下载完成（${hardErrors.length} 章失败，${previewCount} 章试读）`;
    } else {
      job.message = cachedCount
        ? `下载完成（其中 ${cachedCount} 章来自断点缓存）`
        : "下载完成";
    }
    job.filename = filename;
    job.filepath = outPath;
    job.size = fs.statSync(outPath).size;
    job.errors = errors.slice(0, 30);
    job.error_count = hardErrors.length;
    job.preview_count = previewCount;
    job.full_count = total - previewCount;
    job.cached = cachedCount;
  } catch (e) {
    if (String(e.message || e).includes("取消")) {
      job.status = "cancelled";
      job.message = "任务已取消";
      job.progress = 0;
      return;
    }
    job.status = "error";
    job.message = String(e.message || e);
    job.progress = 0;
  }
}

/* ---------------- node discovery & health ---------------- */

function ensureNode(host, source = "seed") {
  const h = normalizeHost(host);
  if (!h) return null;
  if (!NODE_STATE.has(h)) {
    NODE_STATE.set(h, {
      host: h,
      ok: false,
      latency: 99999,
      lastCheck: 0,
      failCount: 0,
      source,
      msg: "pending",
    });
  } else if (source && source !== "probe") {
    const n = NODE_STATE.get(h);
    if (n.source === "seed" || !n.source) n.source = source;
  }
  return h;
}

function markHostOk(host, latency) {
  const h = normalizeHost(host);
  if (!h) return;
  const n =
    NODE_STATE.get(h) ||
    ({
      host: h,
      ok: true,
      latency: latency || 0,
      lastCheck: Date.now(),
      failCount: 0,
      source: "runtime",
      msg: "ok",
    });
  n.ok = true;
  n.failCount = 0;
  if (latency > 0) n.latency = latency;
  n.lastCheck = Date.now();
  n.msg = "ok";
  NODE_STATE.set(h, n);
}

function markHostFail(host, msg) {
  const h = normalizeHost(host);
  if (!h) return;
  const n = NODE_STATE.get(h) || {
    host: h,
    ok: false,
    latency: 99999,
    lastCheck: Date.now(),
    failCount: 0,
    source: "runtime",
    msg: "",
  };
  n.failCount = (n.failCount || 0) + 1;
  // soft fail: mark dead after 2 consecutive failures
  if (n.failCount >= 2) n.ok = false;
  n.lastCheck = Date.now();
  n.msg = String(msg || "fail").slice(0, 120);
  NODE_STATE.set(h, n);
}

async function probeHost(host) {
  const h = ensureNode(host) || host;
  const start = Date.now();
  try {
    // status page title check (optional) + content probe
    const res = await httpGet(
      `${h}/content?item_id=${PROBE_ITEM_ID}`,
      { Accept: "application/json" },
      10000
    );
    const latency = Date.now() - start;
    if (res.status !== 200) {
      markHostFail(h, `http ${res.status}`);
      return false;
    }
    const data = JSON.parse(res.body.toString("utf8") || "{}");
    const content =
      (data.data && (data.data.content || data.data.text)) ||
      (typeof data.data === "string" ? data.data : "");
    if ((data.code === 0 || data.code === "0") && content && String(content).length > 30) {
      markHostOk(h, latency);
      return true;
    }
    // some nodes return error for probe id but still serve others — check homepage
    const home = await httpGet(h + "/", {}, 8000);
    const html = home.body.toString("utf8");
    if (home.status === 200 && (html.includes("番茄API状态") || html.includes("/content"))) {
      markHostOk(h, Date.now() - start);
      return true;
    }
    markHostFail(h, data.msg || data.message || "invalid content");
    return false;
  } catch (e) {
    markHostFail(h, e.message || e);
    return false;
  }
}

async function probeAllNodes() {
  const hosts = [...NODE_STATE.keys()];
  if (!hosts.length) {
    for (const h of seedHostsFromEnv()) ensureNode(h, "seed");
  }
  const list = [...NODE_STATE.keys()];
  await Promise.all(list.map((h) => probeHost(h)));
  const alive = [...NODE_STATE.values()].filter((n) => n.ok).length;
  console.log(`[nodes] probe done: ${alive}/${list.length} alive`);
  return alive;
}

function isBlockedDiscoverHost(host) {
  return /fanqienovel\.com|snssdk\.com|byteimg\.com|bytedance|bing\.com|microsoft|google|baidu|github|gitee|qq\.com|apple\.com|unsplash/i.test(
    host
  );
}

function extractHostsFromText(text, source) {
  const found = new Set();
  if (!text) return found;
  // only IP-based hosts (status mirrors use public IP)
  for (const m of text.matchAll(/https?:\/\/(?:\d{1,3}\.){3}\d{1,3}(?::\d{2,5})?/g)) {
    const h = normalizeHost(m[0]);
    if (h && !isBlockedDiscoverHost(h)) found.add(h);
  }
  for (const m of text.matchAll(/(?:^|[^0-9])((?:\d{1,3}\.){3}\d{1,3}:\d{2,5})(?![0-9])/g)) {
    const h = normalizeHost("http://" + m[1]);
    if (h && !isBlockedDiscoverHost(h)) found.add(h);
  }
  for (const h of found) ensureNode(h, source);
  return found;
}

async function discoverFromStatusPages() {
  // crawl known alive status pages for sibling IPs (some pages list peers)
  const seeds = [...NODE_STATE.keys()].length
    ? [...NODE_STATE.keys()]
    : seedHostsFromEnv();
  let added = 0;
  for (const host of seeds.slice(0, 12)) {
    try {
      const res = await httpGet(host + "/", {}, 10000);
      const html = res.body.toString("utf8");
      if (!html.includes("番茄API状态") && !html.includes("/content") && !html.includes("batch_content")) {
        continue;
      }
      const found = extractHostsFromText(html, "status-page");
      added += found.size;
      // also try common JSON endpoints some mirrors expose
      for (const p of ["/nodes", "/api/nodes", "/servers", "/list.json", "/hosts"]) {
        try {
          const r2 = await httpGet(host + p, { Accept: "application/json" }, 6000);
          if (r2.status === 200) {
            const t = r2.body.toString("utf8");
            extractHostsFromText(t, "status-json");
          }
        } catch {
          // ignore
        }
      }
    } catch {
      // ignore
    }
  }
  return added;
}

async function discoverFromSearchEngines() {
  const queries = [
    "番茄API状态",
    '"番茄API状态" content batch_content',
    "番茄API状态 /content?item_id",
  ];
  let total = 0;
  for (const q of queries) {
    const urls = [
      `https://www.bing.com/search?q=${encodeURIComponent(q)}&count=50`,
      `https://cn.bing.com/search?q=${encodeURIComponent(q)}&count=50`,
    ];
    for (const url of urls) {
      try {
        const res = await httpGet(
          url,
          {
            Accept: "text/html",
            "Accept-Language": "zh-CN,zh;q=0.9",
            Referer: "https://www.bing.com/",
          },
          15000
        );
        const html = res.body.toString("utf8");
        const found = extractHostsFromText(html, "bing");
        // also extract result links that look like status pages
        const hrefs = [...html.matchAll(/href="(https?:\/\/[^"]+)"/g)].map((m) => m[1]);
        for (const href of hrefs) {
          if (/\d+\.\d+\.\d+\.\d+/.test(href)) {
            try {
              const u = new URL(href.split("&")[0]);
              if (u.hostname && /^\d+\.\d+\.\d+\.\d+$/.test(u.hostname)) {
                const h = normalizeHost(`${u.protocol}//${u.host}`);
                if (h && !isBlockedDiscoverHost(h)) {
                  ensureNode(h, "bing-link");
                  total++;
                }
              }
            } catch {
              // ignore
            }
          }
        }
        total += found.size;
      } catch (e) {
        console.log("[discover] search fail", e.message || e);
      }
    }
  }
  return total;
}

async function discoverNodes() {
  console.log("[nodes] discovering...");
  for (const h of seedHostsFromEnv()) ensureNode(h, "seed");
  // env EXTRA_CONTENT_API_HOSTS
  if (process.env.EXTRA_CONTENT_API_HOSTS) {
    for (const h of process.env.EXTRA_CONTENT_API_HOSTS.split(",")) ensureNode(h, "env-extra");
  }
  // optional: NODE_SEED_FILE
  const seedFile = process.env.NODE_SEED_FILE || path.join(ROOT, "nodes.json");
  if (fs.existsSync(seedFile)) {
    try {
      const raw = JSON.parse(fs.readFileSync(seedFile, "utf8"));
      const list = Array.isArray(raw) ? raw : raw.hosts || raw.nodes || [];
      for (const h of list) ensureNode(typeof h === "string" ? h : h.host || h.url, "file");
    } catch (e) {
      console.log("[nodes] seed file parse fail", e.message);
    }
  }

  await discoverFromStatusPages();
  await discoverFromSearchEngines();
  await probeAllNodes();

  // persist alive nodes for next boot
  try {
    const alive = [...NODE_STATE.values()]
      .filter((n) => n.ok)
      .map((n) => ({ host: n.host, latency: n.latency, source: n.source }));
    fs.writeFileSync(
      path.join(ROOT, "nodes.alive.json"),
      JSON.stringify({ updated_at: new Date().toISOString(), hosts: alive }, null, 2)
    );
  } catch {
    // ignore
  }
}

function nodesSnapshot() {
  const nodes = [...NODE_STATE.values()]
    .map((n) => ({
      host: n.host,
      ok: n.ok,
      latency: n.latency,
      failCount: n.failCount,
      lastCheck: n.lastCheck,
      source: n.source,
      msg: n.msg,
    }))
    .sort((a, b) => Number(b.ok) - Number(a.ok) || a.latency - b.latency);
  return {
    total: nodes.length,
    alive: nodes.filter((n) => n.ok).length,
    dead: nodes.filter((n) => !n.ok).length,
    nodes,
  };
}

/* ---------------- HTTP server ---------------- */

function sendJson(res, code, payload) {
  const data = Buffer.from(JSON.stringify(payload), "utf8");
  res.writeHead(code, {
    "Content-Type": "application/json; charset=utf-8",
    "Content-Length": data.length,
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type",
  });
  res.end(data);
}

function sendText(res, code, text, contentType = "text/plain; charset=utf-8") {
  const data = Buffer.from(text, "utf8");
  res.writeHead(code, {
    "Content-Type": contentType,
    "Content-Length": data.length,
    "Access-Control-Allow-Origin": "*",
  });
  res.end(data);
}

function sendFile(res, filePath, filename, contentType) {
  const data = fs.readFileSync(filePath);
  const disposition = `attachment; filename*=UTF-8''${encodeURIComponent(filename)}`;
  res.writeHead(200, {
    "Content-Type": contentType,
    "Content-Length": data.length,
    "Content-Disposition": disposition,
    "Access-Control-Allow-Origin": "*",
  });
  res.end(data);
}

function serveStatic(req, res, urlPath) {
  let rel = decodeURIComponent(urlPath.split("?")[0]);
  if (rel === "/") rel = "/index.html";
  const safe = path.normalize(rel).replace(/^(\.\.[/\\])+/, "");
  const filePath = path.join(ROOT, safe);
  if (!filePath.startsWith(ROOT)) {
    sendText(res, 403, "forbidden");
    return true;
  }
  if (!fs.existsSync(filePath) || fs.statSync(filePath).isDirectory()) {
    return false;
  }
  const ext = path.extname(filePath).toLowerCase();
  const types = {
    ".html": "text/html; charset=utf-8",
    ".js": "application/javascript; charset=utf-8",
    ".css": "text/css; charset=utf-8",
    ".json": "application/json; charset=utf-8",
    ".txt": "text/plain; charset=utf-8",
    ".png": "image/png",
    ".jpg": "image/jpeg",
    ".svg": "image/svg+xml",
    ".ico": "image/x-icon",
  };
  const data = fs.readFileSync(filePath);
  res.writeHead(200, {
    "Content-Type": types[ext] || "application/octet-stream",
    "Content-Length": data.length,
    "Access-Control-Allow-Origin": "*",
  });
  res.end(data);
  return true;
}

async function handleApi(req, res, u) {
  const pathName = u.pathname;
  const qs = u.searchParams;

  if (pathName === "/api/health") {
    const snap = nodesSnapshot();
    sendJson(res, 200, {
      ok: true,
      service: "fanqie-proxy-node",
      runtime: "node",
      nodes_alive: snap.alive,
      nodes_total: snap.total,
    });
    return;
  }

  if (pathName === "/api/nodes") {
    sendJson(res, 200, { code: 0, ...nodesSnapshot() });
    return;
  }

  if (pathName === "/api/nodes/refresh") {
    // async refresh
    discoverNodes().catch((e) => console.error(e));
    sendJson(res, 200, { code: 0, message: "refresh started" });
    return;
  }

  if (pathName === "/api/search") {
    const q = (qs.get("q") || qs.get("query") || "").trim();
    const offset = Number(qs.get("offset") || 0);
    if (!q) {
      sendJson(res, 400, { code: -1, message: "缺少搜索关键词 q" });
      return;
    }
    sendJson(res, 200, await searchBooks(q, offset));
    return;
  }

  if (pathName.startsWith("/api/book/")) {
    const bookId = pathName.slice("/api/book/".length).replace(/\/$/, "");
    if (!/^\d+$/.test(bookId)) {
      sendJson(res, 400, { code: -1, message: "无效 book_id" });
      return;
    }
    try {
      const meta = await getBookMeta(bookId);
      const directory = await getDirectory(bookId);
      sendJson(res, 200, {
        code: 0,
        ...meta,
        chapter_count: directory.chapter_count,
        volume_names: directory.volume_names,
      });
    } catch (e) {
      sendJson(res, 500, { code: -1, message: String(e.message || e) });
    }
    return;
  }

  if (pathName.startsWith("/api/job/")) {
    const rest = pathName.slice("/api/job/".length).replace(/\/$/, "");
    const parts = rest.split("/");
    const jobId = parts[0];
    const job = JOBS.get(jobId);
    if (!job) {
      sendJson(res, 404, { code: -1, message: "任务不存在" });
      return;
    }
    if (parts[1] === "file") {
      if (!job.filepath || !["done", "cancelled"].includes(job.status)) {
        sendJson(res, 400, { code: -1, message: "文件未就绪" });
        return;
      }
      sendFile(res, job.filepath, job.filename || `${jobId}.txt`, "text/plain; charset=utf-8");
      return;
    }
    if (parts[1] === "cancel" && (req.method === "POST" || req.method === "GET")) {
      if (["done", "error", "cancelled"].includes(job.status)) {
        sendJson(res, 200, {
          code: 0,
          job_id: jobId,
          status: job.status,
          message: "任务已结束，无需取消",
        });
        return;
      }
      job.cancel_requested = true;
      job.message = "正在取消...";
      sendJson(res, 200, { code: 0, job_id: jobId, message: "已请求取消" });
      return;
    }
    sendJson(res, 200, {
      code: 0,
      job_id: jobId,
      status: job.status,
      progress: job.progress || 0,
      message: job.message || "",
      done: job.done || 0,
      total: job.total || 0,
      cached: job.cached || 0,
      title: job.title || "",
      author: job.author || "",
      filename: job.filename,
      size: job.size,
      error_count: job.error_count || 0,
      preview_count: job.preview_count || 0,
      full_count: job.full_count || 0,
      range_note: job.range_note || "",
    });
    return;
  }

  if (pathName === "/api/download") {
    let bookId = (qs.get("book_id") || qs.get("id") || "").trim();
    let opts = {
      max_chapters: Number(qs.get("max_chapters") || 0) || 0,
      start_chapter: Number(qs.get("start_chapter") || qs.get("from") || 0) || 0,
      end_chapter: Number(qs.get("end_chapter") || qs.get("to") || 0) || 0,
      resume: qs.get("resume") !== "0" && qs.get("resume") !== "false",
    };
    if (req.method === "POST") {
      const body = await readBody(req);
      try {
        const json = JSON.parse(body || "{}");
        bookId = String(json.book_id || json.id || bookId || "").trim();
        opts = {
          max_chapters: Number(json.max_chapters || opts.max_chapters || 0) || 0,
          start_chapter: Number(json.start_chapter || json.from || opts.start_chapter || 0) || 0,
          end_chapter: Number(json.end_chapter || json.to || opts.end_chapter || 0) || 0,
          resume: json.resume === false || json.resume === 0 ? false : opts.resume,
        };
      } catch {
        // ignore
      }
    }
    if (!/^\d+$/.test(bookId)) {
      sendJson(res, 400, { code: -1, message: "缺少有效 book_id" });
      return;
    }
    const jobId = randomUUID().replace(/-/g, "").slice(0, 12);
    JOBS.set(jobId, {
      status: "queued",
      progress: 0,
      message: "排队中",
      book_id: bookId,
      created_at: Date.now(),
      cancel_requested: false,
      opts,
    });
    setImmediate(() => {
      runDownloadJob(jobId, bookId, opts).catch((e) => {
        const job = JOBS.get(jobId);
        if (job) {
          job.status = "error";
          job.message = String(e.message || e);
        }
      });
    });
    sendJson(res, 200, { code: 0, job_id: jobId, book_id: bookId, opts });
    return;
  }

  sendJson(res, 404, { code: -1, message: "not found" });
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    req.on("data", (c) => chunks.push(c));
    req.on("end", () => resolve(Buffer.concat(chunks).toString("utf8")));
    req.on("error", reject);
  });
}

const server = http.createServer(async (req, res) => {
  try {
    if (req.method === "OPTIONS") {
      res.writeHead(204, {
        "Access-Control-Allow-Origin": "*",
        "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
        "Access-Control-Allow-Headers": "Content-Type",
      });
      res.end();
      return;
    }

    const u = new URL(req.url || "/", `http://${req.headers.host || "localhost"}`);

    if (u.pathname.startsWith("/api/")) {
      await handleApi(req, res, u);
      return;
    }

    if (serveStatic(req, res, u.pathname)) return;
    sendJson(res, 404, { code: -1, message: "not found" });
  } catch (e) {
    console.error(e);
    if (!res.headersSent) sendJson(res, 500, { code: -1, message: String(e.message || e) });
  }
});

async function main() {
  for (const h of seedHostsFromEnv()) ensureNode(h, "seed");
  // load previous alive list
  const aliveFile = path.join(ROOT, "nodes.alive.json");
  if (fs.existsSync(aliveFile)) {
    try {
      const raw = JSON.parse(fs.readFileSync(aliveFile, "utf8"));
      for (const item of raw.hosts || []) {
        ensureNode(typeof item === "string" ? item : item.host, "cache");
      }
    } catch {
      // ignore
    }
  }

  server.listen(PORT, HOST, () => {
    console.log(`Fanqie proxy (Node) running at http://${HOST}:${PORT}`);
  });

  // background node management
  discoverNodes().catch((e) => console.error("[nodes] init", e));
  setInterval(() => {
    probeAllNodes().catch((e) => console.error("[nodes] probe", e));
  }, NODE_PROBE_INTERVAL);
  setInterval(() => {
    discoverNodes().catch((e) => console.error("[nodes] discover", e));
  }, NODE_DISCOVER_INTERVAL);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
