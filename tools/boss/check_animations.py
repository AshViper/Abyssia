# -*- coding: utf-8 -*-
"""書き出し済みの animation を、対になる geo と突き合わせて検査する。

```bash
python tools/boss/check_animations.py                 # 全部見る（壊れていれば終了コード1）
python tools/boss/check_animations.py -v              # 無事なファイルも1行ずつ出す
python tools/boss/check_animations.py abyss_serpent   # 一部だけ
```

ParaInfaction の `tools/check_animations.py` を Abyssia 用に複写したもの（読む場所だけ
`assets/abyssia` に直してある）。

## なぜ要るのか

GeckoLib はここで見る3つのどれも**例外を投げない**。起動して、動かない／再生されない
のを目で見つけるまで判らない。生成器を書き換えるたびに全ファイルへ掛けられるよう、
書き出し済みのファイル全部へ同じ検査を掛ける。

見るのは3つ:

- **ボーンが geo に在るか** … 知らないボーンは黙って読み飛ばされる。そのトラックは
  何も起きない。geo を作り直してボーン名が変わったときに置き去りになる
- **時刻が昇順か** … 逆順のキーがあると `Invalid keyframe data - multiple starting
  keyframes` でその animation が**丸ごと**落ちる。害が一番大きい
- **時刻が尺の内側か** … 遅延（lag）を足したキーが `animation_length` を超えると、
  そのぶん再生されない。上の逆順とほぼ必ず同時に起きる

## 直し方

逆順・尺超えはたいてい生成器で `t + lag * i` のようにキーをずらしているところ。
`tools/boss/boss_kit.py` の `fit` ―― 最後のキーの時刻を上限に詰める ―― を通す。

ボーン名の食い違いは geo 側が正しいので、animation を実名へ振り直す。
"""
import argparse
import json
import pathlib
import sys

# Windows のコンソールは既定が cp932 で、日本語のまま出すと化ける
if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')

ROOT = pathlib.Path(__file__).resolve().parents[2]      # tools/boss/ の2つ上がリポジトリ
ASSETS = ROOT / 'src/main/resources/assets/abyssia'

# animation と geo の対。同じ名前のファイルが両方にある前提で並べる
PAIRS = (('animations/entity', 'geo/entity'),)

EPS = 1e-6


def load(path):
    return json.loads(path.read_text(encoding='utf-8'))


def geo_bones(path):
    geo = load(path)
    return {b['name'] for g in geo['minecraft:geometry'] for b in g.get('bones', [])}


def check_file(anim_path, geo_path):
    """壊れているところを 1件 1行の文字列で返す。無事なら空"""
    bones = geo_bones(geo_path)
    doc = load(anim_path)
    name = anim_path.name.split('.')[0]
    bad = []
    for an, a in sorted(doc.get('animations', {}).items()):
        length = a.get('animation_length')
        for bone, tracks in a.get('bones', {}).items():
            if bone not in bones:
                bad.append(f'{name}/{an}: geo に無いボーン {bone}')
                continue
            if not isinstance(tracks, dict):
                continue
            for kind, keys in tracks.items():
                # キーが1つだけのときは辞書ではなく値そのものが入る（時刻を持たない）
                if not isinstance(keys, dict):
                    continue
                times = []
                for t in keys:
                    try:
                        times.append(float(t))
                    except ValueError:
                        bad.append(f'{name}/{an}/{bone}/{kind}: 時刻として読めない {t!r}')
                        times = None
                        break
                if times is None:
                    continue
                order = sorted(times)
                if times != order:
                    culprit = next(f'{times[i-1]:g}→{times[i]:g}'
                                   for i in range(1, len(times)) if times[i] < times[i - 1])
                    bad.append(f'{name}/{an}/{bone}/{kind}: 時刻が逆順（{culprit}）'
                               f' ― この animation は丸ごと読まれない')
                # 末尾ではなく最大を見る。逆順のときは尺を超えた鍵が末尾に来ない
                if length is not None and times and max(times) > length + EPS:
                    bad.append(f'{name}/{an}/{bone}/{kind}: 尺（{length:g}秒）を'
                               f'{max(times) - length:.2f}秒超えた')
    return bad


def targets(only):
    """(animation, geo) の対を、対応する geo があるものだけ並べる"""
    out = []
    for adir, gdir in PAIRS:
        for anim in sorted((ASSETS / adir).glob('*.animation.json')):
            name = anim.name[:-len('.animation.json')]
            if only and name not in only:
                continue
            geo = ASSETS / gdir / f'{name}.geo.json'
            if geo.exists():
                out.append((name, anim, geo))
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('-v', '--verbose', action='store_true', help='無事なファイルも出す')
    ap.add_argument('targets', nargs='*', help='省略すると全部')
    args = ap.parse_args()

    pairs = targets(set(args.targets))
    if args.targets:
        missing = set(args.targets) - {n for n, _, _ in pairs}
        for n in sorted(missing):
            print(f'{n}: 対になる geo が無い')

    broken = 0
    for name, anim, geo in pairs:
        bad = check_file(anim, geo)
        if bad:
            broken += 1
            for line in bad:
                print(line)
        elif args.verbose:
            print(f'{name}: 無事')

    print(f'\n{len(pairs)} ファイル中 {broken} ファイルに問題あり'
          if broken else f'\n{len(pairs)} ファイル、いずれも無事')
    return 1 if broken else 0


if __name__ == '__main__':
    sys.exit(main())
