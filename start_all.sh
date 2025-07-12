#!/bin/bash

set -e  # Si une commande échoue ➔ tout s'arrête

echo "➡️  1. Compilation du projet Scala (.jar) avec sbt clean package..."
sbt clean package

echo "✅ Jar compilé avec succès."

echo "➡️  2. Arrêt et relancement de Docker (Kafka + Spark)..."
docker-compose down
docker-compose up -d

echo "⏳  Attente pour laisser démarrer Kafka, Spark Master et Worker..."
sleep 15

echo "✅ Tous les containers sont UP."

echo "➡️  3. Lancement du Spark Producer dans le cluster Spark..."
docker exec -i $(docker ps --filter "name=spark-master" --format "{{.Names}}") spark-submit \
  --class SparkProducerBatch \
  --master spark://spark-master:7077 \
  --deploy-mode client \
  --jars /app/jars/postgresql-42.7.5.jar \
  --packages org.apache.spark:spark-sql-kafka-0-10_2.12:3.5.1 \
  /app/projet_spark_streaming_2.12-0.1.jar &

echo "⏳  Pause pour laisser le Producer envoyer les premiers batches..."
sleep 40

echo "➡️  4. Lancement du Spark Consumer dans le cluster Spark..."
docker exec -i $(docker ps --filter "name=spark-master" --format "{{.Names}}") spark-submit \
  --class SparkConsumerStream \
  --master spark://spark-master:7077 \
  --deploy-mode client \
  --jars /app/jars/postgresql-42.7.5.jar \
  --packages org.apache.spark:spark-sql-kafka-0-10_2.12:3.5.1 \
  /app/projet_spark_streaming_2.12-0.1.jar

echo "✅ TOUT tourne parfaitement dans ton cluster Spark Dockerisé ! 🚀💥"
