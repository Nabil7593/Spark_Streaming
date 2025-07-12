import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._
import scala.concurrent.duration._
import java.util.concurrent.TimeUnit

object SparkProducerBatch {
  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder()
      .appName("Spark Kafka Producer Batch")
      .master("local[*]")
      .getOrCreate()

    import spark.implicits._

    val filePath = "bank.csv"
    val topic = "csv-topic"

    // Lecture brute du CSV
    val df = spark.read
      .option("header", "true")
      .option("inferSchema", "true")
      .csv(filePath)
      .cache() // on met en cache car on va le parcourir plusieurs fois

    val totalCount = df.count()
    val batchSize = 1000
    val numBatches = (totalCount / batchSize).toInt + (if (totalCount % batchSize > 0) 1 else 0)

    


    for (batchId <- 0 until numBatches) {
      val batchDF = df
        .withColumn("row_id", monotonically_increasing_id())
        .filter($"row_id" >= batchId * batchSize && $"row_id" < (batchId + 1) * batchSize)
        .drop("row_id")

      val batchToKafka = batchDF.withColumn("value", to_json(struct(batchDF.columns.map(col): _*)))
        .selectExpr("CAST(NULL AS STRING) as key", "value")

      batchToKafka
        .write
        .format("kafka")
        .option("kafka.bootstrap.servers", "kafka:9092")
        .option("topic", topic)
        .option("checkpointLocation", s"/tmp/spark_checkpoint_csv_kafka_batch_$batchId")
        .save()

      println(s"Batch $batchId envoyé (${batchSize.min((totalCount - batchId * batchSize).toInt)} lignes). Pause 30 secondes...")
      TimeUnit.SECONDS.sleep(30)
    }

    println("Tous les batches envoyés ✅")
    spark.stop()
  }
}
