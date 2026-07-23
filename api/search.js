/**
 * Vercel Serverless 书名搜索
 * GET /api/search?q=书名&offset=0
 */
const http = require("http");
const https = require("https");
const { URL } = require("url");

const HOSTS = [
  "http://110.42.57.146:4018",
  "http://81.70.223.143:6897",
  "http://43.143.149.30:8008",
  "http://59.110.160.171:5007",
  "http://103.43.9.59",
];

const OFFICIAL = [
  "https://api-lf.fanqiesdk.com/api/novel/channel/homepage/search/search/v1/",
  "https://novel.snssdk.com/api/novel/channel/homepage/search/search/v1/",
];

const UA =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

function normalizeCoverUrl(raw) {
  let u = String(raw || "").trim();
  if (!u) return "";
  if (u.indexOf("bytecdn.cn") !== -1 && u.indexOf("novel-pic/") !== -1) {
    const m = /novel-pic\/([^~?/]+)/.exec(u);
    if (m) {
      u =
        "https://p3-novel.byteimg.com/img/novel-pic/" +
        m[1] +
        "~tplv-tt-cs0:120:160.image";
    }
  }
  return u;
}

function cors(res) {
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "GET,OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "*");
}

function httpGetJson(url, timeoutMs) {
  return new Promise(function (resolve, reject) {
    const u = new URL(url);
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
          Accept: "application/json",
          "Accept-Encoding": "identity",
        },
        timeout: timeoutMs,
      },
      function (r) {
        const chunks = [];
        r.on("data", function (c) {
          chunks.push(c);
        });
        r.on("end", function () {
          try {
            const text = Buffer.concat(chunks).toString("utf8");
            resolve(JSON.parse(text || "{}"));
          } catch (e) {
            reject(e);
          }
        });
      }
    );
    req.on("timeout", function () {
      req.destroy(new Error("timeout"));
    });
    req.on("error", reject);
    req.end();
  });
}

function parseThirdParty(data) {
  const books = [];
  const seen = new Set();
  function push(it) {
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
      thumb_url: normalizeCoverUrl(
        it.thumb_uri ||
          it.audio_thumb_uri ||
          it.thumb_url ||
          it.cover_url ||
          it.cover ||
          ""
      ),
      score: it.score || "",
      category: it.category || "",
    });
  }
  const tabs = data.search_tabs || [];
  const bookTab =
    tabs.find(function (t) {
      return t && (t.title === "书籍" || t.tab_type === 3);
    }) || tabs[0];
  if (bookTab && Array.isArray(bookTab.data)) {
    for (const cell of bookTab.data) push(cell);
    if (books.length) return books;
  }
  function walk(node, depth) {
    if (!node || depth > 8) return;
    if (Array.isArray(node)) {
      for (const x of node) walk(x, depth + 1);
      return;
    }
    if (typeof node === "object") {
      if (node.book_data || node.book_name || node.book_id) push(node);
      for (const k of Object.keys(node)) walk(node[k], depth + 1);
    }
  }
  walk(data, 0);
  return books;
}

function parseOfficial(data) {
  const ret = (data.data && data.data.ret_data) || [];
  const books = [];
  for (let i = 0; i < ret.length; i++) {
    const it = ret[i] || {};
    const book_id = String(it.book_id || "");
    if (!/^\d{10,}$/.test(book_id)) continue;
    books.push({
      book_id: book_id,
      title: it.title || it.book_name || "",
      author: it.author || "",
      abstract: it.abstract || "",
      thumb_url: normalizeCoverUrl(
        it.thumb_uri || it.thumb_url || it.cover || ""
      ),
      score: it.score || "",
      category: it.category || "",
    });
  }
  const next =
    (data.data && data.data.offset) ||
    Number((data.data && data.data.next_offset) || 0) ||
    null;
  const has_more = Boolean(data.data && data.data.has_more);
  return { books: books, next_offset: next, has_more: has_more };
}

module.exports = async function handler(req, res) {
  cors(res);
  if (req.method === "OPTIONS") {
    res.statusCode = 204;
    res.end();
    return;
  }

  const q = ((req.query && (req.query.q || req.query.query)) || "").trim();
  const offset = Math.max(
    0,
    parseInt((req.query && (req.query.offset || req.query.o)) || "0", 10) || 0
  );
  res.setHeader("Content-Type", "application/json; charset=utf-8");
  res.setHeader("Cache-Control", "no-store");
  if (!q) {
    res.statusCode = 400;
    res.end(JSON.stringify({ code: -1, message: "缺少搜索关键词 q", books: [] }));
    return;
  }

  let lastErr = null;

  // 1) 官方接口支持 offset 分页，结果更多
  for (let i = 0; i < OFFICIAL.length; i++) {
    const base = OFFICIAL[i];
    try {
      const data = await httpGetJson(
        base +
          "?aid=1967&offset=" +
          offset +
          "&q=" +
          encodeURIComponent(q),
        12000
      );
      if (data.code !== 0 && data.code !== "0") {
        lastErr = data.message || "official code " + data.code;
        continue;
      }
      const parsed = parseOfficial(data);
      if (parsed.books.length || offset > 0) {
        res.statusCode = 200;
        res.end(
          JSON.stringify({
            code: 0,
            books: parsed.books,
            offset: offset,
            next_offset:
              parsed.next_offset != null
                ? parsed.next_offset
                : offset + parsed.books.length,
            has_more: parsed.has_more,
            source: "official",
            service: "vercel",
          })
        );
        return;
      }
      lastErr = "official empty";
    } catch (e) {
      lastErr = String(e && e.message ? e.message : e);
    }
  }

  // 2) 第三方节点（page，每页约 7～10 条）
  const page = Math.floor(offset / 10);
  for (let i = 0; i < HOSTS.length; i++) {
    const host = HOSTS[i];
    try {
      const data = await httpGetJson(
        host +
          "/search?query=" +
          encodeURIComponent(q) +
          "&page=" +
          page,
        12000
      );
      if (data.code !== 0 && data.code !== "0") {
        lastErr = data.message || data.msg || "code " + data.code;
        continue;
      }
      const books = parseThirdParty(data);
      if (books.length) {
        res.statusCode = 200;
        res.end(
          JSON.stringify({
            code: 0,
            books: books,
            offset: offset,
            next_offset: offset + books.length,
            has_more: books.length >= 7,
            source: host,
            service: "vercel",
          })
        );
        return;
      }
      lastErr = "empty books";
    } catch (e) {
      lastErr = String(e && e.message ? e.message : e);
    }
  }

  res.statusCode = 502;
  res.end(
    JSON.stringify({
      code: -1,
      message: "搜索失败: " + (lastErr || "unknown"),
      books: [],
    })
  );
};
