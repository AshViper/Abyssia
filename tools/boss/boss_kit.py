# -*- coding: utf-8 -*-
"""ボスの GeckoLib モデル（geo.json）を組み立てるための共通の道具。

ParaInfaction（同じ作者の別 mod）の `tools/boss_kit.py` を複写したもの。中身は同じで、
Abyssia では `tools/boss/<ボス>_model/build.py` から `sys.path` 経由で読む。
直すときは ParaInfaction 側と食い違わないよう、両方に入れる。
（以下の説明は元の文のまま。「原始級ボス」は ParaInfaction のボス）

<h2>なぜ共通化するか</h2>
ボスごとに build.py を持っていると、同じ間違いを9回書くことになる。実際、
苔淵の古鱗を作り直したときに次の2つが見つかった。どちらも黙って形が壊れる類で、
絵を出して見比べるまで気付けない。

  1. <b>左右の符号</b>。geo の rz は正で -X へ振れるので、左（+X 側）の連なりに
     そのまま正の角を渡すと<b>正中線を越えて交差する</b>（触手が X 字に組む）。
  2. <b>先端の飾りの座標</b>。連なりの先に付ける発光体などを絶対座標で書くと、
     連なりがどこへ伸びていても<b>モデル原点に取り残される</b>。

ここへ集めておけば、直すのは1回で済む。

<h2>連なりは関節ごとに Bone を割る</h2>
{@link Kit.chain} は連なりを<b>まっすぐ伸びた姿勢で組み立て</b>、曲がりは各 Bone の
静止姿勢回転に預ける。1本の Bone に弧を丸ごと持たせると、先端を曲げたつもりで
根元から回る。肋・触手・脚・尾はすべてこの形にする。

<h2>座標の約束</h2>
Z- が前、+Y が上、+X が左。単位は 1/16 ブロック。
GeckoLib の焼き込みは X を反転し、回転を (-rx, -ry, +rz) として持つ。頂点へ掛かる順は
<b>X→Y→Z</b>（行列としては Rz・Ry・Rx。`RenderUtil.rotateMatrixAround*` が Z, Y, X の順に
`mulPose` するので、最後に掛けた X が頂点へは最初に効く）。1軸だけなら順番は関係ない:

| 向き | 効果 |
| --- | --- |
| `rx > 0` | 前（Z-）が下がる |
| `ry > 0` | 前が -X 側（右）へ向く |
| `rz > 0` | 上が +X 側（左）へ倒れる。垂れたものの先は -X 側（右）へ振れる |

{@link Kit.chain} の `rz` だけは<b>正で外開き</b>と読めるように符号を吸収してある。

<h2>2軸以上を回すなら、角度ではなく点で書く</h2>
2軸以上を同時に回すと、どの軸が先に効くかで向きが変わる。頭の中で3つの角度を
合成するのは間違いの元なので、斜めの板や棒は {@link Kit.slab} / {@link Kit.beam} /
{@link Kit.loft} / {@link Kit.fan} へ<b>「どこからどこへ」「どちらを向く」を点と方向で渡す</b>。
角度は {@link euler_from_axes} が GeckoLib の順番どおりに解く。

どうしても「この軸で回してから、次にこの軸で回す」と書きたいときは {@link euler_seq} へ
<b>頂点に効かせたい順</b>で渡す。{@link Kit.ring_y} と {@link Kit.dome_ring} は以前
`rot=(0, 円周角, 倒し角)` をそのまま書いていて、ゲーム内では板が外へ倒れずに<b>環ごと
片側へ傾いていた</b>（腐森の根主の萼）。いまは {@link euler_seq} で解いてある。

同じ2軸でも、意味は並べ方で変わる:

| 書き方 | GeckoLib での意味 | 例 |
| --- | --- | --- |
| `(傾き, 0, 円周角)` | 真上で傾けてから円周へ回す。どの板も同じ傾き | {@link Kit.shell} の flare、{@link Kit.radial} の lean |
| `(0, 円周角, 倒し角)` | 円周へ回してから<b>全体を</b> Z で倒す。環ごと傾く | 書いてはいけない |
"""
import math
import random
import zlib


# ============================================================ 素材と箱
class Kit:
    """1体ぶんのモデルを組み立てる道具。テクスチャアトラスと結び付いている。

    atlas はボスごとに違うので、その module を渡して作る。
    stretched は絵柄を面いっぱいに貼る領域（シダ・牙・眼）、
    top_anchored は上端から切り出す領域（外縁が破れたヒレ膜）。
    """

    def __init__(self, atlas, stretched=(), top_anchored=()):
        self.atlas = atlas
        self.stretched = set(stretched)
        self.top_anchored = set(top_anchored)

    @staticmethod
    def _rand(key):
        return random.Random(zlib.crc32(str(key).encode('utf-8')))

    def cube(self, origin, size, region, rot=None, pivot=None, tone=None, key=None):
        """1つの箱。面ごとに実寸ぶんだけ素材を切り出すので、引き伸ばしが起きない。

        tone は素材領域のどのあたりを使うか（0で苔むした側、1で乾いた側）。
        rot / pivot を与えると、その点を中心に箱ごと傾く。
        """
        w, h, d = (float(v) for v in size)
        rx, ry, rw, rh = region
        rnd = self._rand(key or (origin, size, region))
        stretch = region in self.stretched
        top = region in self.top_anchored

        def fuv(fw, fh):
            if stretch:
                return {"uv": [rx, ry], "uv_size": [rw, rh]}
            uw = min(max(fw, 1.0), rw)
            uh = min(max(fh, 1.0), rh)
            ox = rnd.uniform(0, rw - uw) if rw > uw else 0.0
            if top:
                oy = 0.0
            elif tone is None:
                oy = rnd.uniform(0, rh - uh) if rh > uh else 0.0
            else:
                oy = max(0.0, min(rh - uh, tone * (rh - uh)))
            return {"uv": [round(rx + ox, 2), round(ry + oy, 2)],
                    "uv_size": [round(uw, 2), round(uh, 2)]}

        c = {
            "origin": [round(v, 2) for v in origin],
            "size": [round(v, 2) for v in size],
            "uv": {
                "north": fuv(w, h), "south": fuv(w, h),
                "east": fuv(d, h), "west": fuv(d, h),
                "up": fuv(w, d), "down": fuv(w, d),
            },
        }
        # 厚み 0 の板は表と裏が同じ平面に重なる。裏は「裏から見て正像」なので、表から見ると
        # 鏡写しになり、しかも面ごとに別の場所を切り出しているから、破れや孔のある膜は
        # 表の孔を裏の絵が埋めてしまう（図は表しか描かないので気付けない）。
        # 裏には表と同じ切り出しを鏡写しで貼り、どちらから見ても同じ1枚にする
        uv = c["uv"]
        if w == 0:
            uv["east"] = _mirrored(uv["west"], 0)
        if d == 0:
            uv["south"] = _mirrored(uv["north"], 0)
        if h == 0:
            uv["down"] = _mirrored(uv["up"], 1)     # 上下の面は、透かすと上下が逆
        if rot and any(abs(v) > 1e-6 for v in rot):
            c["rotation"] = [round(v, 2) for v in rot]
            c["pivot"] = [round(v, 2) for v in (pivot if pivot else origin)]
        return c

    @staticmethod
    def bone(store, name, pivot, cubes=(), parent=None, rot=None):
        b = {"name": name, "pivot": [round(v, 2) for v in pivot]}
        if parent:
            b["parent"] = parent
        if rot and any(abs(v) > 1e-6 for v in rot):
            b["rotation"] = [round(v, 2) for v in rot]
        b["cubes"] = list(cubes)
        store.append(b)
        return name

    # ==================== 関節の連なり ====================

    def chain(self, store, prefix, parent, start, segs, axis='down', s=1, key=None):
        """関節ごとに Bone を割った連なりを生やす。

        <p>組み立ては<b>まっすぐ伸びた姿勢</b>で行い、曲がりは各 Bone の静止姿勢回転に
        預ける。GeckoLib は親の回転を子へ伝えるので、これだけで弧になる。Pivot が関節に
        乗るため、アニメーション側は「n節目を何度曲げる」と素直に書ける。</p>

        axis: 'down'(-Y へ伸びる) / 'up'(+Y) / 'back'(+Z) / 'fwd'(-Z)
        s: 左右。+1 が左（+X 側）。rz は正で外開き。

        <p>「外開き」に要る geo の符号は<b>伸びる向きで反転する</b>。同じ rz を掛けても、
        下へ伸びる節は -X 側へ、上へ伸びる節は +X 側へ振れるため。ここを揃えておかないと、
        上向きの触角だけが正中線を越えて X 字に交差する。</p>
        """
        # 上へ伸びる連なりだけ、外開きの符号が逆になる
        zsign = 1 if axis == 'up' else -1
        key = key or prefix
        x, y, z = start
        names = []
        prev = (0.0, 0.0, 0.0)
        for i, sg in enumerate(segs):
            length, w, t = sg["length"], sg["w"], sg["t"]
            if axis == 'down':
                origin, size = (x - w / 2, y - length, z - t / 2), (w, length, t)
                nxt = (x, y - length, z)
            elif axis == 'up':
                # 上へ伸びる連なり。触角・角・帆の支柱。'down' を rx で起こそうとすると
                # 前下へ回り込んでしまうので、伸びる向きそのものを変える
                origin, size = (x - w / 2, y, z - t / 2), (w, length, t)
                nxt = (x, y + length, z)
            elif axis == 'back':
                origin, size = (x - w / 2, y - t / 2, z), (w, t, length)
                nxt = (x, y, z + length)
            else:  # fwd
                origin, size = (x - w / 2, y - t / 2, z - length), (w, t, length)
                nxt = (x, y, z - length)
            cubes = [self.cube(origin, size, sg["region"], tone=sg["tone"], key=f"{key}{i}")]
            cubes += sg["extra"]
            if sg["cap"]:
                cubes += sg["cap"](*nxt)
            name = self.bone(store, f"{prefix}_{i}", (x, y, z), cubes,
                             parent=parent if i == 0 else names[-1],
                             rot=(sg["rx"] - prev[0], s * (sg["ry"] - prev[1]),
                                  zsign * s * (sg["rz"] - prev[2])))
            names.append(name)
            prev = (sg["rx"], sg["ry"], sg["rz"])
            x, y, z = nxt
        return names

    # ==================== 断面の環 ====================

    def shell(self, z, cy, a, b, depth, key, span=95.0, n=11, thick=5.0, flare=-5,
              region=None, tone=0.5, top_region=None, belly_region=None, cx=0.0):
        """楕円の縁に沿って板を並べる。

        <p>板を真上に1枚だけ置き、断面の中心まわりに回して並べる。箱を積むより
        遥かに丸い輪郭になる。span を 180 にすると全周を閉じる。</p>
        """
        out = []
        for i in range(n):
            phi = -span + 2 * span * i / (n - 1)
            r = ellipse_r(a, b, phi)
            step = 2 * span / (n - 1)
            w = 2 * math.pi * r * (step / 360.0) * 1.5 + 2.0
            up = math.cos(math.radians(phi))
            reg = region
            if top_region is not None and up > 0.80:
                reg = top_region
            elif belly_region is not None and up < -0.55:
                reg = belly_region
            out.append(self.cube((cx - w / 2, cy + r - thick, z), (w, thick, depth), reg,
                                 rot=(flare, 0, phi), pivot=(cx, cy, z + depth / 2),
                                 tone=tone, key=f"{key}{i}"))
        return out

    def inner_ring(self, z, cy, a, b, depth, key, n=8, thick=6.0, region=None, cx=0.0):
        """内側の闇。隙間から向こう側が抜けて見えないように塞ぐ"""
        out = []
        for i in range(n):
            phi = 360.0 * i / n
            r = ellipse_r(a, b, phi)
            w = 2 * math.pi * r / n * 1.45
            out.append(self.cube((cx - w / 2, cy + r - thick, z), (w, thick, depth), region,
                                 rot=(0, 0, phi), pivot=(cx, cy, z + depth / 2), key=f"{key}{i}"))
        return out

    def ring_y(self, y, r, n, w, h, thick, region, key, tilt=0.0, tone=0.5, phase=0.0,
               depth_out=False):
        """Y軸まわりに板を並べた<b>水平</b>の環。ドームの帯・花冠の花弁・放射状の器官に使う。

        <p>板を +X 側へ1枚だけ置き、<b>先に Z で外へ倒してから</b> Y で円周へ振る。
        こうするとどの板も「外へ倒れた」同じ関係を保ったまま並ぶ。tilt が正で外へ倒れる。
        GeckoLib は頂点へ X→Y→Z の順に掛けるので、`rot=(0, th, tilt)` と書くと逆
        （円周へ振ってから全体を倒す）になる。{@link euler_seq} で同じ姿勢の角を解く。</p>

        <p>{@link Kit.shell} が縦の断面（前後に連なる胴）なのに対し、こちらは横の断面。
        傘や花のように<b>上下に積んで丸くする</b>ものはこちらでないと組めない。</p>
        """
        out = []
        for i in range(n):
            th = phase + 360.0 * i / n
            size = (thick, h, w) if not depth_out else (w, h, thick)
            origin = (r - thick, y - h / 2, -w / 2) if not depth_out else (-w / 2, y - h / 2, r - thick)
            out.append(self.cube(origin, size, region,
                                 rot=euler_seq(('z', tilt), ('y', th)),
                                 pivot=(0, y, 0), tone=tone, key=f"{key}{i}"))
        return out

    def dome_ring(self, cy, a, b, theta_deg, n, w, h, thick, region, key,
                  tone=0.5, phase=0.0):
        """ドームの帯。楕円の輪郭に沿って板を並べ、板を<b>その場で寝かせる</b>。

        <p>{@link Kit.ring_y} の `tilt` は板を環の軸まわりに回すので、角度が大きいと
        板がその場で寝るのではなく<b>円周に沿って振り落とされる</b>。傘や花冠のように
        大きく寝かせる帯をあれで組むと、帯どうしが離れて体が割れる。</p>

        <p>ここでは「半径 R の位置に置いてから φ だけ回す」と「極座標 (R, φ) へ置く」が
        同じことである、という関係をそのまま使う。楕円上の点 (a cosθ, b sinθ) を
        極座標へ直し、その R と φ を渡せば、板は狙った位置に狙った角度で並ぶ。</p>

        cy はドームの中心（帯が並ぶ球の中心）の高さ、a/b は水平・垂直の半径、
        theta_deg は 0 で赤道（cy の高さ）、90 で頂点。
        """
        t = math.radians(theta_deg)
        px, py = a * math.cos(t), b * math.sin(t)
        radius = math.hypot(px, py)
        phi = math.degrees(math.atan2(py, px))
        out = []
        for i in range(n):
            th = phase + 360.0 * i / n
            out.append(self.cube((radius - thick, cy - h / 2, -w / 2), (thick, h, w), region,
                                 rot=euler_seq(('z', -phi), ('y', th)), pivot=(0, cy, 0),
                                 tone=tone, key=f"{key}{i}"))
        return out, (radius, phi, py + cy)

    def radial(self, z, cy, a, b, phi, length, w, region, key, lean=0.0, inward=True,
               tone=None, cx=0.0, depth=None):
        """環の縁から軸へ向かって（または外へ）生えるもの。牙・棘・触角に使う。

        <p>いったん真上に置いてから円周上の位置 phi（真上が0度）へ回す。こうすると
        どの角度の牙も「環の縁から生えて軸を向く」同じ関係を保てる。lean は前後の傾き。</p>
        """
        r = ellipse_r(a, b, phi)
        d = depth if depth is not None else w
        y0 = cy + r - length if inward else cy + r
        return self.cube((cx - w / 2, y0, z - d / 2), (w, length, d), region,
                         rot=(lean, 0, phi), pivot=(cx, cy, z), tone=tone, key=key)

    # ==================== 飾り ====================

    def fern_cluster(self, x, y, z, w, h, key, region, count=5, tilt=24):
        """シダの株。板を Y 軸まわりに扇状へ回し、外側ほど寝かせる"""
        out = []
        for i in range(count):
            t = i / (count - 1) - 0.5
            ry = t * 170 + ((i * 53) % 19) - 9
            out.append(self.cube((x - w / 2, y, z), (w, h * (1.0 - abs(t) * 0.35), 0), region,
                                 rot=(t * tilt, ry, -t * tilt * 0.6), pivot=(x, y, z),
                                 key=f"{key}{i}"))
        return out

    def hanging_moss(self, x, y, z, w, h, key, region):
        """垂れ下がる苔の房。交差2枚"""
        return [
            self.cube((x - w / 2, y - h, z), (w, h, 0), region, rot=(0, 34, 0),
                      pivot=(x, y, z), key=key + "a"),
            self.cube((x - w / 2, y - h, z), (w, h, 0), region, rot=(0, -56, 0),
                      pivot=(x, y, z), key=key + "b"),
        ]

    def membrane(self, x, y0, y1, z0, z1, key, region, rx=0.0, ry=0.0, rz=0.0, pivot=None):
        """破れた膜。厚み0の板。寝かせるのは Bone の静止姿勢で行うのが基本"""
        return self.cube((x, y0, z0), (0, y1 - y0, z1 - z0), region,
                         rot=(rx, ry, rz), pivot=pivot or (x, y0, z0), key=key)

    def glow_tip(self, x, y, z, r, key, region):
        """光る先端。_glowmask に抜かれて周囲の明るさに関わらず光る"""
        return self.cube((x - r, y - r, z - r), (2 * r, 2 * r, 2 * r), region, key=key)

    # ==================== 点と方向で置く箱 ====================

    def slab(self, center, size, u, v, region, tone=None, key=None):
        """中心と向きで置く箱。u / v は箱の局所 X / Y 軸を向けたい方向（geo 空間）。

        <p>局所 Z は u × v で決まる。向きは直交していなくてもよい（v を u に対して
        直交させ直す）。箱は自分の中心まわりに回すので、回転で位置がずれない。</p>

        <p>面とテクスチャの関係は回していない箱と同じ。上面（局所 +Y）は幅が局所 X、
        縦が局所 Z で、絵の上端は<b>局所 +Z 側</b>へ来る（GeckoLib の `quadUp`。以前ここは
        -Z と書いていて、描画器も同じ間違いをしていたので図では気付けなかった）。
        側面（局所 ±X / ±Z）は絵の上端が局所 +Y 側。だから「苔を上から垂らしたい面」は
        局所 Z を<b>上り方向</b>へ向け、「先端を破りたい膜」は局所 Y を先端へ向ける。</p>
        """
        u = _unit(u)
        v = _unit(_sub(v, _mul(u, _dot(u, v))))
        w = _cross(u, v)
        origin = tuple(center[i] - size[i] / 2.0 for i in range(3))
        return self.cube(origin, size, region, rot=euler_from_axes(u, v, w), pivot=center,
                         tone=tone, key=key)

    def beam(self, p0, p1, w, t, region, side=(1, 0, 0), tone=None, key=None, overshoot=0.0):
        """p0 から p1 へ渡す棒。局所 Y が棒の向きなので、絵の上端は p1 側に来る。

        w は side 方向の太さ、t はそれと直交する向きの太さ。骨条・牙・棘・蔓に使う。
        overshoot は両端を延ばす長さ。継ぎ目を重ねて隙間を消すときに使う。
        """
        d = _sub(p1, p0)
        length = math.sqrt(_dot(d, d))
        d = _unit(d)
        if abs(_dot(d, _unit(side))) > 0.98:      # 棒と side がほぼ平行なら別の軸を借りる
            side = (0, 0, 1) if abs(d[2]) < 0.9 else (0, 1, 0)
        u = _unit(_sub(side, _mul(d, _dot(d, side))))
        mid = tuple((p0[i] + p1[i]) / 2.0 for i in range(3))
        return self.slab(mid, (w, length + 2 * overshoot, t), u, d, region, tone=tone, key=key)

    def loft(self, s0, s1, key, t0=-180.0, t1=180.0, n=12, thick=4.0, skin=None,
             region=None, tone=0.5, lap=1.12, overlap=1.06, sink=0.0, end=None):
        """2つの断面のあいだへ板を張り、段差の無い外皮にする。

        <p>{@link shell} は1つの断面に板を並べるだけなので、隣の断面と太さが違うと
        継ぎ目が階段になる。ここでは<b>前後2つの断面の縁どうしを板で結ぶ</b>。板は
        太さの変化に合わせて傾くので、頭骨の丸みも胴の絞りも面として繋がる。</p>

        s0 / s1 は (z, 中心の高さ, 半幅a, 半高b)。t0..t1 は張る範囲で、真上が 0 度、
        +X 側が正、真下が ±180 度（楕円の媒介変数。等間隔に取ると板幅がほぼ揃う）。
        n は板の枚数。lap は周方向に隣どうしを重ねる割合で、板と板のあいだの楔形の
        隙間を塞ぐ。overlap は前後の環どうしを重ねる割合。
        end を (t0, t1) で渡すと、s1 側だけ張る範囲を変えられる。縁の線が断面ごとに
        上がり下がりする殻（口角から鼻先へ持ち上がる上唇など）を、段を付けずに張れる。
        sink は板を内側へ沈める深さ。前後の環は太さがほとんど変わらないと<b>同じ平面で
        重なり</b>、ゲーム内で面を取り合ってちらつく。環を1つおきに少し沈めれば、
        重なりは瓦のような段になって、ちらつかない。

        <p>skin(t, ny) を渡すと板ごとに素材を選べる。t は板の中心角、ny は外向き法線の
        上向き成分（1 で真上、-1 で真下）。(region, tone) を返す。None を返すとその板を
        <b>張らない</b>（骨に空いた孔になる）。</p>

        <p>板の局所 Z は「面に沿って上る向き」に揃えてある（上面の絵は上端が局所 +Z）。
        骨の素材は上辺が苔なので、
        こうしておくと苔が背から脇腹へ垂れる向きに出る。</p>
        """
        out = []
        step = (t1 - t0) / n
        e0, e1 = end if end else (t0, t1)
        estep = (e1 - e0) / n

        def point(sec, t):
            z, cy, a, b = sec[:4]
            r = math.radians(t)
            return (a * math.sin(r), cy + b * math.cos(r), z)

        axis = (0.0, (s0[1] + s1[1]) / 2.0, (s0[0] + s1[0]) / 2.0)
        for i in range(n):
            ta, tb = t0 + step * i, t0 + step * (i + 1)
            ea, eb = e0 + estep * i, e0 + estep * (i + 1)
            tm = (ta + tb + ea + eb) / 4.0
            a0, b0, a1, b1 = point(s0, ta), point(s0, tb), point(s1, ea), point(s1, eb)
            m0, m1 = _mid(a0, b0), _mid(a1, b1)
            along = _sub(m1, m0)                       # 子午線。前の断面から後ろの断面へ
            length = math.sqrt(_dot(along, along))
            along = _unit(along)
            across = _add(_sub(b0, a0), _sub(b1, a1))  # 周方向。t が増える向き
            across = _unit(_sub(across, _mul(along, _dot(along, across))))
            normal = _cross(across, along)
            centre = _mid(m0, m1)
            if _dot(normal, _sub(centre, axis)) < 0:   # 外向きに揃える
                normal = _mul(normal, -1)
            # 面に沿って下る向き。真上・真下の板は水平なので、周方向をそのまま使う
            down = across if across[1] <= 0 else _mul(across, -1)
            picked = skin(tm, normal[1]) if skin else (region, tone)
            if picked is None:
                continue
            reg, tn = picked
            width = max(_dist(a0, b0), _dist(a1, b1)) * lap
            centre = _sub(centre, _mul(normal, thick / 2.0 + sink))
            u = _cross(down, normal)                    # 局所 X。u × normal = -down（上り）
            out.append(self.slab(centre, (length * overlap, thick, width), u, normal, reg,
                                 tone=tn, key=f"{key}{i}"))
        return out

    def fan(self, base, e1, e2, rays, key, ray_region, web_region, tip_region=None,
            r0=0.0, r1=1.0, ray_w=2.4, ray_tone=0.4, web=True, lift=0.12):
        """一点から骨条が扇に開く鰭。骨条のあいだへ膜を張る。

        e1 / e2 は鰭の面を張る2方向（直交する単位ベクトル）。骨条の向きは
        cos(角)・e1 + sin(角)・e2 で、rays は (角度, 長さ) の並び。角度は度。

        <p>r0..r1 は骨条のうち作る範囲（0 が根元、1 が先端）。鰭を2本の Bone へ分けて
        先だけ遅らせて煽りたいとき、根元側と先端側で2回呼ぶ。先端まで作る呼び出しでは
        膜に tip_region（外縁が破れた素材）を使う。</p>

        <p>膜は厚み 0 の板で、隣の膜と重なる。同じ平面に置くと面を取り合ってちらつくので、
        1枚ごとに lift ずつ面の法線方向へずらしてある。</p>
        """
        normal = _cross(e1, e2)
        out = []

        def direction(angle):
            r = math.radians(angle)
            return _add(_mul(e1, math.cos(r)), _mul(e2, math.sin(r)))

        # ray_w を 0 にすると骨条を作らず、膜だけを張る（二叉の尾鰭の切れ込みなど）
        for i, (angle, length) in enumerate(rays if ray_w else ()):
            d = direction(angle)
            out.append(self.beam(_add(base, _mul(d, length * r0)), _add(base, _mul(d, length * r1)),
                                 ray_w, ray_w, ray_region, side=normal, tone=ray_tone,
                                 key=f"{key}ray{i}", overshoot=0.4))
        if not web:
            return out
        for i in range(len(rays) - 1):
            (ang_a, len_a), (ang_b, len_b) = rays[i], rays[i + 1]
            mid = direction((ang_a + ang_b) / 2.0)
            half = math.radians(abs(ang_b - ang_a) / 2.0)
            # 膜は短いほうの骨条までしか届かない。長いほうの先は骨だけが突き出す
            reach = min(len_a, len_b) * math.cos(half)
            lo, hi = reach * r0, reach * r1
            width = 2.0 * hi * math.tan(half) + ray_w
            off = lift * (1 if i % 2 else -1) * (1 + i // 2)
            centre = _add(_add(base, _mul(mid, (lo + hi) / 2.0)), _mul(normal, off))
            reg = tip_region if (tip_region is not None and r1 >= 0.999) else web_region
            # 局所 X が面の法線（厚み 0）、局所 Y が先端へ向かう向き
            out.append(self.slab(centre, (0, hi - lo, width), normal, mid, reg, key=f"{key}web{i}"))
        return out

    def model(self, identifier, bones, w, h, off):
        return {
            "format_version": "1.12.0",
            "minecraft:geometry": [{
                "description": {
                    "identifier": identifier,
                    "texture_width": self.atlas.SIZE,
                    "texture_height": self.atlas.SIZE,
                    "visible_bounds_width": w,
                    "visible_bounds_height": h,
                    "visible_bounds_offset": [0, off, 0],
                },
                "bones": bones,
            }],
        }


def _mirrored(face, axis):
    """面の UV を、横（axis=0）か縦（axis=1）に裏返したもの。uv_size が負になる"""
    uv, size = list(face["uv"]), list(face["uv_size"])
    uv[axis] = round(uv[axis] + size[axis], 2)
    size[axis] = -size[axis]
    return {"uv": uv, "uv_size": size}


# ============================================================ 形の計算
def _add(a, b):
    return (a[0] + b[0], a[1] + b[1], a[2] + b[2])


def _sub(a, b):
    return (a[0] - b[0], a[1] - b[1], a[2] - b[2])


def _mul(a, k):
    return (a[0] * k, a[1] * k, a[2] * k)


def _dot(a, b):
    return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]


def _cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def _unit(a):
    n = math.sqrt(_dot(a, a))
    return (a[0] / n, a[1] / n, a[2] / n)


def _mid(a, b):
    return ((a[0] + b[0]) / 2.0, (a[1] + b[1]) / 2.0, (a[2] + b[2]) / 2.0)


def _dist(a, b):
    return math.sqrt(_dot(_sub(a, b), _sub(a, b)))


def euler_from_axes(u, v, w):
    """箱の局所 X / Y / Z 軸を u / v / w（geo 空間の正規直交系）へ向ける rotation を解く。

    <p>GeckoLib は geo の (rx, ry, rz) を、X を反転した空間で (-rx, -ry, +rz) として持ち、
    頂点へ Rz・Ry・Rx を掛ける。向けたい姿勢を同じ空間の行列 M に直すと</p>

    <pre>
        M = | cγcβ   cγsβsα - sγcα   cγsβcα + sγsα |
            | sγcβ   sγsβsα + cγcα   sγsβcα - cγsα |      α = -rx, β = -ry, γ = rz
            | -sβ    cβsα            cβcα          |
    </pre>

    <p>なので 1列目と3行目だけ見れば3つの角が取り出せる。X の反転は、行列の
    「x 行」と「x 列」の符号を1回ずつ返すことに当たる（M = F・R・F）。</p>

    <p>β が ±90 度に張り付くと α と γ が区別できなくなる（ジンバルロック）。局所 X を
    前後へ真っ直ぐ向けたときだけ起きる。そのときは γ を 0 に固定して α だけで解く。</p>
    """
    m00, m10, m20 = u[0], -u[1], -u[2]
    m01, m11 = -v[0], v[1]
    m21, m22 = v[2], w[2]
    if abs(m20) > 0.99999:
        beta = -math.copysign(math.pi / 2.0, m20)
        gamma = 0.0
        # γ = 0 のとき M01 = sβsα, M11 = cα（上の式から）
        alpha = math.atan2(m01 / math.sin(beta), m11)
    else:
        beta = -math.asin(m20)
        alpha = math.atan2(m21, m22)
        gamma = math.atan2(m10, m00)
    return (-math.degrees(alpha), -math.degrees(beta), math.degrees(gamma))


def euler_seq(*steps):
    """1軸ずつの回転を<b>頂点へ効かせたい順</b>に並べ、同じ姿勢になる rotation を解く。

    steps は `('x' | 'y' | 'z', 度)` の並びで、先に書いたものが先に頂点へ効く。
    角度の向きは geo の rotation と同じ約束（`rx > 0` で前が下がる、など）。

    <p>geo の rotation は必ず X→Y→Z の順に効くので、「Z で倒してから Y で回す」は
    `(0, ry, rz)` とは書けない。ここで合成した姿勢を X→Y→Z の3つの角へ解き直す。
    pivot は変えなくてよい（同じ点まわりの回転を合成しているだけなので）。</p>

    ```python
    euler_seq(('z', 30), ('y', 45))   # 外へ 30 度倒した板を、円周の 45 度の位置へ
    ```
    """
    # X を反転した空間（GeckoLib が回す空間）で行列を作る。角は (-rx, -ry, +rz)
    m = [[1.0, 0.0, 0.0], [0.0, 1.0, 0.0], [0.0, 0.0, 1.0]]
    for axis, deg in steps:
        a = math.radians(deg if axis == 'z' else -deg)
        c, s = math.cos(a), math.sin(a)
        r = {'x': [[1, 0, 0], [0, c, -s], [0, s, c]],
             'y': [[c, 0, s], [0, 1, 0], [-s, 0, c]],
             'z': [[c, -s, 0], [s, c, 0], [0, 0, 1]]}[axis]
        m = [[sum(r[i][k] * m[k][j] for k in range(3)) for j in range(3)] for i in range(3)]
    # M = Rz(γ)・Ry(β)・Rx(α) として読む（式は euler_from_axes の表と同じ）
    if abs(m[2][0]) > 1.0 - 1e-9:
        beta = -math.copysign(math.pi / 2.0, m[2][0])
        gamma = 0.0
        alpha = math.atan2(-m[1][2], m[1][1])       # γ = 0 なら 2行目は (0, cα, -sα)
    else:
        beta = -math.asin(m[2][0])
        alpha = math.atan2(m[2][1], m[2][2])
        gamma = math.atan2(m[1][0], m[0][0])
    return (-math.degrees(alpha), -math.degrees(beta), math.degrees(gamma))


def ellipse_r(a, b, phi_deg):
    """中心から角度 phi（真上が0）方向に見た楕円の半径"""
    p = math.radians(phi_deg)
    return a * b / math.hypot(b * math.sin(p), a * math.cos(p))


def seg(length, w, t, rx=0.0, rz=0.0, ry=0.0, region=None, tone=0.5, extra=None, cap=None):
    """連なりの1節。rx/rz/ry は<b>根元から測った絶対角</b>で書く。

    絶対角のほうが形を思い描きやすい（「3節目は45度垂れる」）。Bone に載せるときは
    前の節との差へ直すので、Pivot は関節に残る。

    extra はこの節と一緒に動く飾り。座標は<b>組み立て時（回転前）</b>のモデル座標で書く。
    cap は節の先端に置くものを作る関数で、先端の座標を受け取る。連なりの位置を数えなくて
    済むので、先端の飾りが原点へ取り残される事故が起きない。
    """
    return {"length": float(length), "w": float(w), "t": float(t),
            "rx": float(rx), "rz": float(rz), "ry": float(ry),
            "region": region, "tone": tone, "extra": extra or [], "cap": cap}


# ============================================================ アニメーション
# 速度差で重さを出す。予備動作はゆっくり、打撃だけ高速、当たった瞬間に静止、
# そこから慣性で流れて、ゆっくり静止姿勢へ戻る。

EASE_SLOW = "easeinsine"        # 予備動作。だんだん速く
EASE_FAST = "easeinquint"       # 打撃。爆発的に
EASE_HIT = "easeoutback"        # インパクト。行き過ぎて戻る
EASE_BACK = "easeoutcubic"      # 慣性で流れて止まる
EASE_SOFT = "easeinoutsine"     # 呼吸のような往復


def fit(frames, length, gap=0.02):
    """遅らせたキーを、昇順のまま尺の内側へ詰める。

    節ごとに ``t + lag * i`` とずらすと、束が長いほど末端のキーが後ろへ流れて、
    ``animation_length`` を追い越し、固定してある最後のキーより後ろへ回り込む。
    そうなると GeckoLib は「Invalid keyframe data - multiple starting keyframes」で
    **その animation を丸ごと読まなくなる**。ずれた1本が動かないのではなく、
    全部が再生されない。絵を出しても気付けないたぐいの壊れ方。

    末尾から前へ詰める。後ろのキーを尺に収め、その手前は ``gap`` だけ空けた位置を
    上限にする ―― 遅れて来たキーは動かせる範囲で遅らせたまま、順序だけが直る。
    キーを後ろへずらすことはしないので、振り付けの形は変わらない。

    ``tools/growth_models/common.py`` の ``lag_curl`` / ``burst_reactions`` が
    同じことを内側でやっている。あちらは上限を先に決め打ちしているが、こちらは
    交差したキー（前後で逆向きに遅らせた場合）も直せる。
    """
    out = list(frames)
    limit = length
    for j in range(len(out) - 1, -1, -1):
        t = min(out[j][0], limit)
        out[j] = (t,) + tuple(out[j][1:])
        limit = t - gap
    return out


def _kf(frames):
    out = {}
    for f in frames:
        t, val = f[0], f[1]
        ease = f[2] if len(f) > 2 else None
        vec = [round(v, 2) for v in val]
        out[f"{t:.2f}"] = vec if ease is None else {"vector": vec, "easing": ease}
    return out


def rot(frames):
    return {"rotation": _kf(frames)}


def pos(frames):
    return {"position": _kf(frames)}


def scale(frames):
    return {"scale": _kf(frames)}


def merge(*ds):
    out = {}
    for d in ds:
        out.update(d)
    return out


def wave(length, amp, phase=0.0, axis=(0, 1, 0), steps=6):
    """正弦の揺れ。phase をずらすと節ごとに遅れて波が伝わる"""
    frames = []
    for i in range(steps + 1):
        v = math.sin(2 * math.pi * (i / steps) + phase)
        frames.append((round(length * i / steps, 2), tuple(a * amp * v for a in axis), EASE_SOFT))
    return rot(frames)


def hold(length, value):
    """姿勢を保つだけ。壊れた部位の垂れ下がりに使う"""
    return rot([(0.0, value), (length, value)])


def strike(length, rest, wind, hit, follow, impact, axis=(1, 0, 0), windup=0.45, back=0.75):
    """予備動作 → 溜め切り → 高速の打撃 → インパクト → 慣性 → 復帰。

    振り付けの骨組みだけを渡せば、速度差はこちらが付ける。数字は角度で、
    rest から wind まではゆっくり、そこで一瞬止まり、hit まで一気に振り抜く。
    """
    a = lambda v: tuple(k * v for k in axis)
    return rot([
        (0.0, a(rest)),
        (length * windup, a(wind), EASE_SLOW),
        (length * (windup + 0.06), a(wind)),
        (impact, a(hit), EASE_FAST),
        (impact + length * 0.14, a(follow), EASE_HIT),
        (length * back, a(follow * 0.35), EASE_BACK),
        (length, a(rest), EASE_BACK),
    ])


# ============================================================ 書き出しの点検
def verify(name, geo, anims):
    """作った geo とアニメーションが食い違っていないか確かめる。

    ボーン名の打ち間違いは黙って「動かないボーン」になるだけなので、
    絵を出しても気付けない。ここで落とす。

    キーの時刻も同じ。逆順に並んだキーは animation を丸ごと落とすし、尺を超えたキーは
    そのぶんが再生されない。どちらも GeckoLib は例外を投げないので、ここで落とす
    （直し方は :func:`fit`）。書き出し済みのファイル全部へ同じ検査を掛けるものが
    ``tools/boss/check_animations.py`` にある。
    """
    bones = geo["minecraft:geometry"][0]["bones"]
    names = []
    seen = set()
    for b in bones:
        assert b["name"] not in seen, f"{name}: ボーン名 {b['name']} が重複している"
        seen.add(b["name"])
        names.append(b["name"])
    for b in bones:
        if b.get("parent"):
            assert b["parent"] in seen, f"{name}: 親ボーン {b['parent']} が無い"
    for anim_name, anim in anims.items():
        length = anim.get("animation_length")
        for boneName, tracks in anim["bones"].items():
            assert boneName in seen, f"{name}/{anim_name}: 未知のボーン {boneName}"
            for kind, keys in tracks.items():
                times = [float(t) for t in keys]
                assert times == sorted(times), \
                    f"{name}/{anim_name}/{boneName}/{kind}: 時刻が逆順（{times}）"
                assert length is None or not times or times[-1] <= length + 1e-6, \
                    f"{name}/{anim_name}/{boneName}/{kind}: 尺（{length}秒）を超えた {times[-1]}"
    cubes = sum(len(b["cubes"]) for b in bones)
    rotated = sum(1 for b in bones for c in b["cubes"] if "rotation" in c)
    rotbones = sum(1 for b in bones if "rotation" in b)
    return f"{name}: bones={len(seen)}(回転{rotbones}) cubes={cubes}(回転{rotated})"
