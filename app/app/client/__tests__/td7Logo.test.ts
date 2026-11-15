// TD-7: every Expo icon in assets/ is replaced by a square CometFit logo icon and app.json points at existing files.
// Reads width/height from the PNG IHDR header (bytes 16..23), so no image library is needed.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, existsSync } from 'node:fs';
import { join } from 'node:path';

const CLIENT = join(import.meta.dirname, '..');
const ASSETS = join(CLIENT, 'assets');

function pngSize(file: string): { w: number; h: number } {
  const b = readFileSync(file);
  assert.equal(b.subarray(1, 4).toString('latin1'), 'PNG', `${file} is a PNG`);
  return { w: b.readUInt32BE(16), h: b.readUInt32BE(20) };
}

const ICONS = ['icon.png', 'splash-icon.png', 'favicon.png', 'android-icon-foreground.png',
  'android-icon-background.png', 'android-icon-monochrome.png'];

test('TD-7 §3: all icons are square', () => {
  for (const f of ICONS) {
    const { w, h } = pngSize(join(ASSETS, f));
    assert.equal(w, h, `${f} is ${w}x${h}`);
  }
});

test('TD-7 §2: the default Expo template icons are gone (byte sizes of the template files)', () => {
  const template: Record<string, number> = {
    'icon.png': 393493, 'favicon.png': 1129, 'splash-icon.png': 17547, 'android-icon-foreground.png': 78796,
    'android-icon-background.png': 17549, 'android-icon-monochrome.png': 4140,
  };
  for (const [f, oldSize] of Object.entries(template)) {
    assert.notEqual(readFileSync(join(ASSETS, f)).length, oldSize, `${f} still looks like the Expo template`);
  }
});

test('TD-7: logo for the sign-in flow keeps the logo aspect ratio (938 x 598 source)', () => {
  const { w, h } = pngSize(join(ASSETS, 'logo.png'));
  assert.ok(Math.abs(w / h - 938 / 598) < 0.01, `logo.png is ${w}x${h}`);
});

test('TD-7: app.json icon paths exist and use the off-white background', () => {
  const cfg = JSON.parse(readFileSync(join(CLIENT, 'app.json'), 'utf8')).expo;
  const paths = [cfg.icon, cfg.splash.image, cfg.web.favicon, cfg.android.adaptiveIcon.foregroundImage,
    cfg.android.adaptiveIcon.backgroundImage, cfg.android.adaptiveIcon.monochromeImage];
  for (const p of paths) assert.ok(existsSync(join(CLIENT, p)), `${p} exists`);
  assert.equal(cfg.splash.backgroundColor, '#F6F6F1');
  assert.equal(cfg.android.adaptiveIcon.backgroundColor, '#F6F6F1');
});
