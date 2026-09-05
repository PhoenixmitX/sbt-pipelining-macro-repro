# sbt 2 pipelining: incremental compile hands downstream a macro-defining upstream's TASTy-only early jar

Reproduction for an sbt 2.0.8 regression (sbt 1.13.0 is fine). Report to **sbt/sbt**.

## Reproduce

```bash
sbt compile                                   # 1. clean build: [success]
echo "// edit" >> app/src/main/scala/app/Use.scala
sbt compile                                   # 2. incremental: [error] in app/Use.scala
```

Step 2 fails with:

```
[error] -- Error: app/src/main/scala/app/Use.scala:4:24
[error] 4 |  val impl: Impl = Impl.make
[error]   |                   ^^^^^^^^^
[error]   |Macro code depends on trait Base in package core found on the classpath, but could not be loaded while evaluating the macro.
[error]   |  This is likely because class files could not be found in the classpath entry for the symbol.
[error]   |  A possible cause is if the origin of this symbol was built with pipelined compilation;
[error]   |  in which case, this problem may go away by disabling pipelining for that origin.
[error]   |  trait Logger is defined in file target/out/jvm/scala-3.9.0/core/early/core_3-0.1.0-SNAPSHOT.jar(core/Base.tasty)
```

## What differs between the two runs

With `app / scalacOptions += "-Ylog-classpath"`, the `core` entry scalac is given for `app` is:

| build | core entry on app's compiler classpath |
|---|---|
| clean | `target/out/jvm/scala-3.9.0/core/classes` (full class files) |
| incremental (core up to date) | `target/out/jvm/scala-3.9.0/core/early/core_3-0.1.0-SNAPSHOT.jar` (TASTy only) |

`core` defines a macro. On the clean build sbt correctly does not compile `app` against
core's early output (the classic "upstream has macros" pipelining fallback), but on the
incremental build, where `core` is already up to date, `app` is compiled against the early jar
anyway. Evaluating `app`'s own macro loads `app.Impl`, whose supertype `core.Base` then has
no class file on the classpath, so the compiler reports the error above.

## Matrix

| sbt | Scala | clean | incremental after editing `app/Use.scala` |
|---|---|---|---|
| 2.0.8 (latest 2.x) | 3.9.0 | ok | **fails** |
| 2.0.8 | 3.8.4 | ok | **fails** |
| 1.13.0 (latest 1.x) | 3.9.0 | ok | ok |

Both `usePipelining := true` globally. JDK 25.0.4, Linux x86_64.

## Workaround

`core / exportPipelining := false` fixes it (no early jar is produced for `core`).
`core / usePipelining := false` does **not** help: an early jar is still exported and the
incremental failure remains.

## Related, but not the same bug

If `core` defines no macro at all, `app` (which does define a macro whose classes extend the
`core` trait) fails on the *clean* build too, on both sbt 1.13.0 and 2.0.8, because sbt only
guards the "upstream defines macros" case. That is the documented limitation the compiler
message points at, and `core / exportPipelining := false` is the intended fix. The regression
reported here is the clean/incremental inconsistency in the macro-defining-upstream case.
