import org.apache.spark.sql.{Dataset, Row, SparkSession}
import org.apache.spark.sql.functions._
import java.util.Properties

object SparkConsumerStream {
  def main(args: Array[String]): Unit = {
    // 1. Initialisation Spark
    val spark = SparkSession.builder()
      .appName("Kafka CSV Consumer Spark")
      .master("local[*]") // Ignoré en cluster
      .config("spark.kafka.bootstrap.servers", "kafka:9092")
      .getOrCreate()
    import spark.implicits._
    spark.sparkContext.setLogLevel("WARN")

    // 2. Inférence du schéma à partir d'un sample local
    val sampleDF = spark.read
      .option("header", "true")
      .option("inferSchema", "true")
      .option("delimiter", ",")
      .csv("bank.csv")            // <-- adapte le chemin si besoin
    val schema = sampleDF.schema
    println("📋 Schéma deviné automatiquement :")
    schema.printTreeString()
    
    // 3. Lecture du flux Kafka
    val kafkaStream = spark.readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", "kafka:9092")
      .option("subscribe", "csv-topic")
      .option("startingOffsets", "earliest")
      .option("failOnDataLoss", "false")
      .load()

    // 4. Parsing JSON → colonnes structurées
    val parsedStream = kafkaStream
      .selectExpr("CAST(value AS STRING)")
      .as[String]
      .select(from_json($"value", schema).as("data"))
      .select("data.*")

    // 5. Préparation de la connexion PostgreSQL
    val jdbcUrl = "jdbc:postgresql://postgres:5432/sparkdb"
    val pgProps = new Properties()
    pgProps.setProperty("user", "admin")
    pgProps.setProperty("password", "admin")
    pgProps.setProperty("driver", "org.postgresql.Driver")

    // 6. Traitement et écriture par micro-batch
    val query = parsedStream.writeStream
      .foreachBatch { (batchDF: Dataset[Row], batchId: Long) =>
        println(s"▶️  Micro-batch $batchId")

        // 📌 Sur le premier batch : overwrite, sinon append
        val writeMode = if (batchId == 0) "overwrite" else "append"

        // 6.1. Stockage des données brutes
        batchDF.write
          .mode(writeMode)
          .jdbc(jdbcUrl, "bank_data", pgProps)

        // 6.2. Filtrer uniquement les « yes »
        val dfYes = batchDF.filter($"deposit" === "yes")

        // ——————————————
        // AGRÉGAT #1 : nombre de dépôts “yes” par métier
        val byJob = dfYes
          .groupBy($"job")
          .count()
          .withColumnRenamed("count", "nb_depots")
        byJob.write.mode("append")
          .jdbc(jdbcUrl, "deposit_by_job", pgProps)

        // AGRÉGAT #2 : nombre de dépôts “yes” par situation familiale
        val byMarital = dfYes
          .groupBy($"marital")
          .count()
          .withColumnRenamed("count", "nb_depots")
        byMarital.write.mode("append")
          .jdbc(jdbcUrl, "deposit_by_marital", pgProps)

        // AGRÉGAT #3 : nombre de dépôts “yes” par niveau d’éducation
        val byEducation = dfYes
          .groupBy($"education")
          .count()
          .withColumnRenamed("count", "nb_depots")
        byEducation.write.mode("append")
          .jdbc(jdbcUrl, "deposit_by_education", pgProps)

        // AGRÉGAT #4 : taux de souscription “yes” par type de contact
        val contactStats = batchDF
          .groupBy($"contact")
          .agg(
            count("*").as("total"),
            sum(when($"deposit" === "yes", 1).otherwise(0)).as("yes_count")
          )
          .withColumn("yes_rate", $"yes_count" / $"total")
        contactStats.write.mode("append")
          .jdbc(jdbcUrl, "yes_rate_by_contact", pgProps)

        // AGRÉGAT #5 : distribution dépôts (yes/no) par type de logement
        val housingDist = batchDF
          .groupBy($"housing", $"deposit")
          .count()
          .withColumnRenamed("count", "nb")
        housingDist.write.mode("append")
          .jdbc(jdbcUrl, "deposit_by_housing", pgProps)

        println(s"✅ Micro-batch $batchId correctement traité")
      }
      .outputMode("append")  // on accumule les résultats par batch
      .start()

    query.awaitTermination()
  }
}
