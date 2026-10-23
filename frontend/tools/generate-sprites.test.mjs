import { test } from "node:test";
import assert from "node:assert/strict";
import { inflateSync } from "node:zlib";
import { existsSync, readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { SPRITES, renderSprite, encodePng } from "./generate-sprites.mjs";

const WIDTH = 12;
const HEIGHT = 16;
// The slugs come from catalogue.ts, the list the renderer requests art for.
const catalogue = readFileSync(
  fileURLToPath(new URL("../src/app/three/catalogue.ts", import.meta.url)),
  "utf8",
);
function slugsOf(kind) {
  const body = new RegExp(`${kind}: \\[([^\\]]*)\\]`).exec(catalogue)[1];
  return [...body.matchAll(/'([a-z0-9-]+)'/g)].map((m) => m[1]);
}
const SLUGS = { units: slugsOf("unit"), enemies: slugsOf("enemy") };

const ENEMY_TYPES = [
  "SLIME",
  "DEAD_SOLDIERS",
  "TOWER_BEAST",
  "CHIMERA",
  "CHYMUS_INSECT",
  "STONE_GOLEM",
  "NECROMANCER",
  "DEAD_DRAGON",
  "DRAGON",
];

test("catalogue.ts lists art for all 9 enemy types and every slug has a committed PNG", () => {
  assert.deepEqual(
    SLUGS.enemies.sort(),
    ENEMY_TYPES.map((t) => t.toLowerCase().replace(/_/g, "-")).sort(),
  );
  for (const [kind, slugs] of Object.entries(SLUGS)) {
    for (const slug of slugs) {
      const file = fileURLToPath(
        new URL(`../public/sprites/${kind}/${slug}.png`, import.meta.url),
      );
      assert.ok(existsSync(file), `missing ${kind}/${slug}.png`);
    }
  }
});

test("every unit and enemy slug has a recipe", () => {
  for (const [kind, slugs] of Object.entries(SLUGS)) {
    assert.deepEqual(Object.keys(SPRITES[kind]).sort(), [...slugs].sort());
  }
});

test("sprites are 12 x 16 index images using only clear and the three ramp greys", () => {
  for (const kind of Object.keys(SLUGS)) {
    for (const [slug, recipe] of Object.entries(SPRITES[kind])) {
      const pixels = renderSprite(kind, recipe);
      assert.equal(pixels.length, WIDTH * HEIGHT, slug);
      for (const p of pixels) {
        assert.ok(
          [0, 85, 170, 255].includes(p),
          `${slug}: unexpected grey ${p}`,
        );
      }
    }
  }
});

test("every sprite is visible and has a distinct silhouette", () => {
  const seen = new Map();
  for (const kind of Object.keys(SLUGS)) {
    for (const [slug, recipe] of Object.entries(SPRITES[kind])) {
      const mask = renderSprite(kind, recipe)
        .map((p) => (p ? 1 : 0))
        .join("");
      assert.ok(mask.includes("1"), `${slug} is empty`);
      assert.ok(
        !seen.has(mask),
        `${slug} has the same silhouette as ${seen.get(mask)}`,
      );
      seen.set(mask, slug);
    }
  }
});

test("units carry exactly one amber (slot 3) pixel, at the head", () => {
  for (const [slug, recipe] of Object.entries(SPRITES.units)) {
    const pixels = renderSprite("units", recipe);
    const amber = pixels
      .map((p, i) => (p === 255 ? i : -1))
      .filter((i) => i >= 0);
    assert.equal(amber.length, 1, slug);
    assert.ok(
      Math.floor(amber[0] / WIDTH) <= 7,
      `${slug}: amber pixel not in the upper half`,
    );
  }
});

test("enemies get a one-pixel rim (slot 1) around the bone body", () => {
  for (const [slug, recipe] of Object.entries(SPRITES.enemies)) {
    const pixels = renderSprite("enemies", recipe);
    assert.ok(pixels.includes(85), `${slug} has no rim`);
    assert.ok(pixels.includes(255), `${slug} has no bone body`);
  }
});

test("encodePng writes a decodable 12 x 16 grayscale+alpha PNG", () => {
  const pixels = renderSprite("units", SPRITES.units.warrior);
  const png = encodePng(WIDTH, HEIGHT, pixels);
  assert.deepEqual([...png.subarray(0, 8)], [137, 80, 78, 71, 13, 10, 26, 10]);
  assert.equal(png.readUInt32BE(16), WIDTH);
  assert.equal(png.readUInt32BE(20), HEIGHT);
  assert.equal(png[24], 8); // bit depth
  assert.equal(png[25], 4); // grayscale + alpha
  // locate IDAT and verify the decoded scanlines
  let off = 8;
  let idat;
  while (off < png.length) {
    const len = png.readUInt32BE(off);
    if (png.toString("latin1", off + 4, off + 8) === "IDAT")
      idat = png.subarray(off + 8, off + 8 + len);
    off += 12 + len;
  }
  const raw = inflateSync(idat);
  assert.equal(raw.length, HEIGHT * (1 + WIDTH * 2));
  for (let y = 0; y < HEIGHT; y++) {
    const row = y * (1 + WIDTH * 2);
    assert.equal(raw[row], 0);
    for (let x = 0; x < WIDTH; x++) {
      const p = pixels[y * WIDTH + x];
      assert.equal(raw[row + 1 + x * 2], p);
      assert.equal(raw[row + 2 + x * 2], p ? 255 : 0);
    }
  }
});
