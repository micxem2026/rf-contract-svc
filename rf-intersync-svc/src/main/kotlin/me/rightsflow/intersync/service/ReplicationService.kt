package me.rightsflow.intersync.service

import me.rightsflow.intersync.dto.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ReplicationService(
    private val syncService: SyncService
) {

    private val log = LoggerFactory.getLogger(ReplicationService::class.java)

    @Transactional
    fun processUser(syncId: Int, message: UsersAvroMessage) {
        try {
            syncService.syncUser(syncId, message)
        } catch (exception: Exception) {
            log.error("Error processing user with id: $syncId", exception)
            throw exception
        }
    }

    @Transactional
    fun processCounterparty(syncId: Int, message: KlfCounterpartyAvroMessage) {
        try {
            syncService.syncKlfCounterparty(syncId, message)
        } catch (exception: Exception) {
            log.error("Error processing counterparty with id: $syncId", exception)
            throw exception
        }
    }

    @Transactional
    fun processOrganization(syncId: Int, message: KlfOrganizationAvroMessage) {
        try {
            syncService.syncKlfOrganization(syncId, message)
        } catch (exception: Exception) {
            log.error("Error processing organization with id: $syncId", exception)
            throw exception
        }
    }

    @Transactional
    fun processOip(syncId: Int, message: KlfOipAvroMessage) {
        try {
            syncService.syncKlfOip(syncId, message)
        } catch (exception: Exception) {
            log.error("Error processing oip with id: $syncId", exception)
            throw exception
        }
    }

    @Transactional
    fun processOipSuperType(syncId: Int, message: LovOipSuperTypeAvroMessage) {
        try {
            syncService.syncLovOipSuperType(syncId, message)
        } catch (exception: Exception) {
            log.error("Error processing oip super type with id: $syncId", exception)
            throw exception
        }
    }

    @Transactional
    fun processOipType(syncId: Int, message: LovOipTypeAvroMessage) {
        try {
            syncService.syncLovOipType(syncId, message)
        } catch (exception: Exception) {
            log.error("Error processing oip type with id: $syncId", exception)
            throw exception
        }
    }

    @Transactional
    fun processRightType(syncId:Int, message: KlfRightTypeAvroMessage) {
        try {
            syncService.syncKlfRightType(syncId, message)
        } catch (exception: Exception) {
            log.error("Error processing right type with id: $syncId", exception)
            throw exception
        }
    }

    @Transactional
    fun processFeatureCategory(syncId:Int, message: KlfFeatureCategoryAvroMessage) {
        try {
            syncService.syncKlfFeatureCategory(syncId, message)
        } catch (exception: Exception) {
            log.error("Error processing feature category with id: $syncId", exception)
            throw exception
        }
    }

    @Transactional
    fun processFeaturePlain(syncId:Int, message: KlfFeaturePlainAvroMessage) {
        try {
            syncService.syncKlfFeaturePlain(syncId, message)
        } catch (exception: Exception) {
            log.error("Error processing feature plain with id: $syncId", exception)
            throw exception
        }
    }

    @Transactional
    fun processFeatureTree(syncId:Int, message: KlfFeatureTreeAvroMessage) {
        try {
            syncService.syncKlfFeatureTree(syncId, message)
        } catch (exception: Exception) {
            log.error("Error processing feature tree with id: $syncId", exception)
            throw exception
        }
    }

    @Transactional
    fun processFeatureCatToRt(syncId:Int, message: KlfFeatureCatToRtAvroMessage) {
        try {
            syncService.syncKlfFeatureCatToRt(syncId, message)
        } catch (exception: Exception) {
            log.error("Error processing feature category to right type with id: $syncId", exception)
            throw exception
        }
    }
}