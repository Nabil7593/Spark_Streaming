import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types._

object SparkConsumerStream {
  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder()
      .appName("Kafka CSV Consumer Spark")
      .master("local[*]") // Ignoré en mode cluster
      .getOrCreate()

    spark.sparkContext.setLogLevel("WARN")
    import spark.implicits._

    // Lire un exemple local de CSV pour deviner le schéma
    val sampleDF = spark.read
      .option("header", "true")
      .option("inferSchema", "true")
      .option("delimiter", ",")
      .csv("bank.csv")

    val schema = sampleDF.schema

    println("📋 Schéma deviné automatiquement :")
    schema.printTreeString()

    // Lecture depuis Kafka
    val kafkaStream = spark.readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", "kafka:9092")
      .option("subscribe", "csv-topic")
      .option("startingOffsets", "earliest")
      .option("failOnDataLoss", "false")
      .load()

    // Conversion JSON Kafka ➝ colonnes structurées
    val parsedStream = kafkaStream
      .selectExpr("CAST(value AS STRING)")
      .as[String]
      .select(from_json($"value", schema).as("data"))
      .select("data.*")

    // Écriture vers PostgreSQL Dockerisé
    val query = parsedStream.writeStream
      .foreachBatch { (batchDF: org.apache.spark.sql.Dataset[org.apache.spark.sql.Row], batchId: Long) =>
        println(s"📥 Batch $batchId reçu. Envoi vers PostgreSQL...")

        batchDF.write
          .format("jdbc")
          .option("url", "jdbc:postgresql://postgres:5432/sparkdb") // nom du service Docker
          .option("dbtable", "bank_data")
          .option("user", "admin")
          .option("password", "admin")
          .option("driver", "org.postgresql.Driver")
          .mode("append")
          .save()

        println(s"✅ Batch $batchId inséré dans PostgreSQL")
      }
      .outputMode("append")
      .start()

    query.awaitTermination()
  }
}
