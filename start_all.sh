#!/bin/bash
set -e

echo "➡️  1. Compilation du projet Scala (.jar) avec sbt clean package..."
sbt clean package
echo "✅ Jar compilé avec succès."

echo "➡️  2. Arrêt et relancement de Docker (Kafka + Spark + Streamlit)..."
docker-compose down -v
docker-compose build
docker-compose up -d

echo "⏳ Attente initiale pour laisser démarrer Kafka, Spark, PostgreSQL..."
sleep 15
echo "✅ Tous les containers sont UP."

echo "🕐 Attente active de Kafka (port 9092)..."
for i in {1..10}; do
  if docker exec projet_spark_streaming-kafka-1 \
    kafka-topics --bootstrap-server kafka:9092 --list >/dev/null 2>&1; then
    echo "✅ Kafka est prêt."
    break
  else
    echo "⏳ Kafka pas encore prêt... tentative $i"
    sleep 5
  fi
done

echo "➡️  3. Suppression du topic Kafka 'csv_topic' (s'il existe déjà)..."
docker exec -i projet_spark_streaming-kafka-1 kafka-topics \
  --delete --topic csv_topic \
  --bootstrap-server kafka:9092 || true

sleep 3

echo "➡️  4. Création propre du topic Kafka 'csv_topic'..."
docker exec -i projet_spark_streaming-kafka-1 kafka-topics \
  --create \
  --topic csv_topic \
  --partitions 1 \
  --replication-factor 1 \
  --bootstrap-server kafka:9092

echo "🕐 Pause après création du topic pour stabilisation..."
sleep 10

echo "✅ Topic Kafka 'csv_topic' prêt."

# 🎯 Lancement du Spark Producer
docker exec -i spark-master \
  spark-submit \
    --class SparkProducerBatch \
    --master spark://spark-master:7077 \
    --deploy-mode client \
    --packages org.apache.spark:spark-sql-kafka-0-10_2.12:3.4.1,org.postgresql:postgresql:42.7.5 \
    /app/projet_spark_streaming_2.12-0.1.jar &


# ⏱ Pause pour laisser le Producer envoyer les premiers batches
sleep 60

# 🛰 Lancement du Spark Consumer
docker exec -i spark-master \
  spark-submit \
    --class SparkConsumerStream \
    --master spark://spark-master:7077 \
    --deploy-mode client \
    --packages org.apache.spark:spark-sql-kafka-0-10_2.12:3.4.1,org.postgresql:postgresql:42.7.5 \
    /app/projet_spark_streaming_2.12-0.1.jar

echo "✅ Pipeline Spark Streaming terminé avec succès 🚀"
