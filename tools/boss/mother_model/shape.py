# -*- coding: utf-8 -*-
"""深淵の母（abyss_mother）の形の表。build.py（箱）と atlas.py（傘の画素の切り抜き）が同じ表を読む。

単位はブロック。y は傘の中心（AbyssMother.headPos()）から上が正、r は傘の軸からの距離。
表は AbyssMotherRenderer（旧メッシュ描画）の BELL / UNDER と同じ値で、旧描画が loft の +Z を下へ
向けていたのを y = -z に直してある。
"""
import math

MODEL_SCALE = 4.0           # 描画側で一様に掛ける倍率
U = 16.0 / MODEL_SCALE      # 1 ブロックが何単位か（4）

# 傘の外面 (y, r)。頂点（r = 0）から縁へ。縁は内へ少し巻き込む（r が 18.2 → 16.9）
BELL = ((8.0, 0.0), (7.6, 5.0), (6.4, 10.0), (4.0, 14.2), (0.5, 17.0), (-3.0, 18.2), (-5.2, 18.0), (-6.4, 16.9))
# 下傘（傘の内側の面）(y, r)。旧描画の最後の2区間は1区間にまとめ、縁は外面の縁へ合わせた
UNDER = ((3.6, 0.0), (3.0, 6.0), (1.0, 11.0), (-2.0, 14.5), (-6.4, 16.85))

COLUMNS = 24                # 周方向の分割（傘・下傘・フリル）
PETALS = 12                 # 開花の区画。1 区画 = 2 列。bell_petal_k の中心は tent_k の付け根の真上
SKIRT_OUTER = 4             # 外面の区間 4 から下（肩 (0.5, 17) より下）が bell_skirt_k
SKIRT_UNDER = 3             # 下傘の区間 3（縁の区間）が bell_skirt_k
PAD_PX = 0.6                # 切り抜きを外へ広げる画素数。隣の板と継ぎ目を少し重ね、髪の毛ほどの隙間を消す


def chord(r):
    """半径 r の円を COLUMNS 等分した弦の長さ"""
    return 2.0 * r * math.sin(math.pi / COLUMNS)


def rows(profile):
    """区間ごとの (上辺の幅の割合, 下辺の幅の割合, v の始まり, v の長さ)。

    板は台形（上辺 = 頂点側の弦、下辺 = 縁側の弦）を囲む長方形で、幅は広いほうの弦。
    割合はその幅に対する弦の長さ。v は頂点から縁までを 0..1 に取った位置（板の高さで測る）。
    """
    c = math.cos(math.pi / COLUMNS)          # 弦の中点は円の内側（r cos(π/N)）にある
    heights = [math.hypot(b[0] - a[0], (b[1] - a[1]) * c) for a, b in zip(profile, profile[1:])]
    total = sum(heights)
    out, acc = [], 0.0
    for (a, b), h in zip(zip(profile, profile[1:]), heights):
        w = max(chord(a[1]), chord(b[1]))
        out.append((chord(a[1]) / w, chord(b[1]) / w, acc / total, h / total))
        acc += h
    return out


BELL_ROWS = rows(BELL)
UNDER_ROWS = rows(UNDER)
