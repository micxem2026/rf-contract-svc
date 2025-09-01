package me.rightsflow.intersync.scheduler

import me.rightsflow.intersync.config.MessageConverter
import me.rightsflow.intersync.dto.*
import org.springframework.stereotype.Component
import me.rightsflow.intersync.service.ReplicationService
import org.apache.avro.generic.GenericRecord
import org.springframework.beans.factory.annotation.Value


@Component
class UserDlqHandler(
    private val replicationService: ReplicationService,
    @Value("\${spring.cloud.stream.kafka.bindings.userProcessor-in-0.consumer.dlq-name}")
    override val topic: String
) : DlqHandler {

    override fun process(key: String, value: GenericRecord?) {
        val syncId = key.substringAfter("=").substringBefore("}").trim().toInt()
        val userDto = when (value) {
            null -> UsersAvroMessage(null,"","","","",false,
                false,false,0L,0L,0L,
                0L,"")
            else -> MessageConverter.convertToUserAvroMessage(value)
        }
        replicationService.processUser(syncId, userDto)
    }
}

@Component
class oipSuperTypeDlqHandler(
    private val replicationService: ReplicationService,
    @Value("\${spring.cloud.stream.kafka.bindings.oipSuperTypeProcessor-in-0.consumer.dlq-name}")
    override val topic: String
) : DlqHandler {

    override fun process(key: String, value: GenericRecord?) {
        val syncId = key.substringAfter("=").substringBefore("}").trim().toInt()
        val oipSuperTypeDto = when (value) {
            null -> LovOipSuperTypeAvroMessage(null,"")
            else -> MessageConverter.convertToLovOipSuperTypeAvroMessage(value)
        }
        replicationService.processOipSuperType(syncId, oipSuperTypeDto)
    }
}

@Component
class oipTypeDlqHandler(
    private val replicationService: ReplicationService,
    @Value("\${spring.cloud.stream.kafka.bindings.oipTypeProcessor-in-0.consumer.dlq-name}")
    override val topic: String
) : DlqHandler {

    override fun process(key: String, value: GenericRecord?) {
        val syncId = key.substringAfter("=").substringBefore("}").trim().toInt()
        val oipTypeDto = when (value) {
            null -> LovOipTypeAvroMessage(null,null,"")
            else -> MessageConverter.convertToLovOipTypeAvroMessage(value)
        }
        replicationService.processOipType(syncId, oipTypeDto)
    }
}

@Component
class oipDlqHandler(
    private val replicationService: ReplicationService,
    @Value("\${spring.cloud.stream.kafka.bindings.oipProcessor-in-0.consumer.dlq-name}")
    override val topic: String
) : DlqHandler {

    override fun process(key: String, value: GenericRecord?) {
        val syncId = key.substringAfter("=").substringBefore("}").trim().toInt()
        val oipDto = when (value) {
            null -> KlfOipAvroMessage(null,"",null,null,"",null,
                null,null,"",null,null,null)
            else -> MessageConverter.convertToKlfOipAvroMessage(value)
        }
        replicationService.processOip(syncId, oipDto)
    }
}

@Component
class organizationDlqHandler(
    private val replicationService: ReplicationService,
    @Value("\${spring.cloud.stream.kafka.bindings.organizationProcessor-in-0.consumer.dlq-name}")
    override val topic: String
) : DlqHandler {

    override fun process(key: String, value: GenericRecord?) {
        val syncId = key.substringAfter("=").substringBefore("}").trim().toInt()
        val organizationDto = when (value) {
            null -> KlfOrganizationAvroMessage(null,"","","",null,null,null)
            else -> MessageConverter.convertToKlfOrganizationAvroMessage(value)
        }
        replicationService.processOrganization(syncId, organizationDto)
    }
}

@Component
class counterpartyDlqHandler(
    private val replicationService: ReplicationService,
    @Value("\${spring.cloud.stream.kafka.bindings.counterpartyProcessor-in-0.consumer.dlq-name}")
    override val topic: String
) : DlqHandler {

    override fun process(key: String, value: GenericRecord?) {
        val syncId = key.substringAfter("=").substringBefore("}").trim().toInt()
        val counterpartyDto = when (value) {
            null -> KlfCounterpartyAvroMessage(null,"","","",null,null,null)
            else -> MessageConverter.convertToKlfCounterpartyAvroMessage(value)
        }
        replicationService.processCounterparty(syncId, counterpartyDto)
    }
}

@Component
class rightTypeDlqHandler(
    private val replicationService: ReplicationService,
    @Value("\${spring.cloud.stream.kafka.bindings.rightTypeProcessor-in-0.consumer.dlq-name}")
    override val topic: String
) : DlqHandler {

    override fun process(key: String, value: GenericRecord?) {
        val syncId = key.substringAfter("=").substringBefore("}").trim().toInt()
        val rightTypeDto = when (value) {
            null -> KlfRightTypeAvroMessage(null,null,"","",null,null,null)
            else -> MessageConverter.convertToKlfRightTypeAvroMessage(value)
        }
        replicationService.processRightType(syncId, rightTypeDto)
    }
}

@Component
class featureCategoryDlqHandler(
    private val replicationService: ReplicationService,
    @Value("\${spring.cloud.stream.kafka.bindings.featureCategoryProcessor-in-0.consumer.dlq-name}")
    override val topic: String
) : DlqHandler {

    override fun process(key: String, value: GenericRecord?) {
        val syncId = key.substringAfter("=").substringBefore("}").trim().toInt()
        val featureCategoryDto = when (value) {
            null -> KlfFeatureCategoryAvroMessage(null,"","",null,null,null)
            else -> MessageConverter.convertToKlfFeatureCategoryAvroMessage(value)
        }
        replicationService.processFeatureCategory(syncId, featureCategoryDto)
    }
}

@Component
class featurePlainDlqHandler(
    private val replicationService: ReplicationService,
    @Value("\${spring.cloud.stream.kafka.bindings.featurePlainProcessor-in-0.consumer.dlq-name}")
    override val topic: String
) : DlqHandler {

    override fun process(key: String, value: GenericRecord?) {
        val syncId = key.substringAfter("=").substringBefore("}").trim().toInt()
        val featurePlainDto = when (value) {
            null -> KlfFeaturePlainAvroMessage(null,"",null,"",null,
                null,null)
            else -> MessageConverter.convertToKlfFeaturePlainAvroMessage(value)
        }
        replicationService.processFeaturePlain(syncId, featurePlainDto)
    }
}

@Component
class featureTreeDlqHandler(
    private val replicationService: ReplicationService,
    @Value("\${spring.cloud.stream.kafka.bindings.featureTreeProcessor-in-0.consumer.dlq-name}")
    override val topic: String
) : DlqHandler {

    override fun process(key: String, value: GenericRecord?) {
        val syncId = key.substringAfter("=").substringBefore("}").trim().toInt()
        val featureTreeDto = when (value) {
            null -> KlfFeatureTreeAvroMessage(null,null,null,null,null,
                "",null,null,null)
            else -> MessageConverter.convertToKlfFeatureTreeAvroMessage(value)
        }
        replicationService.processFeatureTree(syncId, featureTreeDto)
    }
}