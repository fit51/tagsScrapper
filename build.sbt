name := "TagsScrapper"

version := "0.1"

scalaVersion := "2.12.6"

libraryDependencies ++= Dependencies.all

mainClass in assembly := Some("tags.scrapper.Run")
assemblyJarName in assembly := "tags-scrapper-" + version.value + ".jar"

unmanagedSourceDirectories in Test += sourceDirectory.value / "it" / "scala"