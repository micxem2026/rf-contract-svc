package me.rightsflow.intersync.config

import me.rightsflow.intersync.dto.*
import me.rightsflow.intersync.service.ReplicationService
import org.apache.avro.generic.GenericRecord
import org.apache.avro.util.Utf8
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.support.Acknowledgment
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.kafka.support.KafkaNull
import org.springframework.messaging.Message
import java.time.Instant
import java.util.function.Consumer

@Configuration
class StreamProcessors(
    private val replicationService: ReplicationService
) {

    private val log = LoggerFactory.getLogger(StreamProcessors::class.java)


    @Bean
    fun userProcessor(): Consumer<Message<Any>> {
        return Consumer { message ->

            // Извлечение ключа из заголовков
            val keyString = message.headers["kafka_receivedMessageKey"]?.toString()
            if (keyString == null) {
                log.warn("userProcessor -> A tombstone message without a key was received. The message will be ignored.")
            }
            // Извлечение Acknowledgment из заголовков
            val acknowledgment = message.headers.get(KafkaHeaders.ACKNOWLEDGMENT, Acknowledgment::class.java)
            if (acknowledgment == null) {
                log.warn("userProcessor -> No Acknowledgment found in headers for message with id: $keyString")
            }
            if (keyString != null) {
                val syncId = keyString.substringAfter("=").substringBefore("}").trim().toInt()
                log.info("userProcessor -> Received sync message with id: $syncId")
                val userDto = when (message.payload) {
                    is GenericRecord -> MessageConverter.convertToUserAvroMessage(message.payload as GenericRecord)
                    is KafkaNull -> UsersAvroMessage(null,"","","","",false,
                        false,false,0L,0L,0L,0L,"")
                    else -> throw IllegalArgumentException("userProcessor -> Unsupported message type: ${message.payload.javaClass}")
                }
                replicationService.processUser(syncId, userDto)
                acknowledgment?.acknowledge()
                log.info("userProcessor -> Successfully processed message with id: ${syncId}")
            }
        }
    }

    @Bean
    fun counterpartyProcessor(): Consumer<Message<Any>> {
        return Consumer { message ->

            // Извлечение ключа из заголовков
            val keyString = message.headers["kafka_receivedMessageKey"]?.toString()
            if (keyString == null) {
                log.warn("counterpartyProcessor -> A tombstone message without a key was received. The message will be ignored.")
            }
            // Извлечение Acknowledgment из заголовков
            val acknowledgment = message.headers.get(KafkaHeaders.ACKNOWLEDGMENT, Acknowledgment::class.java)
            if (acknowledgment == null) {
                log.warn("counterpartyProcessor -> No Acknowledgment found in headers for message with id: $keyString")
            }
            if (keyString != null) {
                val syncId = keyString.substringAfter("=").substringBefore("}").trim().toInt()
                log.info("counterpartyProcessor -> Received sync message with id: $syncId")
                val counterpartyDto = when (message.payload) {
                    is GenericRecord -> MessageConverter.convertToKlfCounterpartyAvroMessage(message.payload as GenericRecord)
                    is KafkaNull -> KlfCounterpartyAvroMessage(null,null, "", "",null,"",
                        Instant.EPOCH, null,null)
                    else -> throw IllegalArgumentException("counterpartyProcessor -> Unsupported message type: ${message.payload.javaClass}")
                }
                replicationService.processCounterparty(syncId, counterpartyDto)
                acknowledgment?.acknowledge()
                log.info("counterpartyProcessor -> Successfully processed message with id: ${syncId}")
            }
        }
    }

    @Bean
    fun organizationProcessor(): Consumer<Message<Any>> {
        return Consumer { message ->

            // Извлечение ключа из заголовков
            val keyString = message.headers["kafka_receivedMessageKey"]?.toString()
            if (keyString == null) {
                log.warn("organizationProcessor -> A tombstone message without a key was received. The message will be ignored.")
            }
            // Извлечение Acknowledgment из заголовков
            val acknowledgment = message.headers.get(KafkaHeaders.ACKNOWLEDGMENT, Acknowledgment::class.java)
            if (acknowledgment == null) {
                log.warn("organizationProcessor -> No Acknowledgment found in headers for message with id: $keyString")
            }
            if (keyString != null) {
                val syncId = keyString.substringAfter("=").substringBefore("}").trim().toInt()
                log.info("organizationProcessor -> Received sync message with id: $syncId")
                val organizationDto = when (message.payload) {
                    is GenericRecord -> MessageConverter.convertToKlfOrganizationAvroMessage(message.payload as GenericRecord)
                    is KafkaNull -> KlfOrganizationAvroMessage(null,null,"","","",
                        Instant.EPOCH,null,null)
                    else -> throw IllegalArgumentException("organizationProcessor -> Unsupported message type: ${message.payload.javaClass}")
                }
                replicationService.processOrganization(syncId, organizationDto)
                acknowledgment?.acknowledge()
                log.info("organizationProcessor -> Successfully processed message with id: ${syncId}")
            }
        }
    }

    @Bean
    fun oipProcessor(): Consumer<Message<Any>> {
        return Consumer { message ->

            // Извлечение ключа из заголовков
            val keyString = message.headers["kafka_receivedMessageKey"]?.toString()
            if (keyString == null) {
                log.warn("oipProcessor -> A tombstone message without a key was received. The message will be ignored.")
            }
            // Извлечение Acknowledgment из заголовков
            val acknowledgment = message.headers.get(KafkaHeaders.ACKNOWLEDGMENT, Acknowledgment::class.java)
            if (acknowledgment == null) {
                log.warn("oipProcessor -> No Acknowledgment found in headers for message with id: $keyString")
            }
            if (keyString != null) {
                val syncId = keyString.substringAfter("=").substringBefore("}").trim().toInt()
                log.info("oipProcessor -> Received sync message with id: $syncId")
                val oipDto = when (message.payload) {
                    is GenericRecord -> MessageConverter.convertToKlfOipAvroMessage(message.payload as GenericRecord)
                    is KafkaNull -> KlfOipAvroMessage(null,null,null,null,
                        "", null, null, null, null, false, false,
                        0, null, "", "","", "", Instant.EPOCH,
                        null, null)
                    else -> throw IllegalArgumentException("oipProcessor -> Unsupported message type: ${message.payload.javaClass}")
                }
                replicationService.processOip(syncId, oipDto)
                acknowledgment?.acknowledge()
                log.info("oipProcessor -> Successfully processed message with id: ${syncId}")
            }
        }
    }

    @Bean
    fun oipSuperTypeProcessor(): Consumer<Message<Any>> {
        return Consumer { message ->

            // Извлечение ключа из заголовков
            val keyString = message.headers["kafka_receivedMessageKey"]?.toString()
            if (keyString == null) {
                log.warn("oipSuperTypeProcessor -> A tombstone message without a key was received. The message will be ignored.")
            }
            // Извлечение Acknowledgment из заголовков
            val acknowledgment = message.headers.get(KafkaHeaders.ACKNOWLEDGMENT, Acknowledgment::class.java)
            if (acknowledgment == null) {
                log.warn("oipSuperTypeProcessor -> No Acknowledgment found in headers for message with id: $keyString")
            }
            if (keyString != null) {
                val syncId = keyString.substringAfter("=").substringBefore("}").trim().toInt()
                log.info("oipSuperTypeProcessor -> Received sync message with id: $syncId")
                val oipSuperTypeDto = when (message.payload) {
                    is GenericRecord -> MessageConverter.convertToLovOipSuperTypeAvroMessage(message.payload as GenericRecord)
                    is KafkaNull -> LovOipSuperTypeAvroMessage(null,"")
                    else -> throw IllegalArgumentException("oipSuperTypeProcessor -> Unsupported message type: ${message.payload.javaClass}")
                }
                replicationService.processOipSuperType(syncId, oipSuperTypeDto)
                acknowledgment?.acknowledge()
                log.info("oipSuperTypeProcessor -> Successfully processed message with id: ${syncId}")

            }
        }
    }

    @Bean
    fun oipTypeProcessor(): Consumer<Message<Any>> {
        return Consumer { message ->

            // Извлечение ключа из заголовков
            val keyString = message.headers["kafka_receivedMessageKey"]?.toString()
            if (keyString == null) {
                log.warn("oipTypeProcessor -> A tombstone message without a key was received. The message will be ignored.")
            }
            // Извлечение Acknowledgment из заголовков
            val acknowledgment = message.headers.get(KafkaHeaders.ACKNOWLEDGMENT, Acknowledgment::class.java)
            if (acknowledgment == null) {
                log.warn("oipTypeProcessor -> No Acknowledgment found in headers for message with id: $keyString")
            }
            if (keyString != null) {
                val syncId = keyString.substringAfter("=").substringBefore("}").trim().toInt()
                log.info("oipTypeProcessor -> Received sync message with id: $syncId")
                val oipTypeDto = when (message.payload) {
                    is GenericRecord -> MessageConverter.convertToLovOipTypeAvroMessage(message.payload as GenericRecord)
                    is KafkaNull -> LovOipTypeAvroMessage(null,null,"")
                    else -> throw IllegalArgumentException("oipTypeProcessor -> Unsupported message type: ${message.payload.javaClass}")
                }
                replicationService.processOipType(syncId, oipTypeDto)
                acknowledgment?.acknowledge()
                log.info("oipTypeProcessor -> Successfully processed message with id: ${syncId}")
            }
        }
    }

    @Bean
    fun rightTypeProcessor(): Consumer<Message<Any>> {
        return Consumer { message ->

            // Извлечение ключа из заголовков
            val keyString = message.headers["kafka_receivedMessageKey"]?.toString()
            if (keyString == null) {
                log.warn("rightTypeProcessor -> A tombstone message without a key was received. The message will be ignored.")
            }
            // Извлечение Acknowledgment из заголовков
            val acknowledgment = message.headers.get(KafkaHeaders.ACKNOWLEDGMENT, Acknowledgment::class.java)
            if (acknowledgment == null) {
                log.warn("rightTypeProcessor -> No Acknowledgment found in headers for message with id: $keyString")
            }
            if (keyString != null) {
                val syncId = keyString.substringAfter("=").substringBefore("}").trim().toInt()
                log.info("rightTypeProcessor -> Received sync message with id: $syncId")
                val rightTypeDto = when (message.payload) {
                    is GenericRecord -> MessageConverter.convertToKlfRightTypeAvroMessage(message.payload as GenericRecord)
                    is KafkaNull -> KlfRightTypeAvroMessage(null,null,"","",null,
                        "",Instant.EPOCH,null,null)
                    else -> throw IllegalArgumentException("rightTypeProcessor -> Unsupported message type: ${message.payload.javaClass}")
                }
                replicationService.processRightType(syncId, rightTypeDto)
                acknowledgment?.acknowledge()
                log.info("rightTypeProcessor -> Successfully processed message with id: ${syncId}")
            }
        }
    }

    @Bean
    fun featureCategoryProcessor(): Consumer<Message<Any>> {
        return Consumer { message ->

            // Извлечение ключа из заголовков
            val keyString = message.headers["kafka_receivedMessageKey"]?.toString()
            if (keyString == null) {
                log.warn("featureCategoryProcessor -> A tombstone message without a key was received. The message will be ignored.")
            }
            // Извлечение Acknowledgment из заголовков
            val acknowledgment = message.headers.get(KafkaHeaders.ACKNOWLEDGMENT, Acknowledgment::class.java)
            if (acknowledgment == null) {
                log.warn("featureCategoryProcessor -> No Acknowledgment found in headers for message with id: $keyString")
            }
            if (keyString != null) {
                val syncId = keyString.substringAfter("=").substringBefore("}").trim().toInt()
                log.info("featureCategoryProcessor -> Received sync message with id: $syncId")
                val featureCategoryDto = when (message.payload) {
                    is GenericRecord -> MessageConverter.convertToKlfFeatureCategoryAvroMessage(message.payload as GenericRecord)
                    is KafkaNull -> KlfFeatureCategoryAvroMessage(null,"","",Instant.EPOCH,
                        null,null)
                    else -> throw IllegalArgumentException("featureCategoryProcessor -> Unsupported message type: ${message.payload.javaClass}")
                }
                replicationService.processFeatureCategory(syncId, featureCategoryDto)
                acknowledgment?.acknowledge()
                log.info("featureCategoryProcessor -> Successfully processed message with id: ${syncId}")
            }
        }
    }

    @Bean
    fun featurePlainProcessor(): Consumer<Message<Any>> {
        return Consumer { message ->

            // Извлечение ключа из заголовков
            val keyString = message.headers["kafka_receivedMessageKey"]?.toString()
            if (keyString == null) {
                log.warn("featurePlainProcessor -> A tombstone message without a key was received. The message will be ignored.")
            }
            // Извлечение Acknowledgment из заголовков
            val acknowledgment = message.headers.get(KafkaHeaders.ACKNOWLEDGMENT, Acknowledgment::class.java)
            if (acknowledgment == null) {
                log.warn("featurePlainProcessor -> No Acknowledgment found in headers for message with id: $keyString")
            }
            if (keyString != null) {
                val syncId = keyString.substringAfter("=").substringBefore("}").trim().toInt()
                log.info("featurePlainProcessor -> Received sync message with id: $syncId")
                val featurePlainDto = when (message.payload) {
                    is GenericRecord -> MessageConverter.convertToKlfFeaturePlainAvroMessage(message.payload as GenericRecord)
                    is KafkaNull -> KlfFeaturePlainAvroMessage(null,"",null,"",Instant.EPOCH,
                        null,null)
                    else -> throw IllegalArgumentException("featurePlainProcessor -> Unsupported message type: ${message.payload.javaClass}")
                }
                replicationService.processFeaturePlain(syncId, featurePlainDto)
                acknowledgment?.acknowledge()
                log.info("featurePlainProcessor -> Successfully processed message with id: ${syncId}")
            }
        }
    }

    @Bean
    fun featureTreeProcessor(): Consumer<Message<Any>> {
        return Consumer { message ->

            // Извлечение ключа из заголовков
            val keyString = message.headers["kafka_receivedMessageKey"]?.toString()
            if (keyString == null) {
                log.warn("featureTreeProcessor -> A tombstone message without a key was received. The message will be ignored.")
            }
            // Извлечение Acknowledgment из заголовков
            val acknowledgment = message.headers.get(KafkaHeaders.ACKNOWLEDGMENT, Acknowledgment::class.java)
            if (acknowledgment == null) {
                log.warn("featureTreeProcessor -> No Acknowledgment found in headers for message with id: $keyString")
            }
            if (keyString != null) {
                val syncId = keyString.substringAfter("=").substringBefore("}").trim().toInt()
                log.info("featureTreeProcessor -> Received sync message with id: $syncId")
                val featureTreeDto = when (message.payload) {
                    is GenericRecord -> MessageConverter.convertToKlfFeatureTreeAvroMessage(message.payload as GenericRecord)
                    is KafkaNull -> KlfFeatureTreeAvroMessage(null,null,null,null,null,
                        "",Instant.EPOCH,null,null)
                    else -> throw IllegalArgumentException("featureTreeProcessor -> Unsupported message type: ${message.payload.javaClass}")
                }
                replicationService.processFeatureTree(syncId, featureTreeDto)
                acknowledgment?.acknowledge()
                log.info("featureTreeProcessor -> Successfully processed message with id: ${syncId}")
            }
        }
    }

    @Bean
    fun featureCatToRtProcessor(): Consumer<Message<Any>> {
        return Consumer { message ->

            // Извлечение ключа из заголовков
            val keyString = message.headers["kafka_receivedMessageKey"]?.toString()
            if (keyString == null) {
                log.warn("featureCatToRtProcessor -> A tombstone message without a key was received. The message will be ignored.")
            }
            // Извлечение Acknowledgment из заголовков
            val acknowledgment = message.headers.get(KafkaHeaders.ACKNOWLEDGMENT, Acknowledgment::class.java)
            if (acknowledgment == null) {
                log.warn("featureCatToRtProcessor -> No Acknowledgment found in headers for message with id: $keyString")
            }
            if (keyString != null) {
                val syncId = keyString.substringAfter("=").substringBefore("}").trim().toInt()
                log.info("featureCatToRtProcessor -> Received sync message with id: $syncId")
                val featureCatToRtDto = when (message.payload) {
                    is GenericRecord -> MessageConverter.convertToKlfFeatureCatToRtAvroMessage(message.payload as GenericRecord)
                    is KafkaNull -> KlfFeatureCatToRtAvroMessage(null,0,0,null,"",Instant.EPOCH,
                        null,null)
                    else -> throw IllegalArgumentException("featureCatToRtProcessor -> Unsupported message type: ${message.payload.javaClass}")
                }
                replicationService.processFeatureCatToRt(syncId, featureCatToRtDto)
                acknowledgment?.acknowledge()
                log.info("featureCatToRtProcessor -> Successfully processed message with id: ${syncId}")
            }
        }
    }

    @Bean
    fun oipHierarchyProcessor(): Consumer<Message<Any>> {
        return Consumer { message ->

            // Извлечение ключа из заголовков
            val keyString = message.headers["kafka_receivedMessageKey"]?.toString()
            if (keyString == null) {
                log.warn("oipHierarchyProcessor -> A tombstone message without a key was received. The message will be ignored.")
            }
            // Извлечение Acknowledgment из заголовков
            val acknowledgment = message.headers.get(KafkaHeaders.ACKNOWLEDGMENT, Acknowledgment::class.java)
            if (acknowledgment == null) {
                log.warn("oipHierarchyProcessor -> No Acknowledgment found in headers for message with id: $keyString")
            }
            if (keyString != null) {
                val syncId = keyString.substringAfter("=").substringBefore("}").trim().toInt()
                log.info("oipHierarchyProcessor -> Received sync message with id: $syncId")
                val oipHierarchyDto = when (message.payload) {
                    is GenericRecord -> MessageConverter.convertToKlfOipHierarchyAvroMessage(message.payload as GenericRecord)
                    is KafkaNull -> KlfOipHierarchyAvroMessage(null,0,0,"",Instant.EPOCH,
                        null,null)
                    else -> throw IllegalArgumentException("oipHierarchyProcessor -> Unsupported message type: ${message.payload.javaClass}")
                }
                replicationService.processOipHierarchy(syncId, oipHierarchyDto)
                acknowledgment?.acknowledge()
                log.info("oipHierarchyProcessor -> Successfully processed message with id: ${syncId}")
            }
        }
    }

    @Bean
    fun objectProcessor(): Consumer<Message<Any>> {
        return Consumer { message ->

            // Извлечение ключа из заголовков
            val keyString = message.headers["kafka_receivedMessageKey"]?.toString()
            if (keyString == null) {
                log.warn("objectProcessor -> A tombstone message without a key was received. The message will be ignored.")
            }
            // Извлечение Acknowledgment из заголовков
            val acknowledgment = message.headers.get(KafkaHeaders.ACKNOWLEDGMENT, Acknowledgment::class.java)
            if (acknowledgment == null) {
                log.warn("objectProcessor -> No Acknowledgment found in headers for message with id: $keyString")
            }
            if (keyString != null) {
                val syncId = keyString.substringAfter("=").substringBefore("}").trim().toInt()
                log.info("objectProcessor -> Received sync message with id: $syncId")
                val objectDto = when (message.payload) {
                    is GenericRecord -> MessageConverter.convertToLovObjectAvroMessage(message.payload as GenericRecord)
                    is KafkaNull -> LovObjectAvroMessage(null,"","","",0)
                    else -> throw IllegalArgumentException("objectProcessor -> Unsupported message type: ${message.payload.javaClass}")
                }
                replicationService.processObject(syncId, objectDto)
                acknowledgment?.acknowledge()
                log.info("objectProcessor -> Successfully processed message with id: ${syncId}")
            }

        }
    }

    @Bean
    fun pgePgLayerProcessor(): Consumer<Message<Any>> {
        return Consumer { message ->

            // Извлечение ключа из заголовков
            val keyString = message.headers["kafka_receivedMessageKey"]?.toString()
            if (keyString == null) {
                log.warn("pgePgLayerProcessor -> A tombstone message without a key was received. The message will be ignored.")
            }
            // Извлечение Acknowledgment из заголовков
            val acknowledgment = message.headers.get(KafkaHeaders.ACKNOWLEDGMENT, Acknowledgment::class.java)
            if (acknowledgment == null) {
                log.warn("pgePgLayerProcessor -> No Acknowledgment found in headers for message with id: $keyString")
            }
            if (keyString != null) {
                val syncId = keyString.substringAfter("=").substringBefore("}").trim().toInt()
                log.info("pgePgLayerProcessor -> Received sync message with id: $syncId")
                val pgePgLayerDto = when (message.payload) {
                    is GenericRecord -> MessageConverter.convertToLovPgePgLayerAvroMessage(message.payload as GenericRecord)
                    is KafkaNull -> LovPgePgLayerAvroMessage(null,0,"")
                    else -> throw IllegalArgumentException("pgePgLayerProcessor -> Unsupported message type: ${message.payload.javaClass}")
                }
                replicationService.processPgePgLayer(syncId, pgePgLayerDto)
                acknowledgment?.acknowledge()
                log.info("pgePgLayerProcessor -> Successfully processed message with id: ${syncId}")
            }

        }
    }

    @Bean
    fun pgePgToObjProcessor(): Consumer<Message<Any>> {
        return Consumer { message ->

            // Извлечение ключа из заголовков
            val keyString = message.headers["kafka_receivedMessageKey"]?.toString()
            if (keyString == null) {
                log.warn("pgePgToObjProcessor -> A tombstone message without a key was received. The message will be ignored.")
            }
            // Извлечение Acknowledgment из заголовков
            val acknowledgment = message.headers.get(KafkaHeaders.ACKNOWLEDGMENT, Acknowledgment::class.java)
            if (acknowledgment == null) {
                log.warn("pgePgToObjProcessor -> No Acknowledgment found in headers for message with id: $keyString")
            }
            if (keyString != null) {
                val syncId = keyString.substringAfter("=").substringBefore("}").trim().toInt()
                log.info("pgePgToObjProcessor -> Received sync message with id: $syncId")
                val pgePgToObjDto = when (message.payload) {
                    is GenericRecord -> MessageConverter.convertToLovPgePgToObjAvroMessage(message.payload as GenericRecord)
                    is KafkaNull -> LovPgePgToObjAvroMessage(null,0,"")
                    else -> throw IllegalArgumentException("pgePgToObjProcessor -> Unsupported message type: ${message.payload.javaClass}")
                }
                replicationService.processPgePgToObj(syncId, pgePgToObjDto)
                acknowledgment?.acknowledge()
                log.info("pgePgToObjProcessor -> Successfully processed message with id: ${syncId}")
            }

        }
    }

    @Bean
    fun pgePglDtlProcessor(): Consumer<Message<Any>> {
        return Consumer { message ->

            // Извлечение ключа из заголовков
            val keyString = message.headers["kafka_receivedMessageKey"]?.toString()
            if (keyString == null) {
                log.warn("pgePglDtlProcessor -> A tombstone message without a key was received. The message will be ignored.")
            }
            // Извлечение Acknowledgment из заголовков
            val acknowledgment = message.headers.get(KafkaHeaders.ACKNOWLEDGMENT, Acknowledgment::class.java)
            if (acknowledgment == null) {
                log.warn("pgePglDtlProcessor -> No Acknowledgment found in headers for message with id: $keyString")
            }
            if (keyString != null) {
                val syncId = keyString.substringAfter("=").substringBefore("}").trim().toInt()
                log.info("pgePglDtlProcessor -> Received sync message with id: $syncId")
                val pgePglDtlDto = when (message.payload) {
                    is GenericRecord -> MessageConverter.convertToLovPgePglDtlAvroMessage(message.payload as GenericRecord)
                    is KafkaNull -> LovPgePglDtlAvroMessage(null,0,0,"","",0)
                    else -> throw IllegalArgumentException("pgePglDtlProcessor -> Unsupported message type: ${message.payload.javaClass}")
                }
                replicationService.processPgePglDtl(syncId, pgePglDtlDto)
                acknowledgment?.acknowledge()
                log.info("pgePglDtlProcessor -> Successfully processed message with id: ${syncId}")
            }

        }
    }

    @Bean
    fun pgePropTypeProcessor(): Consumer<Message<Any>> {
        return Consumer { message ->

            // Извлечение ключа из заголовков
            val keyString = message.headers["kafka_receivedMessageKey"]?.toString()
            if (keyString == null) {
                log.warn("pgePropTypeProcessor -> A tombstone message without a key was received. The message will be ignored.")
            }
            // Извлечение Acknowledgment из заголовков
            val acknowledgment = message.headers.get(KafkaHeaders.ACKNOWLEDGMENT, Acknowledgment::class.java)
            if (acknowledgment == null) {
                log.warn("pgePropTypeProcessor -> No Acknowledgment found in headers for message with id: $keyString")
            }
            if (keyString != null) {
                val syncId = keyString.substringAfter("=").substringBefore("}").trim().toInt()
                log.info("pgePropTypeProcessor -> Received sync message with id: $syncId")
                val pgePropTypeDto = when (message.payload) {
                    is GenericRecord -> MessageConverter.convertToLovPgePropTypeAvroMessage(message.payload as GenericRecord)
                    is KafkaNull -> LovPgePropTypeAvroMessage(null,"",null, false)
                    else -> throw IllegalArgumentException("pgePropTypeProcessor -> Unsupported message type: ${message.payload.javaClass}")
                }
                replicationService.processPgePropType(syncId, pgePropTypeDto)
                acknowledgment?.acknowledge()
                log.info("pgePropTypeProcessor -> Successfully processed message with id: ${syncId}")
            }

        }
    }

    @Bean
    fun pgePropertyProcessor(): Consumer<Message<Any>> {
        return Consumer { message ->

            // Извлечение ключа из заголовков
            val keyString = message.headers["kafka_receivedMessageKey"]?.toString()
            if (keyString == null) {
                log.warn("pgePropertyProcessor -> A tombstone message without a key was received. The message will be ignored.")
            }
            // Извлечение Acknowledgment из заголовков
            val acknowledgment = message.headers.get(KafkaHeaders.ACKNOWLEDGMENT, Acknowledgment::class.java)
            if (acknowledgment == null) {
                log.warn("pgePropertyProcessor -> No Acknowledgment found in headers for message with id: $keyString")
            }
            if (keyString != null) {
                val syncId = keyString.substringAfter("=").substringBefore("}").trim().toInt()
                log.info("pgePropertyProcessor -> Received sync message with id: $syncId")
                val pgePropertyDto = when (message.payload) {
                    is GenericRecord -> MessageConverter.convertToLovPgePropertyAvroMessage(message.payload as GenericRecord)
                    is KafkaNull -> LovPgePropertyAvroMessage(null,"","", 0)
                    else -> throw IllegalArgumentException("pgePropertyProcessor -> Unsupported message type: ${message.payload.javaClass}")
                }
                replicationService.processPgeProperty(syncId, pgePropertyDto)
                acknowledgment?.acknowledge()
                log.info("pgePropertyProcessor -> Successfully processed message with id: ${syncId}")
            }

        }
    }

    @Bean
    fun pgePropertyGroupProcessor(): Consumer<Message<Any>> {
        return Consumer { message ->

            // Извлечение ключа из заголовков
            val keyString = message.headers["kafka_receivedMessageKey"]?.toString()
            if (keyString == null) {
                log.warn("pgePropertyGroupProcessor -> A tombstone message without a key was received. The message will be ignored.")
            }
            // Извлечение Acknowledgment из заголовков
            val acknowledgment = message.headers.get(KafkaHeaders.ACKNOWLEDGMENT, Acknowledgment::class.java)
            if (acknowledgment == null) {
                log.warn("pgePropertyGroupProcessor -> No Acknowledgment found in headers for message with id: $keyString")
            }
            if (keyString != null) {
                val syncId = keyString.substringAfter("=").substringBefore("}").trim().toInt()
                log.info("pgePropertyGroupProcessor -> Received sync message with id: $syncId")
                val pgePropertyGroupDto = when (message.payload) {
                    is GenericRecord -> MessageConverter.convertToLovPgePropertyGroupAvroMessage(message.payload as GenericRecord)
                    is KafkaNull -> LovPgePropertyGroupAvroMessage(null,"","", "", 0)
                    else -> throw IllegalArgumentException("pgePropertyGroupProcessor -> Unsupported message type: ${message.payload.javaClass}")
                }
                replicationService.processPgePropertyGroup(syncId, pgePropertyGroupDto)
                acknowledgment?.acknowledge()
                log.info("pgePropertyGroupProcessor -> Successfully processed message with id: ${syncId}")
            }

        }
    }


}

object MessageConverter {

    fun convertToUserAvroMessage(record: GenericRecord): UsersAvroMessage {
        return UsersAvroMessage(
            id = record.get("id") as Int,
            username = record.getString("username"),
            display_name = record.getString("display_name"),
            email = record.getString("email"),
            password_hash = record.getString("password_hash"),
            enabled = record.getBooleanOrNull("enabled"),
            account_non_expired = record.getBooleanOrNull("account_non_expired"),
            account_non_locked = record.getBooleanOrNull("account_non_locked"),
            expiration_date = record.get("expiration_date") as Long?,
            last_logon = record.get("last_logon") as Long?,
            created_at = record.get("created_at") as Long?,
            updated_at = record.get("updated_at") as Long?,
            user_type = record.getString("user_type")
        )
    }

    fun convertToKlfCounterpartyAvroMessage(record: GenericRecord): KlfCounterpartyAvroMessage {
        return KlfCounterpartyAvroMessage(
            id = record.get("id") as Int?,
            guid = record.getStringOrNull("guid"),
            code_1c = record.getStringOrNull("code_1c"),
            name = record.getString("name"),
            id_org_ref = record.get("id_org_ref") as Int?,
            created_by = record.getString("created_by"),
            created_at = record.getStringOrNull("created_at")?.let { Instant.parse(it)},
            updated_by = record.getStringOrNull("updated_by"),
            updated_at = record.getStringOrNull("updated_at")?.let { Instant.parse(it)}
        )
    }

    fun convertToKlfOrganizationAvroMessage(record: GenericRecord): KlfOrganizationAvroMessage {
        return KlfOrganizationAvroMessage(
            id = record.get("id") as Int?,
            guid = record.getStringOrNull("guid"),
            code_1c = record.getStringOrNull("code_1c"),
            name = record.getString("name"),
            created_by = record.getString("created_by"),
            created_at = record.getStringOrNull("created_at")?.let { Instant.parse(it)},
            updated_by = record.getStringOrNull("updated_by"),
            updated_at = record.getStringOrNull("updated_at")?.let { Instant.parse(it)}
        )
    }

    fun convertToLovOipSuperTypeAvroMessage(record: GenericRecord): LovOipSuperTypeAvroMessage {
        return LovOipSuperTypeAvroMessage(
            id = record.get("id") as Int?,
            name = record.getString("name")
        )
    }

    fun convertToLovOipTypeAvroMessage(record: GenericRecord): LovOipTypeAvroMessage {
        return LovOipTypeAvroMessage(
            id = record.get("id") as Int?,
            id_oip_super_type = record.get("id_oip_super_type") as Int?,
            name = record.getString("name")
        )
    }

    fun convertToKlfOipAvroMessage(record: GenericRecord): KlfOipAvroMessage {
        return KlfOipAvroMessage(
            id = record.get("id") as Int?,
            guid = record.getStringOrNull("guid"),
            id_oip_super_type = record.get("id_oip_super_type") as Int?,
            id_oip_type = record.get("id_oip_type") as Int?,
            name = record.getString("name"),
            part_num = record.get("part_num") as Int?,
            part_count = record.get("part_count") as Int?,
            duration = record.get("duration") as Long?,
            has_parent = record.getBoolean("has_parent"),
            has_children = record.getBoolean("has_children"),
            children_count = record.get("children_count") as Int?,
            root_id = record.get("root_id") as Int?,
            native_name = record.getStringOrNull("native_name"),
            full_name = record.getStringOrNull("full_name"),
            release_year = record.getStringOrNull("release_year"),
            description = record.getString("description"),
            created_by = record.getString("created_by"),
            created_at = record.getStringOrNull("created_at")?.let { Instant.parse(it)},
            updated_by = record.getStringOrNull("updated_by"),
            updated_at = record.getStringOrNull("updated_at")?.let { Instant.parse(it)}
        )
    }

    fun convertToKlfRightTypeAvroMessage(record: GenericRecord): KlfRightTypeAvroMessage {
        return KlfRightTypeAvroMessage(
            id = record.get("id") as Int?,
            id_parent = record.get("id_parent") as Int?,
            name = record.getString("name"),
            description = record.getStringOrNull("description"),
            id_right_group = record.get("id_right_group") as Int?,
            created_by = record.getString("created_by"),
            created_at = record.getStringOrNull("created_at")?.let { Instant.parse(it)},
            updated_by = record.getStringOrNull("updated_by"),
            updated_at = record.getStringOrNull("updated_at")?.let { Instant.parse(it)}
        )
    }

    fun convertToKlfFeatureCategoryAvroMessage(record: GenericRecord): KlfFeatureCategoryAvroMessage {
        return KlfFeatureCategoryAvroMessage(
            id = record.get("id") as Int?,
            name = record.getString("name"),
            created_by = record.getString("created_by"),
            created_at = record.getStringOrNull("created_at")?.let { Instant.parse(it)},
            updated_by = record.getStringOrNull("updated_by"),
            updated_at = record.getStringOrNull("updated_at")?.let { Instant.parse(it)},
        )
    }

    fun convertToKlfFeaturePlainAvroMessage(record: GenericRecord): KlfFeaturePlainAvroMessage {
        return KlfFeaturePlainAvroMessage(
            id = record.get("id") as Int?,
            name = record.getString("name"),
            id_feature_category = record.get("id_feature_category") as Int?,
            created_by = record.getString("created_by"),
            created_at = record.getStringOrNull("created_at")?.let { Instant.parse(it)},
            updated_by = record.getStringOrNull("updated_by"),
            updated_at = record.getStringOrNull("updated_at")?.let { Instant.parse(it)}
        )
    }

    fun convertToKlfFeatureTreeAvroMessage(record: GenericRecord): KlfFeatureTreeAvroMessage {
        return KlfFeatureTreeAvroMessage(
            id = record.get("id") as Int?,
            id_parent = record.get("id_parent") as Int?,
            id_feature_category = record.get("id_feature_category") as Int?,
            id_feature_plain = record.get("id_feature_plain") as Int?,
            validity_period = record.getStringOrNull("validity_period"),
            created_by = record.getString("created_by"),
            created_at = record.getStringOrNull("created_at")?.let { Instant.parse(it)},
            updated_by = record.getStringOrNull("updated_by"),
            updated_at = record.getStringOrNull("updated_at")?.let { Instant.parse(it)}
        )
    }

    fun convertToKlfFeatureCatToRtAvroMessage(record: GenericRecord): KlfFeatureCatToRtAvroMessage {
        return KlfFeatureCatToRtAvroMessage(
            id = record.get("id") as Int?,
            id_right_type = record.get("id_right_type") as Int,
            id_feature_category = record.get("id_feature_category") as Int,
            id_def_feature = record.get("id_def_feature") as Int?,
            created_by = record.getString("created_by"),
            created_at = record.getStringOrNull("created_at")?.let { Instant.parse(it)},
            updated_by = record.getStringOrNull("updated_by"),
            updated_at = record.getStringOrNull("updated_at")?.let { Instant.parse(it)}
        )
    }

    fun convertToKlfOipHierarchyAvroMessage(record: GenericRecord): KlfOipHierarchyAvroMessage {
        return KlfOipHierarchyAvroMessage(
            id = record.get("id") as Int?,
            id_parent = record.get("id_parent") as Int,
            id_oip = record.get("id_oip") as Int,
            created_by = record.getString("created_by"),
            created_at = record.getStringOrNull("created_at")?.let { Instant.parse(it)},
            updated_by = record.getStringOrNull("updated_by"),
            updated_at = record.getStringOrNull("updated_at")?.let { Instant.parse(it)}
        )
    }

    fun convertToLovObjectAvroMessage(record: GenericRecord): LovObjectAvroMessage {
        return LovObjectAvroMessage(
            id = record.get("id") as Int?,
            name = record.getString("name"),
            table_name = record.getString("table_name"),
            where_filter = record.getStringOrNull("where_filter"),
            svc_id = record.get("svc_id") as Int
        )
    }

    fun convertToLovPgePgLayerAvroMessage(record: GenericRecord): LovPgePgLayerAvroMessage {
        return LovPgePgLayerAvroMessage(
            id = record.get("id") as Int?,
            id_pg = record.get("id_pg") as Int,
            sel_value = record.getString("sel_value")
        )
    }

    fun convertToLovPgePgToObjAvroMessage(record: GenericRecord): LovPgePgToObjAvroMessage {
        return LovPgePgToObjAvroMessage(
            id = record.get("id") as Int?,
            id_obj = record.get("id_obj") as Int,
            code_pg = record.getString("code_pg")
        )
    }

    fun convertToLovPgePglDtlAvroMessage(record: GenericRecord): LovPgePglDtlAvroMessage {
        return LovPgePglDtlAvroMessage(
            id = record.get("id") as Int?,
            id_pgl = record.get("id_pgl") as Int,
            id_property = record.get("id_property") as Int,
            property_format = record.getStringOrNull("property_format"),
            default_value = record.getStringOrNull("default_value"),
            pg_order = record.get("pg_order") as Int
        )
    }

    fun convertToLovPgePropTypeAvroMessage(record: GenericRecord): LovPgePropTypeAvroMessage {
        return LovPgePropTypeAvroMessage(
            id = record.get("id") as Int?,
            id_obj = record.get("id_obj") as Int?,
            name = record.getString("name"),
            use_multi_select = record.getBoolean("use_multi_select")
        )
    }

    fun convertToLovPgePropertyAvroMessage(record: GenericRecord): LovPgePropertyAvroMessage {
        return LovPgePropertyAvroMessage(
            id = record.get("id") as Int?,
            code = record.getString("code"),
            name = record.getString("name"),
            id_prop_type = record.get("id_prop_type") as Int
        )
    }

    fun convertToLovPgePropertyGroupAvroMessage(record: GenericRecord): LovPgePropertyGroupAvroMessage {
        return LovPgePropertyGroupAvroMessage(
            id = record.get("id") as Int?,
            code = record.getString("code"),
            name = record.getString("name"),
            layer_sel_query = record.getString("layer_sel_query"),
            svc_id = record.get("svc_id") as Int
        )
    }

    private fun GenericRecord.getBooleanOrNull(fieldName: String): Boolean? {
        this.schema.getField(fieldName) ?: return null;
        return when (val value = this.get(fieldName)) {
            is Boolean -> value
            else -> null
        }
    }

    private fun GenericRecord.getBoolean(fieldName: String): Boolean {
        return getBooleanOrNull(fieldName) ?: false
    }

    private fun GenericRecord.getStringOrNull(fieldName: String): String? {
        this.schema.getField(fieldName) ?: return null // Поле отсутствует в схеме
        return when (val value = this.get(fieldName)) {
            is Utf8 -> value.toString()
            is String -> value
            null -> null
            else -> value.toString()
        }
    }

    private fun GenericRecord.getString(fieldName: String): String {
        return getStringOrNull(fieldName) ?: ""
    }

    private fun GenericRecord.getRequiredString(fieldName: String): String {
        return getStringOrNull(fieldName) ?: throw IllegalArgumentException("Field $fieldName is required but was null")
    }
}