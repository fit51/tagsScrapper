name := "TagsScrapper"

version := "0.1"

scalaVersion := "2.12.6"

libraryDependencies ++= Dependencies.all

mainClass in assembly := Some("com.example.Main")

unmanagedSourceDirectories in Test += sourceDirectory.value / "it" / "scala"