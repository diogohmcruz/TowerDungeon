// Guards the panel tokens (docs/brainstorm/village-resources.md 8.4, issue #14).
// Run with `npm run sprites:test` or `npm run theme:test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync, readdirSync, statSync, existsSync } from "node:fs";
import { brotliDecompressSync } from "node:zlib";
import { dirname, join, relative } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const stylesPath = join(root, "src/styles.css");
const css = readFileSync(stylesPath, "utf8");

// ---- tokens -------------------------------------------------------------

function block(startMarker) {
  const start = css.indexOf(startMarker);
  assert.ok(start >= 0, `missing ${startMarker} in styles.css`);
  return css.slice(start, css.indexOf("\n}\n", start));
}

function tokens(text) {
  const out = {};
  for (const m of text.matchAll(/--([a-z-]+):\s*(#[0-9a-fA-F]{6})/g)) {
    out[m[1]] = m[2];
  }
  return out;
}

const dark = tokens(block(':root,\n:root[data-theme="dark"]'));
const light = { ...dark, ...tokens(block(':root[data-theme="light"]')) };

function luminance(hex) {
  const c = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255);
  const [r, g, b] = c.map((v) =>
    v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4,
  );
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

export function contrast(a, b) {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (hi + 0.05) / (lo + 0.05);
}

function atLeast(theme, fg, bg, min) {
  const ratio = contrast(theme[fg], theme[bg]);
  // The design states ratios to one decimal, so allow rounding of 0.05.
  assert.ok(
    ratio >= min - 0.05,
    `--${fg} ${theme[fg]} on --${bg} ${theme[bg]} is ${ratio.toFixed(2)}:1, stated ${min}:1`,
  );
}

test("dark tokens are the settled set", () => {
  const expected = {
    void: "#07090f",
    ink: "#0e1424",
    panel: "#0b0e17",
    rule: "#2e3546",
    text: "#aab3c7",
    bone: "#e7dcc4",
    muted: "#7b869c",
    cold: "#8fb7ff",
    ember: "#7a2230",
    "ember-dim": "#4a1620",
    "ember-text": "#d0556a",
  };
  for (const [k, v] of Object.entries(expected)) {
    assert.equal(dark[k]?.toLowerCase(), v, `--${k}`);
  }
});

test("dark contrast on the panel meets the stated ratios", () => {
  atLeast(dark, "text", "panel", 9.2);
  atLeast(dark, "bone", "panel", 14.2);
  atLeast(dark, "muted", "panel", 5.3);
  atLeast(dark, "ember-text", "panel", 4.8);
});

test("light theme is overcast ash with crimson loss text", () => {
  assert.equal(light["ember-text"].toLowerCase(), "#7a2230");
  assert.equal(light.ember.toLowerCase(), "#7a2230");
  for (const k of ["void", "panel"]) {
    const [r, g, b] = [1, 3, 5].map((i) =>
      parseInt(light[k].slice(i, i + 2), 16),
    );
    assert.ok(
      r >= 0xb0 && r <= 0xcc && Math.max(r, g, b) - Math.min(r, g, b) < 16,
      `--${k} is not ash`,
    );
  }
});

test("light contrast meets the stated ratios", () => {
  atLeast(light, "ember-text", "void", 5.3);
  atLeast(light, "ember-text", "panel", 5.3);
  for (const bg of ["panel", "void"]) {
    atLeast(light, "text", bg, 7);
    atLeast(light, "bone", bg, 7);
    atLeast(light, "muted", bg, 4.5);
  }
});

// ---- forbidden constructs in component styles -----------------------------

function walk(dir, exts, out = []) {
  for (const name of readdirSync(dir)) {
    const p = join(dir, name);
    if (statSync(p).isDirectory()) walk(p, exts, out);
    else if (exts.some((e) => p.endsWith(e))) out.push(p);
  }
  return out;
}

test("component styles carry no shadow, radius, gradient or --positive", () => {
  const files = walk(join(root, "src/app"), [".scss", ".css"]).filter(
    (f) => !f.includes("/tower-scene/") && !f.includes("/three/"),
  );
  assert.ok(files.length > 10);
  const banned =
    /box-shadow|text-shadow|border-radius|gradient\(|--positive|--negative|#4caf7d/;
  for (const f of files) {
    const text = readFileSync(f, "utf8");
    assert.ok(
      !banned.test(text),
      `${relative(root, f)} has a banned construct`,
    );
    assert.ok(
      !/#[0-9a-fA-F]{3,8}\b/.test(text),
      `${relative(root, f)} hard-codes a colour`,
    );
  }
  assert.ok(!/--positive|border-radius|box-shadow/.test(css), "styles.css");
});

test("every var(--x) used in components is defined", () => {
  const defined = new Set(
    [...css.matchAll(/--([a-z0-9-]+):/g)].map((m) => m[1]),
  );
  const files = walk(join(root, "src/app"), [".scss"]);
  for (const f of files) {
    const text = readFileSync(f, "utf8");
    const local = new Set(
      [...text.matchAll(/--([a-z0-9-]+):/g)].map((m) => m[1]),
    );
    for (const m of text.matchAll(/var\(--([a-z0-9-]+)\s*\)/g)) {
      assert.ok(
        defined.has(m[1]) || local.has(m[1]),
        `${relative(root, f)} uses undefined --${m[1]}`,
      );
    }
  }
});

test("the tower display no longer prints the Summit", () => {
  const html = readFileSync(
    join(root, "src/app/components/tower/tower-display.component.html"),
    "utf8",
  );
  assert.ok(!html.includes("maxFloor"));
  assert.ok(!html.includes("depth-max"));
});

test("reduced motion stops transitions and animations", () => {
  const m = css.match(
    /@media \(prefers-reduced-motion: reduce\)\s*\{[\s\S]*?\n\}/,
  );
  assert.ok(m, "no reduced-motion block");
  assert.match(m[0], /transition:\s*none\s*!important/);
  assert.match(m[0], /animation:\s*none\s*!important/);
});

test("UI transitions are at most 200 ms", () => {
  const files = [stylesPath, ...walk(join(root, "src/app"), [".scss"])];
  for (const f of files) {
    for (const m of readFileSync(f, "utf8").matchAll(/(\d*\.?\d+)(ms|s)\b/g)) {
      const ms = m[2] === "s" ? parseFloat(m[1]) * 1000 : parseFloat(m[1]);
      assert.ok(ms <= 200, `${relative(root, f)} has a ${m[0]} duration`);
    }
  }
});

// ---- font: self-hosted, licensed, covers the UI glyphs ------------------

const fontDir = join(root, "public/fonts/fusion-pixel");
const fontFile = join(
  fontDir,
  "fusion-pixel-12px-proportional-latin-subset.woff2",
);

test("font is self-hosted with its licence and no CDN", () => {
  assert.ok(existsSync(fontFile));
  const ofl = readFileSync(join(fontDir, "OFL.txt"), "utf8");
  assert.match(ofl, /SIL OPEN FONT LICENSE Version 1\.1/);
  assert.match(
    css,
    /fonts\/fusion-pixel\/fusion-pixel-12px-proportional-latin-subset\.woff2/,
  );
  assert.ok(!/https?:\/\//.test(css), "styles.css loads from a URL");
  const index = readFileSync(join(root, "src/index.html"), "utf8");
  assert.ok(!/fonts\.googleapis|fonts\.gstatic|cdn/i.test(index));
});

const KNOWN = { 0: "cmap", 3: "hmtx", 10: "glyf", 11: "loca" };

function readBase128(buf, pos) {
  let v = 0;
  for (let i = 0; i < 5; i++) {
    const b = buf[pos.i++];
    v = v * 128 + (b & 0x7f);
    if (!(b & 0x80)) return v;
  }
  throw new Error("bad UIntBase128");
}

function woff2Cmap(buf) {
  assert.equal(buf.toString("latin1", 0, 4), "wOF2");
  const numTables = buf.readUInt16BE(12);
  const compressedSize = buf.readUInt32BE(20);
  const pos = { i: 48 };
  const tables = [];
  for (let t = 0; t < numTables; t++) {
    const flags = buf[pos.i++];
    const idx = flags & 0x3f;
    const version = flags >> 6;
    let tag = KNOWN[idx] ?? `#${idx}`;
    if (idx === 63) {
      tag = buf.toString("latin1", pos.i, pos.i + 4);
      pos.i += 4;
    }
    const orig = readBase128(buf, pos);
    let length = orig;
    const transformed =
      tag === "glyf" || tag === "loca"
        ? version === 0
        : tag === "hmtx"
          ? version === 1
          : version !== 0;
    if (transformed) length = readBase128(buf, pos);
    tables.push({ tag, length });
  }
  const data = brotliDecompressSync(
    buf.subarray(pos.i, pos.i + compressedSize),
  );
  let off = 0;
  for (const t of tables) {
    if (t.tag === "cmap") return parseCmap(data.subarray(off, off + t.length));
    off += t.length;
  }
  throw new Error("no cmap");
}

function parseCmap(d) {
  const n = d.readUInt16BE(2);
  const set = new Set();
  for (let i = 0; i < n; i++) {
    const platform = d.readUInt16BE(4 + i * 8);
    const sub = d.readUInt32BE(8 + i * 8);
    const fmt = d.readUInt16BE(sub);
    if (platform === 3 && fmt === 4) {
      const segX2 = d.readUInt16BE(sub + 6);
      const endO = sub + 14;
      const startO = endO + segX2 + 2;
      const deltaO = startO + segX2;
      const rangeO = deltaO + segX2;
      for (let s = 0; s < segX2 / 2; s++) {
        const end = d.readUInt16BE(endO + s * 2);
        const start = d.readUInt16BE(startO + s * 2);
        const delta = d.readInt16BE(deltaO + s * 2);
        const ro = d.readUInt16BE(rangeO + s * 2);
        for (let c = start; c <= end && c !== 0xffff; c++) {
          let g;
          if (ro === 0) g = (c + delta) & 0xffff;
          else {
            g = d.readUInt16BE(rangeO + s * 2 + ro + (c - start) * 2);
            if (g !== 0) g = (g + delta) & 0xffff;
          }
          if (g !== 0) set.add(c);
        }
      }
    } else if (platform === 3 && fmt === 12) {
      const groups = d.readUInt32BE(sub + 12);
      for (let g = 0; g < groups; g++) {
        const s = d.readUInt32BE(sub + 16 + g * 12);
        const e = d.readUInt32BE(sub + 20 + g * 12);
        for (let c = s; c <= e; c++) set.add(c);
      }
    }
  }
  return set;
}

test("the font covers every glyph the pixel-face UI text uses", () => {
  const cmap = woff2Cmap(readFileSync(fontFile));
  // Headings and numerals: Latin, digits, punctuation, plus + - x . , / : ( )
  const needed = new Set();
  for (let c = 0x20; c <= 0x7e; c++) needed.add(c);
  for (const ch of "×·–—…→‘’“”•é") {
    needed.add(ch.codePointAt(0));
  }
  const missing = [...needed].filter((c) => !cmap.has(c));
  assert.deepEqual(
    missing.map((c) => c.toString(16)),
    [],
  );
});

test("headings and numerals in templates stay inside the font's coverage", () => {
  // The pixel face has no U+2212 or U+2713; templates use ASCII "-" instead.
  const cmap = woff2Cmap(readFileSync(fontFile));
  assert.ok(!cmap.has(0x2212));
  const html = readFileSync(
    join(root, "src/app/components/village-management/village-management.html"),
    "utf8",
  );
  assert.ok(!html.includes("−"));
});
