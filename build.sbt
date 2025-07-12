name := "projet_spark_streaming"

version := "0.1"

scalaVersion := "2.12.18"

libraryDependencies ++= Seq(
  "org.apache.spark" %% "spark-core" % "3.5.1",
  "org.apache.spark" %% "spark-sql" % "3.5.1",
  "org.apache.spark" %% "spark-sql-kafka-0-10" % "3.5.1", // mets la même version !
  "org.postgresql" % "postgresql" % "42.7.5"
)
