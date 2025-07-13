#!/bin/bash
set -e  # Si une commande échoue ➔ tout s'arrête

echo "➡️  1. Compilation du projet Scala (.jar) avec sbt clean package..."
sbt clean package

echo "✅ Jar compilé avec succès."

echo "➡️  2. Arrêt et relancement de Docker (Kafka + Spark + Streamlit)..."
docker-compose down -v
docker-compose build
docker-compose up -d

echo "⏳  Attente pour laisser démarrer Kafka, Spark, PostgreSQL..."
sleep 15

echo "✅ Tous les containers sont UP."

echo "➡️  3. Création du topic Kafka 'csv-topic' s'il n'existe pas..."
docker exec -i projet_spark_streaming-kafka-1 \
  kafka-topics --create --if-not-exists \
  --bootstrap-server kafka:9092 \
  --replication-factor 1 \
  --partitions 1 \
  --topic csv-topic

echo "✅ Topic 'csv-topic' prêt."

echo "➡️  4. Lancement du Spark Producer dans le cluster Spark..."
docker exec -i spark-master spark-submit \
  --class SparkProducerBatch \
  --master spark://spark-master:7077 \
  --deploy-mode client \
  --jars /app/jars/postgresql-42.7.5.jar \
  --packages org.apache.spark:spark-sql-kafka-0-10_2.12:3.5.1 \
  /app/projet_spark_streaming_2.12-0.1.jar &

echo "⏳  Pause pour laisser le Producer envoyer les premiers batches..."
sleep 60

echo "➡️  5. Lancement du Spark Consumer dans le cluster Spark..."
docker exec -i spark-master spark-submit \
  --class SparkConsumerStream \
  --master spark://spark-master:7077 \
  --deploy-mode client \
  --jars /app/jars/postgresql-42.7.5.jar \
  --packages org.apache.spark:spark-sql-kafka-0-10_2.12:3.5.1 \
  /app/projet_spark_streaming_2.12-0.1.jar

echo "✅ TOUT tourne parfaitement dans ton cluster Spark Dockerisé ! 🚀💥"
