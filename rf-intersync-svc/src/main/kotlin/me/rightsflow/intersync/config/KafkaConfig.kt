package me.rightsflow.intersync.config

import io.confluent.kafka.serializers.KafkaAvroSerializer
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.EnableScheduling

@Configuration
@EnableScheduling
class KafkaConfig {

    @Value("\${spring.cloud.stream.kafka.binder.brokers}") // Получаем адрес брокеров
    private lateinit var bootstrapServers: String

    @Value("\${spring.cloud.stream.kafka.binder.configuration.schema.registry.url}") // Получаем URL реестра
    private lateinit var schemaRegistryUrl: String

    @Bean
    fun kafkaTemplate(): KafkaTemplate<String, GenericRecord?> {

        val producerProps = mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java.name,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to KafkaAvroSerializer::class.java.name,
            "schema.registry.url" to schemaRegistryUrl,
            ProducerConfig.ACKS_CONFIG to "all",
            ProducerConfig.RETRIES_CONFIG to 3,
            ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG to true
        )

        val producerFactory = DefaultKafkaProducerFactory<String, GenericRecord?>(producerProps)
        return KafkaTemplate(producerFactory)
    }

}
