import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._
import java.util.concurrent.TimeUnit

object SparkProducerBatch {
  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder()
      .appName("Spark Kafka Producer Batch")
      .master("local[*]")
      .getOrCreate()

    import spark.implicits._

    val filePath = "/opt/bank/bank.csv"
    val topic = "csv_topic"

    val df = spark.read
      .option("header", "true")
      .option("inferSchema", "true")
      .csv(filePath)
      .cache()

    val totalCount = df.count()
    val batchSize = 1000
    val numBatches = (totalCount / batchSize).toInt + (if (totalCount % batchSize > 0) 1 else 0)

    println(s"📦 Total : $totalCount lignes, envoi de $numBatches batchs de $batchSize lignes...")

    for (batchId <- 0 until numBatches) {
      val batchDF = df
        .withColumn("row_id", monotonically_increasing_id())
        .filter($"row_id" >= batchId * batchSize && $"row_id" < (batchId + 1) * batchSize)
        .drop("row_id")

      val batchToKafka = batchDF
        .select(to_json(struct(batchDF.columns.map(col): _*)).as("value"))
        .selectExpr("CAST(NULL AS STRING) as key", "value")

      println(s"🚀 Envoi du batch $batchId ...")
      batchToKafka
        .write
        .format("kafka")
        .option("kafka.bootstrap.servers", "kafka:9092")
        .option("topic", topic)
        .save()

      println(s"✅ Batch $batchId envoyé. Pause 30s...")
      TimeUnit.SECONDS.sleep(30)
    }

    println("✅ Tous les batchs ont été envoyés avec succès.")
    spark.stop()
  }
}
