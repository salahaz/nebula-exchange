/* Copyright (c) 2020 vesoft inc. All rights reserved.
 *
 * This source code is licensed under Apache 2.0 License.
 */

package com.vesoft.nebula.exchange.reader

import com.vesoft.exchange.common.config.{KafkaSourceConfigEntry, PulsarSourceConfigEntry}
import org.apache.spark.sql.types.StringType
import org.apache.spark.sql.{DataFrame, SparkSession}
import java.nio.file.{Files, Paths}
import org.apache.spark.sql.avro.functions.{from_avro}

/**
  * Spark Streaming
  *
  * @param session
  */
abstract class StreamingBaseReader(override val session: SparkSession) extends Reader {

  override def close(): Unit = {
    session.close()
  }
}

/**
  *
  * @param session
  * @param kafkaConfig
  * @param targetFields
  */
class KafkaReader(override val session: SparkSession,
                  kafkaConfig: KafkaSourceConfigEntry,
                  targetFields: List[String])
    extends StreamingBaseReader(session) {

  require(
    kafkaConfig.server.trim.nonEmpty && kafkaConfig.topic.trim.nonEmpty && targetFields.nonEmpty)

  override def read(): DataFrame = {
    import org.apache.spark.sql.functions._
    import session.implicits._
    val fields = targetFields.distinct
    val reader =
      session.readStream
        .format("kafka")
        .option("kafka.bootstrap.servers", kafkaConfig.server)
        .option("subscribe", kafkaConfig.topic)
        .option("startingOffsets", kafkaConfig.startingOffsets)

    val maxOffsetsPerTrigger = kafkaConfig.maxOffsetsPerTrigger
    if (maxOffsetsPerTrigger.isDefined)
      reader.option("maxOffsetsPerTrigger", maxOffsetsPerTrigger.get)

    val schema = new String(Files.readAllBytes(Paths.get(kafkaConfig.schema)))

    reader
      .load()
      .select($"key".cast(StringType), from_avro($"value", schema).as("value"))
      .select("key", "value.*")
      .toDF()

  }
}

/**
  *
  * @param session
  * @param pulsarConfig
  */
class PulsarReader(override val session: SparkSession, pulsarConfig: PulsarSourceConfigEntry)
    extends StreamingBaseReader(session) {

  override def read(): DataFrame = {
    session.readStream
      .format("pulsar")
      .option("service.url", pulsarConfig.serviceUrl)
      .option("admin.url", pulsarConfig.adminUrl)
      .options(pulsarConfig.options)
      .load()
  }
}
