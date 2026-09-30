"""QLoRA fine-tune of a small instruct model on data/<ver>/sft (chat messages).

Run inside WSL2 (see train/run.sh):
    ./run.sh train_sft.py --data ../data/v1/sft --out ~/model-ai/runs/v1 [--tasks text2spec,edit] [--max-steps 50]

Writes <out>/adapter (LoRA), <out>/train_log.jsonl and <out>/run.json. Loss is computed on the
assistant reply only. Prints one JSON line with the result at the end.
"""

import argparse
import json
import os
import time

from common import load_sft, write_json


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", required=True, help="directory with train.jsonl / val.jsonl (sft format)")
    ap.add_argument("--out", required=True)
    ap.add_argument("--model", default="unsloth/Qwen2.5-Coder-3B-Instruct-bnb-4bit")
    ap.add_argument("--tasks", default="text2spec,edit")
    ap.add_argument("--max-seq", type=int, default=1024)
    ap.add_argument("--epochs", type=float, default=1.0)
    ap.add_argument("--max-steps", type=int, default=-1, help="for smoke tests")
    ap.add_argument("--batch", type=int, default=8)
    ap.add_argument("--accum", type=int, default=2)
    ap.add_argument("--lr", type=float, default=2e-4)
    ap.add_argument("--rank", type=int, default=32)
    ap.add_argument("--limit", type=int, default=0, help="use only N training rows")
    ap.add_argument("--seed", type=int, default=1)
    ap.add_argument("--packing", action="store_true", help="pack several short rows into one max-seq sequence")
    ap.add_argument("--no-eval", action="store_true", help="skip periodic evaluation (benchmarks)")
    args = ap.parse_args()

    import torch
    from unsloth import FastLanguageModel
    from unsloth.chat_templates import train_on_responses_only
    from datasets import Dataset
    from transformers import TrainerCallback
    from trl import SFTConfig, SFTTrainer

    tasks = set(args.tasks.split(","))
    train_rows = [r for r in load_sft(os.path.join(args.data, "train.jsonl")) if r["task"] in tasks]
    val_rows = [r for r in load_sft(os.path.join(args.data, "val.jsonl")) if r["task"] in tasks][:200]
    if args.limit:
        train_rows = train_rows[: args.limit]

    model, tok = FastLanguageModel.from_pretrained(args.model, max_seq_length=args.max_seq, load_in_4bit=True, dtype=None)
    model = FastLanguageModel.get_peft_model(
        model,
        r=args.rank,
        lora_alpha=args.rank,
        lora_dropout=0,
        target_modules=["q_proj", "k_proj", "v_proj", "o_proj", "gate_proj", "up_proj", "down_proj"],
        use_gradient_checkpointing="unsloth",
        random_state=args.seed,
    )

    def render(rows):
        texts = [tok.apply_chat_template(r["messages"], tokenize=False) for r in rows]
        lens = [len(tok(t).input_ids) for t in texts[:2000]]
        return Dataset.from_dict({"text": texts}), lens

    train_ds, lens = render(train_rows)
    val_ds, _ = render(val_rows)
    too_long = sum(1 for n in lens if n > args.max_seq)
    lens.sort()
    token_stats = {"p50": lens[len(lens) // 2], "p95": lens[int(len(lens) * 0.95)], "max": lens[-1], "over_max_seq": too_long}
    print(json.dumps({"rows": len(train_rows), "val_rows": len(val_rows), "tokens": token_stats}), flush=True)

    os.makedirs(args.out, exist_ok=True)
    # Architecture for the dashboard's model view (the HF cache uses symlinks Windows cannot read)
    write_json(os.path.join(args.out, "model_config.json"), {**model.config.to_dict(), "_lora_rank": args.rank, "_model": args.model})
    log_path = os.path.join(args.out, "train_log.jsonl")

    live_path = os.path.join(args.out, "live.json")

    class JsonLog(TrainerCallback):
        """train_log.jsonl (every logged step) + live.json (latest state, rewritten every step) for the dashboard."""

        def __init__(self):
            self.t_step = None
            self.durations = []
            self.last = {}

        def on_step_begin(self, _args, state, _control, **kw):
            self.t_step = time.time()

        def on_step_end(self, _args, state, _control, **kw):
            if self.t_step is not None:
                self.durations = (self.durations + [time.time() - self.t_step])[-20:]
            self.write_live(state)

        def on_log(self, _args, state, _control, logs=None, **kw):
            if logs:
                with open(log_path, "a", encoding="utf8") as f:
                    f.write(json.dumps({"step": state.global_step, "time": round(time.time(), 1), **logs}) + "\n")
                self.last.update({k: v for k, v in logs.items() if isinstance(v, (int, float))})
                self.write_live(state)

        def write_live(self, state):
            sec = sum(self.durations) / len(self.durations) if self.durations else None
            live = {
                "time": round(time.time(), 2),
                "step": state.global_step,
                "total": state.max_steps,
                "epoch": round(state.epoch or 0, 4),
                "sec_per_step": round(sec, 3) if sec else None,
                "eta_s": round(sec * (state.max_steps - state.global_step)) if sec else None,
                "rows_per_step": args.batch * args.accum,
                "loss": self.last.get("loss"),
                "grad_norm": self.last.get("grad_norm"),
                "learning_rate": self.last.get("learning_rate"),
                "eval_loss": self.last.get("eval_loss"),
                "vram_gb": round(torch.cuda.memory_reserved() / 2**30, 2),
            }
            tmp = live_path + ".tmp"
            with open(tmp, "w", encoding="utf8") as f:
                json.dump(live, f)
            os.replace(tmp, live_path)

    trainer = SFTTrainer(
        model=model,
        tokenizer=tok,
        train_dataset=train_ds,
        eval_dataset=val_ds,
        args=SFTConfig(
            dataset_text_field="text",
            max_seq_length=args.max_seq,
            packing=args.packing,
            per_device_train_batch_size=args.batch,
            per_device_eval_batch_size=args.batch,
            gradient_accumulation_steps=args.accum,
            num_train_epochs=args.epochs,
            max_steps=args.max_steps,
            learning_rate=args.lr,
            lr_scheduler_type="cosine",
            warmup_ratio=0.03,
            weight_decay=0.01,
            optim="adamw_8bit",
            logging_steps=1,  # every step: the dashboard follows training live
            eval_strategy="no" if args.no_eval else "steps",
            eval_steps=300 if args.max_steps < 0 else max(10, args.max_steps // 2),
            save_strategy="steps",
            save_steps=500,
            save_total_limit=2,
            output_dir=os.path.join(args.out, "checkpoints"),
            report_to="none",
            seed=args.seed,
            bf16=torch.cuda.is_bf16_supported(),
            fp16=not torch.cuda.is_bf16_supported(),
        ),
        callbacks=[JsonLog()],
    )
    trainer = train_on_responses_only(trainer, instruction_part="<|im_start|>user\n", response_part="<|im_start|>assistant\n")

    t0 = time.time()
    result = trainer.train()
    metrics = trainer.evaluate()
    model.save_pretrained(os.path.join(args.out, "adapter"))
    tok.save_pretrained(os.path.join(args.out, "adapter"))
    run = {
        "model": args.model,
        "args": vars(args),
        "tokens": token_stats,
        "train_loss": result.training_loss,
        "eval_loss": metrics.get("eval_loss"),
        "minutes": round((time.time() - t0) / 60, 1),
        "train_seconds": round(result.metrics.get("train_runtime", 0), 1),
        "rows_per_second": round(len(train_rows) * args.epochs / max(1e-6, result.metrics.get("train_runtime", 0)), 3) if args.max_steps < 0 else None,
        "peak_vram_gb": round(torch.cuda.max_memory_reserved() / 2**30, 2),
    }
    write_json(os.path.join(args.out, "run.json"), run)
    print(json.dumps(run), flush=True)


if __name__ == "__main__":
    main()
