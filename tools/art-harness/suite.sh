#!/bin/bash
# usage: suite.sh <IDE apk> <tag> <spec:sc_id>...   creates and builds each fixture project; one log per fixture in out/logs
T=$(cd $(dirname $0) && pwd); APK=$1; TAG=$2; shift 2; mkdir -p $T/out/logs
for f in "$@"; do
  spec=${f%%:*}; id=${f##*:}
  rm -rf /storage/emulated/0/.sketchware/data/$id /storage/emulated/0/.sketchware/mysc/$id /storage/emulated/0/.sketchware/mysc/list/$id
  log=$T/out/logs/$TAG-$spec-$id.log
  $T/run.sh $APK $T/out/root-$TAG create $spec $id > $log 2>&1
  $T/run.sh $APK $T/out/root-$TAG build $id >> $log 2>&1; echo "EXIT $?" >> $log
  echo "$TAG $spec $id: $(grep -c 'STEP .* ok' $log) steps ok $(grep -m1 'STEP .* FAILED' $log) $(tail -1 $log)"
done
