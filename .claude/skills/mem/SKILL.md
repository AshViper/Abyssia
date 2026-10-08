---
name: mem
description: Look up project memory (Obsidian vault) as excerpts, never whole notes. Use before reading any vault note or when a past decision/problem may matter.
---

Vault access is excerpt-only. Never Read a vault note whole.

```bash
python tools/memory.py search <terms...> [-n 5]     # ranked notes: description + matching lines
python tools/memory.py heads <note>                 # headings of one note
python tools/memory.py show <note> <heading words> [--max 60]   # just that section
```

1. `search` with 1-3 specific terms (class name, feature id). Stop when a line answers the question.
2. Need more: `heads` then `show <note> <heading>`. Level 3 notes (decisions/problems/solutions) only when the task touches that past topic. `history/` and `_archive/` are not read.
3. The code and config win over the note; if they disagree, fix the note (haiku `memo` agent) only when it will matter later.
