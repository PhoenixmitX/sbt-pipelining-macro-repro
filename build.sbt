ThisBuild / scalaVersion := "3.9.0"
ThisBuild / usePipelining := true

lazy val core = project
lazy val app = project.dependsOn(core)
  // uncomment to see which core output scalac is given:
  // .settings(scalacOptions += "-Ylog-classpath")

// Workaround: core / exportPipelining := false
