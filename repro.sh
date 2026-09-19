#!/bin/sh
# Reproduces: after a target wipe, sbt 2's action cache restores an upstream module's classes and
# analysis but not its pipelining early (TASTy) jar; the next incremental compile of that module
# then creates the early jar with only the recompiled units, and downstream fails to resolve
# every unchanged upstream type.
set +e
cd "$(dirname "$0")"
git checkout -- core app                      # start from the committed sources
rm -f core/src/main/scala/core/Paged.scala

echo "### 1. clean build"
sbt compile
echo "### 2. wipe the build output (the global cache under ~/.cache/sbt/v2 stays)"
if [ "${WIPE:-rm}" = clean ]; then sbt clean; else rm -rf target/out; fi
echo "### 3. rebuild: core is restored from the action cache -> no core/early jar"
sbt compile
ls target/out/jvm/scala-3.9.0/core/early 2>/dev/null || echo "core early jar: ABSENT"
echo "core classes dir on disk: $(find target/out/jvm/scala-3.9.0/core/classes -type f 2>/dev/null | wc -l) files"
echo "### 4. add a type to core and use it from app, then compile incrementally"
cat > core/src/main/scala/core/Paged.scala <<'SRC'
package core

final case class Paged[T](rows: List[Row[T]], page: Int)
SRC
cat >> app/src/main/scala/app/Use.scala <<'SRC'

object UsePaged:
  val paged: Paged[Item] = Paged(List(Use.row), 0)
SRC
sbt compile
echo "### after: what downstream saw"
ls target/out/jvm/scala-3.9.0/core/early 2>/dev/null && unzip -l target/out/jvm/scala-3.9.0/core/early/*.jar | grep -E '\.tasty|\.sig'
echo "core classes dir on disk: $(find target/out/jvm/scala-3.9.0/core/classes -type f 2>/dev/null | sed 's|.*/classes/||' | tr '\n' ' ')"
