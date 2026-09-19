# sbt 2 pipelining: after an action-cache hit, the upstream early jar is rebuilt from the next incremental round only

Reproduction for sbt 2.0.9 (also 2.0.8). Report to **sbt/sbt**.

Two plain Scala 3 modules, no macros: `core` (`Item`, `Row`) and `app`, which uses them.
`ThisBuild / usePipelining := true`.

## Reproduce

```bash
./repro.sh
```

which does, step by step:

```bash
sbt compile                 # 1. clean build:                            [success]
rm -rf target/out           # 2. wipe the build output (or: sbt clean); ~/.cache/sbt/v2 stays
sbt compile                 # 3. core hits the action cache:             [success], but no core/early jar,
                            #    and target/.../core/classes is empty on disk
# 4. add core/Paged.scala (a new type) and use it from app/Use.scala
sbt compile                 # 5. incremental:                            [error] Not found: type Row / Item
```

Step 5 fails with:

```
[info] compiling 1 Scala source to target/out/jvm/scala-3.9.0/core/classes ...
[info] compiling 1 Scala source to target/out/jvm/scala-3.9.0/app/classes ...
[error] -- [E006] Not Found Error: app/src/main/scala/app/Use.scala:6:11
[error] 6 |  val row: Row[Item] = Row(List(Item("a")))
[error]   |           ^^^
[error]   |           Not found: type Row
[error] -- [E006] Not Found Error: app/src/main/scala/app/Use.scala:6:15
[error]   |               Not found: type Item
...
[error] 5 errors found
```

The new type `Paged` resolves; every *unchanged* `core` type does not. A second `sbt compile`
fails the same way.

Step 2 can also be `sbt clean` (or just `core/clean`). What matters is that step 3 serves `core`'s
`compileIncremental` from the action cache. In this two-module project both wipes do that. In a
larger build `rm -rf target/out` may miss for a module a few times (upstream modules hit first and
that changes the module's classpath shape and cache key) and `<upstream>/clean; compile` with no
source change is the reliable way to get the hit. Check for it: after step 3 the module's `early/`
directory is missing and its on-disk `classes/` directory is empty. After step 5 the early jar `core` exports to `app` holds one entry:

```
$ unzip -l target/out/jvm/scala-3.9.0/core/early/core_3-0.1.0-SNAPSHOT.jar
     2508  core/Paged.tasty
```

and `target/out/jvm/scala-3.9.0/core/classes` holds only `Paged.class`, `Paged$.class`,
`Paged.tasty`, while `classes.sbtdir.zip` and the packaged `core_3-0.1.0-SNAPSHOT.jar` are complete.

## Why

`compileIncremental` is a cached task (`Def.cachedTask`) whose declared outputs are the analysis file
and the classes directory (`sbt.Defaults.cachedCompileIncrementalTask`). The pipelining early jar
(`earlyOutput`) is not among them. On a cache hit Zinc does not run, the early jar is not restored, and
`compileIncremental` re-derives the pipelining decision from the jar's presence on disk:

```scala
// sbt 2.0.9 Defaults.scala, TaskZero / compileIncremental
ping.tryComplete(Result.Value(c.toPath(earlyOutput.value).toFile.exists && !definesMacro(analysis)))
```

So right after the hit there is no early jar and `app` falls back to `core`'s full products: fine.
The next *incremental* compile of `core` is where it breaks. Zinc (2.0.4, `Incremental.scala`) has scalac
write its pickles to a temporary `<early>-<uuid>.jar` and merges that into the real early jar
(`mergeUpdates()` in `AnalysisCallback`, then `PickleJar.write`, which `touch`es the jar if it is missing
and prunes entries whose class is no longer a product). With no jar to merge into, the "merged" jar
contains only the units of that round, `afterEarlyOutput(true)` fires, and `app` is compiled
against a jar that lacks every TASTy `core` did not just recompile.

## Matrix

| sbt | pipelining | step 2 | result of step 5 |
|---|---|---|---|
| 2.0.9 | `ThisBuild / usePipelining := true` | `rm -rf target/out` | **fails** |
| 2.0.9 | `ThisBuild / usePipelining := true` | `sbt clean` | **fails** |
| 2.0.8 | `ThisBuild / usePipelining := true` | `rm -rf target/out` | **fails** |
| 2.0.9 | `ThisBuild / usePipelining := false` | `rm -rf target/out` | ok |
| 2.0.9 | pipelining on, `core / exportPipelining := false` | `rm -rf target/out` | ok |

Scala 3.9.0, JDK 25.0.4, Linux x86_64. The variants ran in separate copies of this directory, each with
its own sbt server (the `sbtn` thin client is the default in sbt 2; copying `project/target/active.json`
along makes a copy talk to the original server).

## Experimenting

The action cache is keyed on inputs, so an edit that was compiled once at a path is served from the
cache when it is repeated at that path, even with the trap armed: no upstream compile line, no early
jar, no error. That reads as a false negative. Use a change that was never compiled before, or set
`Global / localCacheDirectory` to an empty directory for each experiment.

## Recovery / workarounds

- Kill the sbt server, `rm -rf target/out`, `sbt compile`: `core` hits the cache again, so it has no
  early jar and `app` compiles against `core`'s full products. The trap is armed again for the next
  incremental change to `core`.
- `core / exportPipelining := false` (no early jar for `core` at all) or `usePipelining := false`.

## Where this was first seen

A Scala 3 monorepo (sbt 2.0.9, pipelining on): two worktrees had run `<upstream>/clean` plus a
rebuild with the upstream unchanged, then a rebase changed four upstream sources, and the first
incremental compile of the downstream module failed with 55 and 75 "Not found" errors, all for
upstream types the rebase had not touched. The same chain was replayed on that build with a
one-line upstream edit: after `<upstream>/clean; compile` twice, the early jar was missing; the next
edit produced an early jar with one entry and the downstream compile failed.

## Related

- sbt/sbt#9546 — with pipelining, a downstream compile served a stale action-cache result after an
  upstream deletion; also observes an empty `classes` directory in the pipelined build.
- sbt/sbt#9715 (fixed in 2.0.9 by #9719) — the earlier repro on the `main` branch of this repo: a
  macro-defining upstream was put on the pipelined classpath on incremental builds.
