// Captures the tower scene and its renderer.info in headless Chrome over CDP.
// Spike tooling for #15; needs Node 22+ (global WebSocket and fetch), Chrome and
// a running `ng serve` (dev mode: the synthetic tower and the `window.__tower`
// hook are dev-only). Backend REST calls are stubbed, so no backend is needed.
//
//   node tools/capture-scene.mjs <outDir> <baseUrl> <label> [floors] [garrison] [current]
//
// For each theme (night, day) and pixel scale (1, 2, 3) it loads
// `<baseUrl>/?inkFixture=<floors>&floor=<current>&garrison=<garrison>&pixelScale=<s>`,
// saves `<outDir>/<label>-<theme>-<scale>x.png` (canvas only) and prints one
// JSON line per capture with the renderer.info numbers of the last frame.
// Defaults: 100 floors, garrison 12, current floor 50.
import { spawn } from "node:child_process";
import { mkdirSync, writeFileSync, mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

const [
  outDir,
  baseUrl,
  label,
  floors = "100",
  garrison = "12",
  current = "50",
] = process.argv.slice(2);
const CHROME =
  process.env.CHROME ??
  "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";
const PORT = 9333;
mkdirSync(outDir, { recursive: true });

const profile = mkdtempSync(join(tmpdir(), "td-chrome-"));
const chrome = spawn(
  CHROME,
  [
    "--headless=new",
    `--remote-debugging-port=${PORT}`,
    `--user-data-dir=${profile}`,
    "--use-angle=swiftshader",
    "--enable-unsafe-swiftshader",
    "--ignore-gpu-blocklist",
    "--hide-scrollbars",
    "about:blank",
  ],
  { stdio: "ignore" },
);

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
async function connect() {
  for (let i = 0; i < 50; i++) {
    try {
      const targets = await (
        await fetch(`http://127.0.0.1:${PORT}/json`)
      ).json();
      const page = targets.find((t) => t.type === "page");
      if (page) return new WebSocket(page.webSocketDebuggerUrl);
    } catch {
      /* not up yet */
    }
    await sleep(200);
  }
  throw new Error("Chrome did not start");
}

const ws = await connect();
await new Promise((r) => (ws.onopen = r));
let id = 0;
const pending = new Map();
ws.onmessage = (m) => {
  const msg = JSON.parse(m.data);
  if (process.env.CDP_LOG && msg.method === "Runtime.exceptionThrown") {
    console.error(
      "EXC",
      msg.params.exceptionDetails.exception?.description?.slice(0, 400),
    );
  }
  if (process.env.CDP_LOG && msg.method === "Runtime.consoleAPICalled") {
    console.error(
      "CON",
      msg.params.type,
      msg.params.args
        .map((a) => a.value ?? a.description)
        .join(" ")
        .slice(0, 300),
    );
  }
  if (msg.method === "Fetch.requestPaused") {
    // The scene must not depend on whether the backend is running: stub its REST calls.
    send("Fetch.fulfillRequest", {
      requestId: msg.params.requestId,
      responseCode: 200,
      responseHeaders: [
        { name: "Content-Type", value: "application/json" },
        { name: "Access-Control-Allow-Origin", value: "*" },
      ],
      body: Buffer.from("{}").toString("base64"),
    }).catch(() => {});
  }
  if (msg.id && pending.has(msg.id)) {
    pending.get(msg.id)(msg);
    pending.delete(msg.id);
  }
};
const send = (method, params = {}) =>
  new Promise((resolve, reject) => {
    const i = ++id;
    pending.set(i, (msg) =>
      msg.error
        ? reject(new Error(JSON.stringify(msg.error)))
        : resolve(msg.result),
    );
    ws.send(JSON.stringify({ id: i, method, params }));
  });
const evaluate = async (expression) =>
  (
    await send("Runtime.evaluate", {
      expression,
      returnByValue: true,
      awaitPromise: true,
    })
  ).result.value;

await send("Page.enable");
await send("Runtime.enable");
await send("Fetch.enable", {
  patterns: [{ urlPattern: "http://localhost:8080/*" }],
});
// VIEW_W x VIEW_H is the page viewport (default 1440 x 900), DPR the device
// pixel ratio (default 2), SCALES the pixel scales to capture (default 1,2,3),
// THEMES dark,light. #17 captures the scene column at its 440 px minimum:
//   VIEW_W=440 VIEW_H=1000 DPR=1 SCALES=2 node tools/capture-scene.mjs ...
const VIEW_W = Number(process.env.VIEW_W ?? 1440);
const VIEW_H = Number(process.env.VIEW_H ?? 900);
const DPR = Number(process.env.DPR ?? 2);
const SCALES = (process.env.SCALES ?? "1,2,3").split(",").map(Number);
const THEMES = (process.env.THEMES ?? "dark,light").split(",");
await send("Emulation.setDeviceMetricsOverride", {
  width: VIEW_W,
  height: VIEW_H,
  deviceScaleFactor: DPR,
  mobile: false,
});

const results = [];
try {
  for (const theme of THEMES) {
    for (const scale of SCALES) {
      const url = `${baseUrl}/?inkFixture=${floors}&floor=${current}&garrison=${garrison}&pixelScale=${scale}${process.env.FIXTURE_EXTRA ?? ""}`;
      await send("Page.navigate", { url: "about:blank" });
      await send("Page.navigate", { url });
      await evaluate(`localStorage.setItem('td-theme', '${theme}')`);
      // The app opens on a Campaign Key screen without a key (added after #15).
      await evaluate(
        `localStorage.setItem('td-campaign-key', 'AAAAAAAAAAAAAAAAAAAAAA')`,
      );
      await send("Page.reload");
      let ok = false;
      for (let i = 0; i < 60 && !ok; i++) {
        await sleep(500);
        // Past the Campaign Key gate (a key is already stored above).
        await evaluate(`document.querySelector('.begin')?.click()`).catch(
          () => {},
        );
        ok = await evaluate(
          `!!window.__tower && !!document.querySelector('app-tower-scene canvas') && window.__tower.info().calls > 0`,
        ).catch(() => false);
      }
      if (!ok) {
        console.error(
          await evaluate(
            `JSON.stringify({canvases: document.querySelectorAll('canvas').length, hook: typeof window.__tower, rect: JSON.stringify(document.querySelector('app-tower-scene canvas')?.getBoundingClientRect()), host: JSON.stringify(document.querySelector('app-tower-scene')?.getBoundingClientRect()), webgl: !!document.createElement('canvas').getContext('webgl2'), vis: document.visibilityState, html: document.querySelector('app-tower-scene')?.outerHTML.slice(0, 600), scene: !!document.querySelector('app-tower-scene'), url: location.href, text: document.body.innerText.slice(0, 200)})`,
          ),
        );
      }
      await sleep(3000); // let the camera settle on the current floor
      const info = await evaluate(`JSON.stringify(window.__tower?.info())`);
      const rect = JSON.parse(
        await evaluate(
          `JSON.stringify(document.querySelector('app-tower-scene canvas')?.getBoundingClientRect())`,
        ),
      );
      const shot = await send("Page.captureScreenshot", {
        format: "png",
        clip: {
          x: rect.x,
          y: rect.y,
          width: rect.width,
          height: rect.height,
          scale: 1,
        },
      });
      const name = `${label}-${theme === "dark" ? "night" : "day"}-${scale}x${DPR === 2 ? "" : `-dpr${DPR}`}.png`;
      writeFileSync(join(outDir, name), Buffer.from(shot.data, "base64"));
      results.push({
        name,
        theme,
        scale,
        ok,
        info: JSON.parse(info ?? "null"),
      });
      console.log(JSON.stringify(results[results.length - 1]));
    }
  }
} finally {
  ws.close();
  chrome.kill();
}
