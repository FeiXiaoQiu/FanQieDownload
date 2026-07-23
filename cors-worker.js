/**
 * Cloudflare Worker：给静态站补 CORS（免费额度通常够个人用）
 *
 * 部署：
 * 1. https://dash.cloudflare.com → Workers & Pages → Create Worker
 * 2. 粘贴本文件全部代码 → Deploy
 * 3. 复制 *.workers.dev 地址
 * 4. 打开站点，控制台执行：
 *    localStorage.setItem('fq_cors_proxy', 'https://你的子域.workers.dev')
 *    location.reload()
 * 或部署时在 index.html 加：
 *    <script>window.FQ_CORS_PROXY='https://你的子域.workers.dev'</script>
 *
 * 请求格式：GET /?url=https%3A%2F%2Fexample.com%2Fpath
 */
export default {
  async fetch(request) {
    const cors = {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "GET,HEAD,OPTIONS",
      "Access-Control-Allow-Headers": "*",
      "Access-Control-Max-Age": "86400",
    };

    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: cors });
    }

    const reqUrl = new URL(request.url);
    let target = reqUrl.searchParams.get("url") || reqUrl.searchParams.get("u");
    if (!target) {
      const path = reqUrl.pathname.replace(/^\//, "");
      if (/^https?:\/\//i.test(path)) target = path + reqUrl.search;
    }
    if (!target) {
      return new Response(
        JSON.stringify({
          ok: true,
          usage: "GET /?url=" + encodeURIComponent("http://host/path"),
        }),
        {
          status: 200,
          headers: { "Content-Type": "application/json", ...cors },
        }
      );
    }

    let dest;
    try {
      dest = new URL(target);
    } catch {
      return new Response(JSON.stringify({ error: "invalid url" }), {
        status: 400,
        headers: { "Content-Type": "application/json", ...cors },
      });
    }
    if (dest.protocol !== "http:" && dest.protocol !== "https:") {
      return new Response(JSON.stringify({ error: "protocol not allowed" }), {
        status: 400,
        headers: { "Content-Type": "application/json", ...cors },
      });
    }

    try {
      const upstream = await fetch(dest.toString(), {
        method: "GET",
        headers: {
          Accept: "application/json,text/plain,*/*",
          "User-Agent":
            "Mozilla/5.0 (compatible; FanqieDL-CORS/1.0; +https://github.com/FeiXiaoQiu/FanQieDownload)",
        },
        redirect: "follow",
        cf: { cacheTtl: 0, cacheEverything: false },
      });
      const body = await upstream.arrayBuffer();
      const headers = new Headers(cors);
      headers.set(
        "Content-Type",
        upstream.headers.get("Content-Type") || "application/json; charset=utf-8"
      );
      headers.set("Cache-Control", "no-store");
      headers.set("X-Proxy-Status", String(upstream.status));
      return new Response(body, { status: upstream.status, headers });
    } catch (e) {
      return new Response(
        JSON.stringify({ error: "upstream failed", message: String(e) }),
        {
          status: 502,
          headers: { "Content-Type": "application/json", ...cors },
        }
      );
    }
  },
};
