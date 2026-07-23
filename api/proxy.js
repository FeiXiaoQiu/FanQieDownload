/**
 * Vercel Serverless CORS 代理
 * GET /api/proxy?url=http%3A%2F%2Fhost%2Fpath
 */
const http = require("http");
const https = require("https");
const { URL } = require("url");

const UA =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

function cors(res) {
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "GET,HEAD,OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "*");
  res.setHeader("Access-Control-Max-Age", "86400");
}

function fetchBuffer(target, timeoutMs) {
  return new Promise(function (resolve, reject) {
    let u;
    try {
      u = new URL(target);
    } catch (e) {
      reject(e);
      return;
    }
    if (u.protocol !== "http:" && u.protocol !== "https:") {
      reject(new Error("protocol not allowed"));
      return;
    }
    const lib = u.protocol === "https:" ? https : http;
    const isImg =
      /byteimg|bytecdn|novel-pic|tos-cn|p\d+-/.test(u.hostname) ||
      /\.(png|jpe?g|webp|gif|image)(\?|$)/i.test(u.pathname + u.search);
    const req = lib.request(
      {
        protocol: u.protocol,
        hostname: u.hostname,
        port: u.port || (u.protocol === "https:" ? 443 : 80),
        path: u.pathname + u.search,
        method: "GET",
        headers: {
          "User-Agent": UA,
          Accept: isImg
            ? "image/avif,image/webp,image/apng,image/*,*/*;q=0.8"
            : "application/json,text/plain,*/*",
          "Accept-Encoding": "identity",
          Referer: "https://fanqienovel.com/",
          Origin: "https://fanqienovel.com",
        },
        timeout: timeoutMs,
      },
      function (r) {
        const chunks = [];
        r.on("data", function (c) {
          chunks.push(c);
        });
        r.on("end", function () {
          resolve({
            status: r.statusCode || 0,
            contentType: r.headers["content-type"] || "application/json; charset=utf-8",
            body: Buffer.concat(chunks),
          });
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

module.exports = async function handler(req, res) {
  cors(res);
  if (req.method === "OPTIONS") {
    res.statusCode = 204;
    res.end();
    return;
  }

  const q = req.query || {};
  const target = (q.url || q.u || "").trim();
  if (!target) {
    res.statusCode = 200;
    res.setHeader("Content-Type", "application/json; charset=utf-8");
    res.end(
      JSON.stringify({
        ok: true,
        usage: "GET /api/proxy?url=" + encodeURIComponent("http://host/path"),
      })
    );
    return;
  }

  try {
    // bytecdn 直连常 403，代理层先改写为 byteimg
    let finalTarget = String(target);
    if (
      finalTarget.indexOf("bytecdn.cn") !== -1 &&
      finalTarget.indexOf("novel-pic/") !== -1
    ) {
      const m = /novel-pic\/([^~?/]+)/.exec(finalTarget);
      if (m) {
        finalTarget =
          "https://p3-novel.byteimg.com/img/novel-pic/" +
          m[1] +
          "~tplv-tt-cs0:120:160.image";
      }
    }
    let up = await fetchBuffer(finalTarget, 18000);
    // 仍失败且是图片：再试 origin 路径
    if (
      (up.status === 403 || up.status === 404) &&
      finalTarget.indexOf("novel-pic/") !== -1
    ) {
      const m2 = /novel-pic\/([^~?/]+)/.exec(finalTarget);
      if (m2) {
        const alt =
          "https://p3-novel.byteimg.com/img/novel-pic/" +
          m2[1] +
          "~tplv-tt-cs0:300:400.image";
        if (alt !== finalTarget) {
          up = await fetchBuffer(alt, 18000);
        }
      }
    }
    res.statusCode = up.status || 200;
    res.setHeader("Content-Type", up.contentType);
    res.setHeader("Cache-Control", "public, max-age=86400");
    res.setHeader("X-Proxy-Status", String(up.status));
    res.setHeader("X-Proxy-Target", finalTarget.slice(0, 200));
    res.end(up.body);
  } catch (e) {
    res.statusCode = 502;
    res.setHeader("Content-Type", "application/json; charset=utf-8");
    res.end(
      JSON.stringify({
        error: "upstream failed",
        message: String(e && e.message ? e.message : e),
      })
    );
  }
};
