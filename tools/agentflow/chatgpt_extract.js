// ChatGPT sheet extraction (paste into the ChatGPT tab via Claude in Chrome javascript_tool).
// Generated images are blob: URLs that the extension cannot download and tool output is cut at ~1000 chars,
// so each icon is reduced in-page: magenta background mask -> column/row runs -> icon bbox -> box-average to
// 32 px on the longest side (square=true: 32x32 for opaque block tiles) -> k-means 20 colours.
//   const r = await window.__extract(1500, true);   // last <img> at least 1500 px wide; returns the boxes
//   window.__get(i, 0); window.__get(i, 1);          // one JSON line per half (top / bottom rows) of icon i
// Save the lines to a .jsonl and run tools/agentflow/rebuild_sheet.py, then sheets.py import as usual.
window.__extract = async function (minW, square) {
  const img = [...document.querySelectorAll('main img')].filter(i => i.naturalWidth >= minW).pop();
  await img.decode();
  const W = img.naturalWidth, H = img.naturalHeight, c = document.createElement('canvas');
  c.width = W; c.height = H;
  const g = c.getContext('2d'); g.drawImage(img, 0, 0);
  const d = g.getImageData(0, 0, W, H).data;
  const m = new Uint8Array(W * H);
  for (let p = 0; p < W * H; p++) m[p] = (d[p * 4] > 170 && d[p * 4 + 1] < 110 && d[p * 4 + 2] > 170) ? 0 : 1;
  const colF = new Float32Array(W), rowF = new Float32Array(H);
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) if (m[y * W + x]) { colF[x]++; rowF[y]++; }
  const runs = (a, n, th) => {
    const r = []; let s = -1;
    for (let i = 0; i <= n; i++) {
      const on = i < n && a[i] > th;
      if (on && s < 0) s = i;
      if (!on && s >= 0) { if (i - s > 20) r.push([s, i]); s = -1; }
    }
    return r;
  };
  const cols = runs(colF, W, 2), rows = runs(rowF, H, 2), tiles = [];
  const dist = (p, q) => (p[0] - q[0]) ** 2 + (p[1] - q[1]) ** 2 + (p[2] - q[2]) ** 2;
  const AL = '0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ';
  for (const [y0, y1] of rows) for (const [x0, x1] of cols) {
    let bx0 = 1e9, by0 = 1e9, bx1 = -1, by1 = -1, n = 0;
    for (let y = y0; y < y1; y++) for (let x = x0; x < x1; x++) if (m[y * W + x]) {
      n++; bx0 = Math.min(bx0, x); bx1 = Math.max(bx1, x); by0 = Math.min(by0, y); by1 = Math.max(by1, y);
    }
    if (n < 800) continue;
    const cw = bx1 - bx0 + 1, ch = by1 - by0 + 1, s = Math.max(cw, ch), S = 32;
    const tw = square ? S : Math.max(1, Math.round(cw * S / s)), th = square ? S : Math.max(1, Math.round(ch * S / s));
    const px = [];
    for (let ty = 0; ty < th; ty++) for (let tx = 0; tx < tw; tx++) {
      const sx0 = bx0 + Math.floor(tx * cw / tw), sx1 = bx0 + Math.floor((tx + 1) * cw / tw);
      const sy0 = by0 + Math.floor(ty * ch / th), sy1 = by0 + Math.floor((ty + 1) * ch / th);
      let r = 0, gg = 0, b = 0, a = 0, t = 0;
      for (let y = sy0; y < Math.max(sy1, sy0 + 1); y++) for (let x = sx0; x < Math.max(sx1, sx0 + 1); x++) {
        t++; const p = y * W + x;
        if (m[p]) { a++; r += d[p * 4]; gg += d[p * 4 + 1]; b += d[p * 4 + 2]; }
      }
      px.push(a / t >= 0.5 ? [r / a, gg / a, b / a] : null);
    }
    const solid = px.filter(Boolean), K = Math.min(20, solid.length), C = [solid[0].slice()];
    while (C.length < K) {           // farthest-point init
      let best = null, bd = -1;
      for (const p of solid) { let md = 1e18; for (const q of C) md = Math.min(md, dist(p, q)); if (md > bd) { bd = md; best = p; } }
      if (bd < 1) break;
      C.push(best.slice());
    }
    const lab = new Array(px.length).fill(-1);
    for (let it = 0; it < 12; it++) {
      const acc = C.map(() => [0, 0, 0, 0]);
      px.forEach((p, i) => {
        if (!p) return;
        let bi = 0, bd = 1e18;
        C.forEach((q, j) => { const dd = dist(p, q); if (dd < bd) { bd = dd; bi = j; } });
        lab[i] = bi; const A = acc[bi]; A[0] += p[0]; A[1] += p[1]; A[2] += p[2]; A[3]++;
      });
      acc.forEach((A, j) => { if (A[3]) C[j] = [A[0] / A[3], A[1] / A[3], A[2] / A[3]]; });
    }
    tiles.push({
      box: [bx0, by0, bx1, by1], w: tw, h: th,
      pal: C.map(q => q.map(v => Math.round(v).toString(16).padStart(2, '0')).join('')).join(','),
      idx: lab.map(l => l < 0 ? '.' : AL[l]).join(''),
    });
  }
  window.__T = tiles;
  return { W, H, cols: cols.length, rows: rows.length, tiles: tiles.map(t => [t.box.join(' '), t.w, t.h]) };
};
window.__get = (t, half) => {
  const T = window.__T[t], h = Math.ceil(T.h / 2) * T.w;
  return JSON.stringify({ t, half, w: T.w, h: T.h, pal: half ? undefined : T.pal, idx: half ? T.idx.slice(h) : T.idx.slice(0, h) });
};
