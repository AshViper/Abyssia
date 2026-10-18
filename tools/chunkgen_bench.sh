#!/bin/bash
# usage: bench.sh PORT OUTFILE
R=/f/Java/Abyssia/.claude/worktrees/zen-gagarin-d3ecfb/tools/scratch_rcon.py
P=$1; O=$2; : > $O
c(){ python $R --port $P --password abyss "$@"; }
for L in "4000 4000" "-6000 3000" "9000 -7000"; do
  set -- $L
  c cmd "abyssia caves stats reset" >> $O 2>&1
  echo "== cold $L" >> $O; c gentime $1 $2 6 >> $O 2>&1; c cmd "abyssia caves stats" >> $O 2>&1
  c cmd "abyssia caves stats reset" >> $O 2>&1
  echo "== warm $L" >> $O; c gentime $(( $1 + 96 )) $2 6 >> $O 2>&1; c cmd "abyssia caves stats" >> $O 2>&1
done
echo DONE >> $O
