// EGG01 in-page extractor (paste into the ChatGPT tab via javascript_tool): await __ex(1000,16,12) then __g(i) per egg -> "i|w|h|palette|RLE" lines for decode.py.
// Per output pixel: alpha = magenta coverage < 50%, colour = median of the centre half of the cell; k-means 12 (farthest-point init); RLE "c*n ".
window.__ex = async function (minW, S, K) {
  const img = [...document.querySelectorAll('main img')].filter(i => i.naturalWidth >= minW).pop();
  await img.decode();
  const W = img.naturalWidth, H = img.naturalHeight, c = document.createElement('canvas');
  c.width = W; c.height = H;
  const g = c.getContext('2d'); g.drawImage(img, 0, 0);
  const d = g.getImageData(0, 0, W, H).data, m = new Uint8Array(W * H);
  for (let p = 0; p < W * H; p++) m[p] = (d[p*4] > 170 && d[p*4+1] < 110 && d[p*4+2] > 170) ? 0 : 1;
  const colF = new Float32Array(W), rowF = new Float32Array(H);
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) if (m[y*W+x]) { colF[x]++; rowF[y]++; }
  const runs = (a, n, th) => { const r = []; let s = -1;
    for (let i = 0; i <= n; i++) { const on = i < n && a[i] > th;
      if (on && s < 0) s = i; if (!on && s >= 0) { if (i - s > 20) r.push([s, i]); s = -1; } } return r; };
  const cols = runs(colF, W, 2), rows = runs(rowF, H, 2), tiles = [];
  const dist = (p, q) => (p[0]-q[0])**2 + (p[1]-q[1])**2 + (p[2]-q[2])**2;
  const AL = '0123456789abcdefghijklmnopqrstuvwxyz';
  const med = a => { a.sort((x, y) => x - y); return a[a.length >> 1]; };
  for (const [y0, y1] of rows) for (const [x0, x1] of cols) {
    let bx0 = 1e9, by0 = 1e9, bx1 = -1, by1 = -1, n = 0;
    for (let y = y0; y < y1; y++) for (let x = x0; x < x1; x++) if (m[y*W+x]) { n++;
      bx0 = Math.min(bx0, x); bx1 = Math.max(bx1, x); by0 = Math.min(by0, y); by1 = Math.max(by1, y); }
    if (n < 800) continue;
    const cw = bx1 - bx0 + 1, ch = by1 - by0 + 1, s = Math.max(cw, ch);
    const tw = Math.max(1, Math.round(cw * S / s)), th = Math.max(1, Math.round(ch * S / s)), px = [];
    for (let ty = 0; ty < th; ty++) for (let tx = 0; tx < tw; tx++) {
      const sx0 = bx0 + Math.floor(tx * cw / tw), sx1 = Math.max(sx0 + 1, bx0 + Math.floor((tx + 1) * cw / tw));
      const sy0 = by0 + Math.floor(ty * ch / th), sy1 = Math.max(sy0 + 1, by0 + Math.floor((ty + 1) * ch / th));
      let a = 0, t = 0;
      for (let y = sy0; y < sy1; y++) for (let x = sx0; x < sx1; x++) { t++; if (m[y*W+x]) a++; }
      if (a / t < 0.5) { px.push(null); continue; }
      const qx0 = sx0 + ((sx1 - sx0) >> 2), qx1 = sx1 - ((sx1 - sx0) >> 2), qy0 = sy0 + ((sy1 - sy0) >> 2), qy1 = sy1 - ((sy1 - sy0) >> 2);
      let R = [], G = [], B = [];
      for (let pass = 0; pass < 2 && !R.length; pass++) {
        const X0 = pass ? sx0 : qx0, X1 = pass ? sx1 : Math.max(qx1, qx0 + 1), Y0 = pass ? sy0 : qy0, Y1 = pass ? sy1 : Math.max(qy1, qy0 + 1);
        for (let y = Y0; y < Y1; y++) for (let x = X0; x < X1; x++) { const p = y*W+x; if (m[p]) { R.push(d[p*4]); G.push(d[p*4+1]); B.push(d[p*4+2]); } }
      }
      px.push([med(R), med(G), med(B)]);
    }
    const solid = px.filter(Boolean), C = [solid[0].slice()];
    while (C.length < Math.min(K, solid.length)) { let best = null, bd = -1;
      for (const p of solid) { let md = 1e18; for (const q of C) md = Math.min(md, dist(p, q)); if (md > bd) { bd = md; best = p; } }
      if (bd < 1) break; C.push(best.slice()); }
    const lab = new Array(px.length).fill(-1);
    for (let it = 0; it < 12; it++) { const acc = C.map(() => [0, 0, 0, 0]);
      px.forEach((p, i) => { if (!p) return; let bi = 0, bd = 1e18;
        C.forEach((q, j) => { const dd = dist(p, q); if (dd < bd) { bd = dd; bi = j; } });
        lab[i] = bi; const A = acc[bi]; A[0] += p[0]; A[1] += p[1]; A[2] += p[2]; A[3]++; });
      acc.forEach((A, j) => { if (A[3]) C[j] = [A[0]/A[3], A[1]/A[3], A[2]/A[3]]; }); }
    const idx = lab.map(l => l < 0 ? '.' : AL[l]).join('');
    let rle = '', i = 0;
    while (i < idx.length) { let j = i; while (j < idx.length && idx[j] === idx[i]) j++;
      rle += (j - i >= 3) ? idx[i] + '*' + (j - i) + ' ' : idx.slice(i, j); i = j; }
    tiles.push({ box: [bx0, by0, bx1, by1], w: tw, h: th,
      pal: C.map(q => q.map(v => Math.round(v).toString(16).padStart(2, '0')).join('')).join(','), rle });
  }
  window.__T = tiles;
  return { W, H, cols: cols.length, rows: rows.length, n: tiles.length, boxes: tiles.map(t => t.box.join(' ') + ' ' + t.w + 'x' + t.h).join('; ') };
};
window.__g = i => { const T = window.__T[i]; return i + '|' + T.w + '|' + T.h + '|' + T.pal + '|' + T.rle; };
