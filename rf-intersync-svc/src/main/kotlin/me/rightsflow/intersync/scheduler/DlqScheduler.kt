package me.rightsflow.intersync.scheduler

import io.confluent.kafka.serializers.KafkaAvroDeserializer
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.TimeUnit


@Component
class GenericDlqScheduler(
    @param:Value("\${rightsflow.app.dlg-topic-poll-seconds}")
    private val dlqTopicPollSeconds: Long,
    @param:Value("\${spring.cloud.stream.kafka.binder.brokers}")
    private val bootstrapServers: String,
    @param:Value("\${spring.cloud.stream.kafka.binder.configuration.schema.registry.url}")
    private val schemaRegistryUrl: String,
    private val kafkaTemplate: KafkaTemplate<String, GenericRecord?>,
    private val handlers: List<DlqHandler> // все зарегистрированные хендлеры
) {

    private val log = LoggerFactory.getLogger(GenericDlqScheduler::class.java)

    @Scheduled(cron = "0 0/15 * * * *")
    fun processAllDlqs() {
        if (handlers.isEmpty()) {
            log.info("No DLQ handlers registered")
            return
        }
        handlers.forEach { processDlqTopic(it) }
    }

    private fun processDlqTopic(handler: DlqHandler) {
        val dlqTopic = handler.topic
        log.info("Start processing DLQ: $dlqTopic")

        val consumerProps = mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG to "${dlqTopic}.reprocessor-group",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java.name,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to KafkaAvroDeserializer::class.java.name,
            "schema.registry.url" to schemaRegistryUrl,
            "specific.avro.reader" to "false",
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to "false",
            ConsumerConfig.MAX_POLL_RECORDS_CONFIG to "1000",
            ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG to "30000",
            ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG to "40000",
        )

        KafkaConsumer<String, GenericRecord?>(consumerProps).use { consumer ->
            consumer.subscribe(listOf(dlqTopic))
            val records = consumer.poll(Duration.ofSeconds(dlqTopicPollSeconds))

            if (records.isEmpty) {
                log.info("DLQ topic $dlqTopic is empty")
                return
            }

            val failedRecords = mutableListOf<ConsumerRecord<String, GenericRecord?>>()
            var successCount = 0
            val totalCount = records.count()

            log.info("Received $totalCount messages from DLQ $dlqTopic")

            for (record in records) {
                val key = record.key()
                try {
                    if (key == null) {
                        log.warn("Tombstone without key, ignoring [partition=${record.partition()}, offset=${record.offset()}]")
                        continue
                    }
                    handler.process(key, record.value())
                    successCount++
                    log.debug("Processed key=$key [partition=${record.partition()}, offset=${record.offset()}]")
                } catch (e: Exception) {
                    log.error(
                        "Error processing key=${key} [partition=${record.partition()}, offset=${record.offset()}]. Will be returned to DLQ. ${e.message}",
                        e
                    )
                    failedRecords.add(record)
                }
            }

            if (successCount > 0) {
                log.info("DLQ $dlqTopic: success=$successCount, failed=${failedRecords.size} of $totalCount")

                if (failedRecords.isNotEmpty()) {
                    returnFailedMessagesToDlq(dlqTopic, failedRecords)
                }

                consumer.commitSync()
                log.info("Committed offsets for DLQ $dlqTopic batch of $totalCount")
            } else {
                log.warn("DLQ $dlqTopic: no messages processed successfully, no commit performed")
            }
        }
    }

    private fun returnFailedMessagesToDlq(
        dlqTopic: String,
        failedRecords: List<ConsumerRecord<String, GenericRecord?>>
    ) {
        log.warn("Returning ${failedRecords.size} messages back to DLQ $dlqTopic...")
        var returned = 0
        var errors = 0
        failedRecords.forEach { record ->
            try {
                kafkaTemplate.send(dlqTopic, record.key(), record.value()).get(5, TimeUnit.SECONDS)
                returned++
            } catch (e: Exception) {
                errors++
                log.error("Return error for key=${record.key()} [offset=${record.offset()}]: ${e.message}", e)
            }
        }
        log.info("Returned to DLQ $dlqTopic: ok=$returned, errors=$errors")
    }
}