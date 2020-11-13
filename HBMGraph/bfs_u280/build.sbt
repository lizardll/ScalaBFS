
scalaVersion := "2.11.12"

resolvers ++= Seq(
  Resolver.sonatypeRepo("snapshots"),
  Resolver.sonatypeRepo("releases")
)

libraryDependencies += "edu.berkeley.cs" %% "chisel3" % "3.3.2"
libraryDependencies += "edu.berkeley.cs" %% "chisel-iotesters" % "1.4.2"
libraryDependencies += "edu.berkeley.cs" %% "firrtl" % "1.3.2"
