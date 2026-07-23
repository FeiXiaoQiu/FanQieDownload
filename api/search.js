/**
 * Vercel Serverless 书名搜索（服务端直连节点，无浏览器 CORS）
 * GET /api/search?q=书名
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

const UA =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

/** bytecdn 防盗链 403；统一改写为可代理的 byteimg */
function normalizeCoverUrl(raw) {
  let u = String(raw || "").trim();
  if (!u) return "";
  if (u.indexOf("bytecdn.cn") !== -1 && u.indexOf("novel-pic/") !== -1) {
    const m = /novel-pic\/([^~?/]+)/.exec(u);
    if (m) {
      u =
        "https://p3-novel.byteimg.com/img/novel-pic/" +
        m[1] +
        "~tplv-tt-cs0:440:440.image";
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

function parseSearch(data) {
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
    // thumb_url(bytecdn) 常 403；优先 thumb_uri，并强制改写为 byteimg
    const rawThumb =
      it.thumb_uri ||
      it.audio_thumb_uri ||
      it.thumb_url ||
      it.cover_url ||
      it.cover ||
      "";
    const thumb = normalizeCoverUrl(rawThumb);
    books.push({
      book_id: book_id,
      title: title,
      author: it.author || "",
      abstract: it.abstract || "",
      thumb_url: thumb,
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

module.exports = async function handler(req, res) {
  cors(res);
  if (req.method === "OPTIONS") {
    res.statusCode = 204;
    res.end();
    return;
  }

  const q = ((req.query && (req.query.q || req.query.query)) || "").trim();
  res.setHeader("Content-Type", "application/json; charset=utf-8");
  res.setHeader("Cache-Control", "no-store");
  if (!q) {
    res.statusCode = 400;
    res.end(JSON.stringify({ code: -1, message: "缺少搜索关键词 q", books: [] }));
    return;
  }

  let lastErr = null;
  for (let i = 0; i < HOSTS.length; i++) {
    const host = HOSTS[i];
    try {
      const data = await httpGetJson(
        host + "/search?query=" + encodeURIComponent(q) + "&page=0",
        12000
      );
      if (data.code !== 0 && data.code !== "0") {
        lastErr = data.message || data.msg || "code " + data.code;
        continue;
      }
      const books = parseSearch(data);
      if (books.length) {
        res.statusCode = 200;
        res.end(
          JSON.stringify({
            code: 0,
            books: books,
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

  res.statusCode = 200;
  res.end(
    JSON.stringify({
      code: -1,
      message: "搜索失败: " + (lastErr || "无节点"),
      books: [],
    })
  );
};
