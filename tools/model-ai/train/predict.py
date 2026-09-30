"""Batch generation for evaluation: sft/<split>.jsonl -> preds.jsonl ({id, output}).

    ./run.sh predict.py --adapter ~/model-ai/runs/v1/adapter --data ../data/v1/sft/test.jsonl \
        --out ../data/v1/pred_v1_test.jsonl [--limit 300] [--tasks text2spec]

Greedy decoding, no grammar (measures the raw model). Score on Windows with:
    node ai.js eval --pred data/v1/pred_v1_test.jsonl --ref data/v1/test.jsonl
(missing ids count as failures, so evaluate subsampled predictions against a filtered ref, see --ref-out).
"""

import argparse
import json
import time

from common import load_sft, sample_rows


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--adapter", required=True, help="LoRA adapter dir (or a base model id)")
    ap.add_argument("--data", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--tasks", default="text2spec,edit")
    ap.add_argument("--limit", type=int, default=0)
    ap.add_argument("--batch", type=int, default=16)
    ap.add_argument("--max-new", type=int, default=768)
    ap.add_argument("--max-seq", type=int, default=2048)
    args = ap.parse_args()

    from unsloth import FastLanguageModel

    tasks = set(args.tasks.split(","))
    rows = sample_rows([r for r in load_sft(args.data) if r.get("task", "text2spec") in tasks], args.limit)
    model, tok = FastLanguageModel.from_pretrained(args.adapter, max_seq_length=args.max_seq, load_in_4bit=True, dtype=None)
    FastLanguageModel.for_inference(model)
    tok.padding_side = "left"

    t0 = time.time()
    gen_tokens = 0
    with open(args.out, "w", encoding="utf8") as f:
        # Similar prompt lengths per batch -> less padding
        order = sorted(rows, key=lambda r: len(r["messages"][1]["content"]))
        for i in range(0, len(order), args.batch):
            batch = order[i : i + args.batch]
            prompts = [tok.apply_chat_template(r["messages"][:2], tokenize=False, add_generation_prompt=True) for r in batch]
            enc = tok(prompts, return_tensors="pt", padding=True).to("cuda")
            out = model.generate(**enc, max_new_tokens=args.max_new, do_sample=False, pad_token_id=tok.pad_token_id)
            new = out[:, enc["input_ids"].shape[1] :]
            for r, ids in zip(batch, new):
                text = tok.decode(ids, skip_special_tokens=True)
                gen_tokens += int((ids != tok.pad_token_id).sum())
                f.write(json.dumps({"id": r["id"], "output": text}, ensure_ascii=False) + "\n")
            print(json.dumps({"done": i + len(batch), "of": len(order), "sec": round(time.time() - t0)}), flush=True)
    print(json.dumps({"predictions": len(order), "minutes": round((time.time() - t0) / 60, 1), "gen_tokens": gen_tokens}), flush=True)


if __name__ == "__main__":
    main()
