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
    @param:Value("\${spring.cloud.stream.kafka.bindings.userProcessor-in-0.consumer.dlq-name}")
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
class OipSuperTypeDlqHandler(
    private val replicationService: ReplicationService,
    @param:Value("\${spring.cloud.stream.kafka.bindings.oipSuperTypeProcessor-in-0.consumer.dlq-name}")
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
class OipTypeDlqHandler(
    private val replicationService: ReplicationService,
    @param:Value("\${spring.cloud.stream.kafka.bindings.oipTypeProcessor-in-0.consumer.dlq-name}")
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
class OipDlqHandler(
    private val replicationService: ReplicationService,
    @param:Value("\${spring.cloud.stream.kafka.bindings.oipProcessor-in-0.consumer.dlq-name}")
    override val topic: String
) : DlqHandler {

    override fun process(key: String, value: GenericRecord?) {
        val syncId = key.substringAfter("=").substringBefore("}").trim().toInt()
        val oipDto = when (value) {
            null -> KlfOipAvroMessage(null,"",null,null,"",null,
                null,null,null,false,false, 0,
                null, "", "", "", "",null,null,null)
            else -> MessageConverter.convertToKlfOipAvroMessage(value)
        }
        replicationService.processOip(syncId, oipDto)
    }
}

@Component
class OrganizationDlqHandler(
    private val replicationService: ReplicationService,
    @param:Value("\${spring.cloud.stream.kafka.bindings.organizationProcessor-in-0.consumer.dlq-name}")
    override val topic: String
) : DlqHandler {

    override fun process(key: String, value: GenericRecord?) {
        val syncId = key.substringAfter("=").substringBefore("}").trim().toInt()
        val organizationDto = when (value) {
            null -> KlfOrganizationAvroMessage(null,"","","","",null,null,null)
            else -> MessageConverter.convertToKlfOrganizationAvroMessage(value)
        }
        replicationService.processOrganization(syncId, organizationDto)
    }
}

@Component
class CounterpartyDlqHandler(
    private val replicationService: ReplicationService,
    @param:Value("\${spring.cloud.stream.kafka.bindings.counterpartyProcessor-in-0.consumer.dlq-name}")
    override val topic: String
) : DlqHandler {

    override fun process(key: String, value: GenericRecord?) {
        val syncId = key.substringAfter("=").substringBefore("}").trim().toInt()
        val counterpartyDto = when (value) {
            null -> KlfCounterpartyAvroMessage(null,"","","",null,"",null,null,null)
            else -> MessageConverter.convertToKlfCounterpartyAvroMessage(value)
        }
        replicationService.processCounterparty(syncId, counterpartyDto)
    }
}

@Component
class RightTypeDlqHandler(
    private val replicationService: ReplicationService,
    @param:Value("\${spring.cloud.stream.kafka.bindings.rightTypeProcessor-in-0.consumer.dlq-name}")
    override val topic: String
) : DlqHandler {

    override fun process(key: String, value: GenericRecord?) {
        val syncId = key.substringAfter("=").substringBefore("}").trim().toInt()
        val rightTypeDto = when (value) {
            null -> KlfRightTypeAvroMessage(null,null,"","",null,
                                            "",null,null,null)
            else -> MessageConverter.convertToKlfRightTypeAvroMessage(value)
        }
        replicationService.processRightType(syncId, rightTypeDto)
    }
}

@Component
class FeatureCategoryDlqHandler(
    private val replicationService: ReplicationService,
    @param:Value("\${spring.cloud.stream.kafka.bindings.featureCategoryProcessor-in-0.consumer.dlq-name}")
    override val topic: String
) : DlqHandler {

    override fun process(key: String, value: GenericRecord?) {
        val syncId = key.substringAfter("=").substringBefore("}").trim().toInt()
        val featureCategoryDto = when (value) {
            null -> KlfFeatureCategoryAvroMessage(null,"", null, "",null,null,null)
            else -> MessageConverter.convertToKlfFeatureCategoryAvroMessage(value)
        }
        replicationService.processFeatureCategory(syncId, featureCategoryDto)
    }
}

@Component
class FeaturePlainDlqHandler(
    private val replicationService: ReplicationService,
    @param:Value("\${spring.cloud.stream.kafka.bindings.featurePlainProcessor-in-0.consumer.dlq-name}")
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
class FeatureTreeDlqHandler(
    private val replicationService: ReplicationService,
    @param:Value("\${spring.cloud.stream.kafka.bindings.featureTreeProcessor-in-0.consumer.dlq-name}")
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

@Component
class FeatureCatToRtDlqHandler(
    private val replicationService: ReplicationService,
    @param:Value("\${spring.cloud.stream.kafka.bindings.featureCatToRtProcessor-in-0.consumer.dlq-name}")
    override val topic: String
) : DlqHandler {

    override fun process(key: String, value: GenericRecord?) {
        val syncId = key.substringAfter("=").substringBefore("}").trim().toInt()
        val featureCatToRtDto = when (value) {
            null -> KlfFeatureCatToRtAvroMessage(null,0,0,null,"",null,null,null)
            else -> MessageConverter.convertToKlfFeatureCatToRtAvroMessage(value)
        }
        replicationService.processFeatureCatToRt(syncId, featureCatToRtDto)
    }
}

@Component
class OipHierarchyDlqHandler(
    private val replicationService: ReplicationService,
    @param:Value("\${spring.cloud.stream.kafka.bindings.oipHierarchyProcessor-in-0.consumer.dlq-name}")
    override val topic: String
) : DlqHandler {

    override fun process(key: String, value: GenericRecord?) {
        val syncId = key.substringAfter("=").substringBefore("}").trim().toInt()
        val oipHierarchyDto = when (value) {
            null -> KlfOipHierarchyAvroMessage(null,0,0,"",null,null,null)
            else -> MessageConverter.convertToKlfOipHierarchyAvroMessage(value)
        }
        replicationService.processOipHierarchy(syncId, oipHierarchyDto)
    }
}


@Component
class ObjectDlqHandler(
    private val replicationService: ReplicationService,
    @param:Value("\${spring.cloud.stream.kafka.bindings.objectProcessor-in-0.consumer.dlq-name}")
    override val topic: String
) : DlqHandler {

    override fun process(key: String, value: GenericRecord?) {
        val syncId = key.substringAfter("=").substringBefore("}").trim().toInt()
        val objectDto = when (value) {
            null -> LovObjectAvroMessage(null,"","","",0)
            else -> MessageConverter.convertToLovObjectAvroMessage(value)
        }
        replicationService.processObject(syncId, objectDto)
    }
}

@Component
class PgePgLayerDlqHandler(
    private val replicationService: ReplicationService,
    @param:Value("\${spring.cloud.stream.kafka.bindings.pgePgLayerProcessor-in-0.consumer.dlq-name}")
    override val topic: String
) : DlqHandler {

    override fun process(key: String, value: GenericRecord?) {
        val syncId = key.substringAfter("=").substringBefore("}").trim().toInt()
        val pgePgLayerDto = when (value) {
            null -> LovPgePgLayerAvroMessage(null,0,"")
            else -> MessageConverter.convertToLovPgePgLayerAvroMessage(value)
        }
        replicationService.processPgePgLayer(syncId, pgePgLayerDto)
    }
}

@Component
class PgePgToObjDlqHandler(
    private val replicationService: ReplicationService,
    @param:Value("\${spring.cloud.stream.kafka.bindings.pgePgToObjProcessor-in-0.consumer.dlq-name}")
    override val topic: String
) : DlqHandler {

    override fun process(key: String, value: GenericRecord?) {
        val syncId = key.substringAfter("=").substringBefore("}").trim().toInt()
        val pgePgToObjDto = when (value) {
            null -> LovPgePgToObjAvroMessage(null,0,"")
            else -> MessageConverter.convertToLovPgePgToObjAvroMessage(value)
        }
        replicationService.processPgePgToObj(syncId, pgePgToObjDto)
    }
}


@Component
class PgePglDtlDlqHandler(
    private val replicationService: ReplicationService,
    @param:Value("\${spring.cloud.stream.kafka.bindings.pgePglDtlProcessor-in-0.consumer.dlq-name}")
    override val topic: String
) : DlqHandler {

    override fun process(key: String, value: GenericRecord?) {
        val syncId = key.substringAfter("=").substringBefore("}").trim().toInt()
        val pgePglDtlDto = when (value) {
            null -> LovPgePglDtlAvroMessage(null,0,0, "", "", 0)
            else -> MessageConverter.convertToLovPgePglDtlAvroMessage(value)
        }
        replicationService.processPgePglDtl(syncId, pgePglDtlDto)
    }
}

@Component
class PgePropTypeDlqHandler(
    private val replicationService: ReplicationService,
    @param:Value("\${spring.cloud.stream.kafka.bindings.pgePropTypeProcessor-in-0.consumer.dlq-name}")
    override val topic: String
) : DlqHandler {

    override fun process(key: String, value: GenericRecord?) {
        val syncId = key.substringAfter("=").substringBefore("}").trim().toInt()
        val pgePropTypeDto = when (value) {
            null -> LovPgePropTypeAvroMessage(null,"",null, false)
            else -> MessageConverter.convertToLovPgePropTypeAvroMessage(value)
        }
        replicationService.processPgePropType(syncId, pgePropTypeDto)
    }
}

@Component
class PgePropertyDlqHandler(
    private val replicationService: ReplicationService,
    @param:Value("\${spring.cloud.stream.kafka.bindings.pgePropertyProcessor-in-0.consumer.dlq-name}")
    override val topic: String
) : DlqHandler {

    override fun process(key: String, value: GenericRecord?) {
        val syncId = key.substringAfter("=").substringBefore("}").trim().toInt()
        val pgePropertyDto = when (value) {
            null -> LovPgePropertyAvroMessage(null,"","", 0)
            else -> MessageConverter.convertToLovPgePropertyAvroMessage(value)
        }
        replicationService.processPgeProperty(syncId, pgePropertyDto)
    }
}

@Component
class PgePropertyGroupDlqHandler(
    private val replicationService: ReplicationService,
    @param:Value("\${spring.cloud.stream.kafka.bindings.pgePropertyGroupProcessor-in-0.consumer.dlq-name}")
    override val topic: String
) : DlqHandler {

    override fun process(key: String, value: GenericRecord?) {
        val syncId = key.substringAfter("=").substringBefore("}").trim().toInt()
        val pgePropertyGroupDto = when (value) {
            null -> LovPgePropertyGroupAvroMessage(null,"","", "", 0)
            else -> MessageConverter.convertToLovPgePropertyGroupAvroMessage(value)
        }
        replicationService.processPgePropertyGroup(syncId, pgePropertyGroupDto)
    }
}

