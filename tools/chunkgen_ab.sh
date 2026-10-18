#!/bin/bash
# usage: chunkgen_ab.sh SCRATCH_DIR PORT LEVEL OUTDIR [nodp]   (RCON pw abyss; nodp = trial_chambers disabled via world datapack)
D=$1; P=$2; L=$3; O=$4; MODE=$5
R=/f/Java/Abyssia/.claude/worktrees/zen-gagarin-d3ecfb/tools/scratch_rcon.py
J="/c/Users/nagis/.gradle/jdks/eclipse_adoptium-21-amd64-windows/jdk-21.0.10+7/bin"
mkdir -p $O; rm -rf $D/run/$L
sed -i "s/^level-name=.*/level-name=$L/" $D/run/server.properties
if [ "$MODE" = nodp ]; then
  mkdir -p $D/run/$L/datapacks/notc/data/minecraft/worldgen/structure_set
  echo '{"pack":{"pack_format":48,"description":"no trial chambers"}}' > $D/run/$L/datapacks/notc/pack.mcmeta
  echo '{"placement":{"type":"minecraft:random_spread","salt":94251327,"separation":12,"spacing":34},"structures":[]}' > $D/run/$L/datapacks/notc/data/minecraft/worldgen/structure_set/trial_chambers.json
fi
rm -f $D/run_ab.log
(cd $D && nohup ./gradlew runServer --offline --console=plain > run_ab.log 2>&1 &)
t=0; until grep -q 'Done (' $D/run_ab.log 2>/dev/null; do sleep 3; t=$((t+3)); [ $t -gt 400 ] && { echo timeout; exit 1; }; done
c(){ python $R --port $((P+4)) --password abyss "$@"; }
PID=$(c gentime 20000 20000 4 | grep -o "pid [0-9]*" | cut -d' ' -f2)
$J/jcmd $PID JFR.start name=ab settings=profile filename=$O/$L.jfr stackdepth=128 >/dev/null
: > $O/$L.txt
for X in "-3000 -9000" "4000 4000" "-12000 -2000"; do echo "== $X" >> $O/$L.txt; c gentime $X 6 | grep -E "^wall|^cpu" >> $O/$L.txt; done
$J/jcmd $PID JFR.stop name=ab >/dev/null
c cmd "save-all flush" >/dev/null
$J/jfr print --events jdk.ExecutionSample --stack-depth 128 $O/$L.jfr > $O/$L.samples
c cmd "datapack list" | head -2 >> $O/$L.txt
c cmd stop >/dev/null
until ! netstat -ano | grep -q ":$P.*LISTENING"; do sleep 2; done
echo finished $L
