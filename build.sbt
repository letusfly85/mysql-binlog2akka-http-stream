organization := "io.wonder.soft"

name := "mysql-binlog2akka-http-stream"

version := "0.0.1-SNAPSHOT"

scalaVersion := "2.12.4"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

dependencyOverrides += "org.scala-lang" %% "scala-compiler" % scalaVersion.value

libraryDependencies ++= {
  val akkaV       = "2.5.8"
  val akkaHttpV = "10.0.8"
  val scalikeJDBCV = "2.5.1"
  Seq(
    "com.typesafe.akka" %% "akka-actor"              % akkaV,
    "com.typesafe.akka" %% "akka-persistence"        % akkaV,
    "com.typesafe.akka" %% "akka-slf4j"              % akkaV,
    "ch.qos.logback"    %  "logback-classic"         % "1.1.7",
    "com.typesafe.akka" %% "akka-stream"             % akkaV,
    "com.typesafe.akka" %% "akka-http-core"          % akkaHttpV,
    "com.typesafe.akka" %% "akka-http"               % akkaHttpV,
    "com.typesafe.akka" %% "akka-http-spray-json"    % akkaHttpV,
    "com.typesafe.akka" %% "akka-http-testkit"       % akkaHttpV,

    "org.scalikejdbc" %% "scalikejdbc"                     % scalikeJDBCV,
    "org.scalikejdbc" %% "scalikejdbc-config"              % scalikeJDBCV,
    "mysql" % "mysql-connector-java" % "5.1.28",

    "com.github.shyiko" % "mysql-binlog-connector-java" % "0.13.0",

    "com.amazonaws" % "amazon-kinesis-producer" % "0.12.8",

    "commons-io" % "commons-io" % "2.5",

    "org.specs2" % "specs2_2.12" % "2.4.17" % Test,

    "commons-configuration" % "commons-configuration" % "1.10"
  )
}

//refs: https://github.com/gerferra/amphip/blob/master/build.sbt
scalacOptions ++= Seq(
  "-Ypatmat-exhaust-depth", "off"
)