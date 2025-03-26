// See README.md for license details.

ThisBuild / scalaVersion := "2.13.15" // a safe version is 2.13._
ThisBuild / version := "0.1.0"
ThisBuild / organization := "zjw"

// chisel chaged from scala based compiler for FIRRTL to using firtool since 5._._
// and in 6._._ the firtool is added to chisel lib so no manual install is needed
val chiselVersion = "6.6.0"

lazy val root = (project in file("."))
  .settings(
    name := "%NAME%",
    libraryDependencies ++= Seq(
      "org.chipsalliance" %% "chisel" % chiselVersion,
      // chiseltest6 is ported to Chisel 5 and 6, which is based on Verilator
      // chiseltest automatically includes the correct version of scalatest
      "edu.berkeley.cs" %% "chiseltest" % "6.0.0"
    ),
    scalacOptions ++= Seq(
      "-language:reflectiveCalls",
      "-deprecation",
      "-feature",
      "-Xcheckinit",
      "-Ymacro-annotations"
    ),
    addCompilerPlugin(
      "org.chipsalliance" % "chisel-plugin" % chiselVersion cross CrossVersion.full
    )
  )
