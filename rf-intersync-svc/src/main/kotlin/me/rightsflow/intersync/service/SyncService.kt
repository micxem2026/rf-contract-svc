package me.rightsflow.intersync.service

import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import me.rightsflow.intersync.dto.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Service
class SyncService {

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    @Transactional
    fun syncUser(pSyncId: Int, dto: UsersAvroMessage): Int {
        val query = entityManager.createNativeQuery(
            "SELECT pkg_sync.sync_users(" +
                    ":pSyncId, " +
                    ":pId, " +
                    ":pUsername, " +
                    ":pDisplayName, " +
                    ":pEmail, " +
                    ":pPasswordHash, " +
                    ":pEnabled, " +
                    ":pAccountNonExpired, " +
                    ":pAccountNonLocked, " +
                    ":pExpirationDate, " +
                    ":pLastLogon, " +
                    ":pCreatedAt, " +
                    ":pUpdatedAt," +
                    ":pUserType" +
                    ")"
        )

        // Установка параметров
        query.setParameter("pSyncId", pSyncId)
        query.setParameter("pId", dto.id)
        query.setParameter("pUsername", dto.username)
        query.setParameter("pDisplayName", dto.display_name)
        query.setParameter("pEmail", dto.email)
        query.setParameter("pPasswordHash", dto.password_hash)
        query.setParameter("pEnabled", dto.enabled)
        query.setParameter("pAccountNonExpired", dto.account_non_expired)
        query.setParameter("pAccountNonLocked", dto.account_non_locked)
        query.setParameter("pExpirationDate", dto.expiration_date?.let { microsToOffsetDateTime(it) })
        query.setParameter("pLastLogon", dto.last_logon?.let { microsToOffsetDateTime(it) })
        query.setParameter("pCreatedAt", dto.created_at?.let { microsToOffsetDateTime(it) })
        query.setParameter("pUpdatedAt", dto.updated_at?.let { microsToOffsetDateTime(it) })
        query.setParameter("pUserType", dto.user_type)
        // Выполнение запроса и возврат результата
        //throw RuntimeException("Sync error")
        return query.singleResult as Int
    }

    @Transactional
    fun syncKlfCounterparty(pSyncId: Int, dto: KlfCounterpartyAvroMessage): Int {
        val query = entityManager.createNativeQuery(
            "SELECT pkg_sync.sync_klf_counterparty(" +
                    ":pSyncId, " +
                    ":pId, " +
                    ":pGuid, " +
                    ":pName, " +
                    ":pIdOrgRef, " +
                    ":pCreatedBy, " +
                    ":pCreatedAt, " +
                    ":pUpdatedBy, " +
                    ":pUpdatedAt" +
                    ")"
        )

        query.setParameter("pSyncId", pSyncId)
        query.setParameter("pId", dto.id)
        query.setParameter("pGuid", dto.guid)
        query.setParameter("pName", dto.name)
        query.setParameter("pIdOrgRef", dto.id_org_ref)
        query.setParameter("pCreatedBy", dto.created_by)
        query.setParameter("pCreatedAt", dto.created_at?.let { microsToOffsetDateTime(it) })
        query.setParameter("pUpdatedBy", dto.updated_by)
        query.setParameter("pUpdatedAt", dto.updated_at?.let { microsToOffsetDateTime(it) })

        return query.singleResult as Int
    }

    @Transactional
    fun syncKlfOrganization(pSyncId: Int, dto: KlfOrganizationAvroMessage): Int {
        val query = entityManager.createNativeQuery(
            "SELECT pkg_sync.sync_klf_organization(" +
                    ":pSyncId, " +
                    ":pId, " +
                    ":pGuid, " +
                    ":pName, " +
                    ":pCreatedBy, " +
                    ":pCreatedAt, " +
                    ":pUpdatedBy, " +
                    ":pUpdatedAt" +
                    ")"
        )

        query.setParameter("pSyncId", pSyncId)
        query.setParameter("pId", dto.id)
        query.setParameter("pGuid", dto.guid)
        query.setParameter("pName", dto.name)
        query.setParameter("pCreatedBy", dto.created_by)
        query.setParameter("pCreatedAt", dto.created_at?.let { microsToOffsetDateTime(it) })
        query.setParameter("pUpdatedBy", dto.updated_by)
        query.setParameter("pUpdatedAt", dto.updated_at?.let { microsToOffsetDateTime(it) })

        return query.singleResult as Int
    }

    @Transactional
    fun syncLovOipSuperType(pSyncId: Int, dto: LovOipSuperTypeAvroMessage): Int {
        val query = entityManager.createNativeQuery(
            "SELECT pkg_sync.sync_lov_oip_super_type(" +
                    ":pSyncId, " +
                    ":pId, " +
                    ":pName" +
                    ")"
        )

        query.setParameter("pSyncId", pSyncId)
        query.setParameter("pId", dto.id)
        query.setParameter("pName", dto.name)

        return query.singleResult as Int
    }

    @Transactional
    fun syncLovOipType(pSyncId: Int, dto: LovOipTypeAvroMessage): Int {
        val query = entityManager.createNativeQuery(
            "SELECT pkg_sync.sync_lov_oip_type(" +
                    ":pSyncId, " +
                    ":pId, " +
                    ":pIdOipSuperType, " +
                    ":pName" +
                    ")"
        )

        query.setParameter("pSyncId", pSyncId)
        query.setParameter("pId", dto.id)
        query.setParameter("pIdOipSuperType", dto.id_oip_super_type)
        query.setParameter("pName", dto.name)

        return query.singleResult as Int
    }

    @Transactional
    fun syncKlfOip(pSyncId: Int, dto: KlfOipAvroMessage): Int {
        val query = entityManager.createNativeQuery(
            "SELECT pkg_sync.sync_klf_oip(" +
                    ":pSyncId, " +
                    ":pId, " +
                    ":pGuid, " +
                    ":pIdOipSuperType, " +
                    ":pIdOipType, " +
                    ":pName, " +
                    ":pPartNum, " +
                    ":pPartCount, " +
                    ":pDuration, " +
                    ":pDescription, " +
                    ":pHasParent, " +
                    ":pHasChildren, " +
                    ":pChildrenCount, " +
                    ":pRootId, " +
                    ":pNativeName, " +
                    ":pReleaseYear, " +
                    ":pCreatedBy, " +
                    ":pCreatedAt, " +
                    ":pUpdatedBy, " +
                    ":pUpdatedAt" +
                    ")"
        )

        query.setParameter("pSyncId", pSyncId)
        query.setParameter("pId", dto.id)
        query.setParameter("pGuid", dto.guid)
        query.setParameter("pIdOipSuperType", dto.id_oip_super_type)
        query.setParameter("pIdOipType", dto.id_oip_type)
        query.setParameter("pName", dto.name)
        query.setParameter("pPartNum", dto.part_num)
        query.setParameter("pPartCount", dto.part_count)
        query.setParameter("pDuration", dto.duration?.let { microsToHms(it) })
        query.setParameter("pDescription", dto.description)
        query.setParameter("pHasParent", dto.has_parent)
        query.setParameter("pHasChildren", dto.has_children)
        query.setParameter("pChildrenCount", dto.children_count)
        query.setParameter("pRootId", dto.root_id)
        query.setParameter("pNativeName", dto.native_name)
        query.setParameter("pReleaseYear", dto.release_year)
        query.setParameter("pCreatedBy", dto.created_by)
        query.setParameter("pCreatedAt", dto.created_at?.let { microsToOffsetDateTime(it) })
        query.setParameter("pUpdatedBy", dto.updated_by)
        query.setParameter("pUpdatedAt", dto.updated_at?.let { microsToOffsetDateTime(it) })

        return query.singleResult as Int
    }

    @Transactional
    fun syncKlfRightType(pSyncId: Int, dto: KlfRightTypeAvroMessage): Int {
        val query = entityManager.createNativeQuery(
            "SELECT pkg_sync.sync_klf_right_type(" +
                    ":pSyncId, " +
                    ":pId, " +
                    ":pIdParent, " +
                    ":pName, " +
                    ":pDescription, " +
                    ":pCreatedBy, " +
                    ":pCreatedAt, " +
                    ":pUpdatedBy, " +
                    ":pUpdatedAt" +
                    ")"
        )

        query.setParameter("pSyncId", pSyncId)
        query.setParameter("pId", dto.id)
        query.setParameter("pIdParent", dto.id_parent)
        query.setParameter("pName", dto.name)
        query.setParameter("pDescription", dto.description)
        query.setParameter("pCreatedBy", dto.created_by)
        query.setParameter("pCreatedAt", dto.created_at?.let { microsToOffsetDateTime(it) })
        query.setParameter("pUpdatedBy", dto.updated_by)
        query.setParameter("pUpdatedAt", dto.updated_at?.let { microsToOffsetDateTime(it) })

        return query.singleResult as Int
    }

    @Transactional
    fun syncKlfFeatureCategory(pSyncId: Int, dto: KlfFeatureCategoryAvroMessage): Int {
        val query = entityManager.createNativeQuery(
            "SELECT pkg_sync.sync_klf_feature_category(" +
                    ":pSyncId, " +
                    ":pId, " +
                    ":pName, " +
                    ":pCreatedBy, " +
                    ":pCreatedAt, " +
                    ":pUpdatedBy, " +
                    ":pUpdatedAt" +
                    ")"
        )

        query.setParameter("pSyncId", pSyncId)
        query.setParameter("pId", dto.id)
        query.setParameter("pName", dto.name)
        query.setParameter("pCreatedBy", dto.created_by)
        query.setParameter("pCreatedAt", dto.created_at?.let { microsToOffsetDateTime(it) })
        query.setParameter("pUpdatedBy", dto.updated_by)
        query.setParameter("pUpdatedAt", dto.updated_at?.let { microsToOffsetDateTime(it) })

        return query.singleResult as Int
    }

    @Transactional
    fun syncKlfFeaturePlain(pSyncId: Int, dto: KlfFeaturePlainAvroMessage): Int {
        val query = entityManager.createNativeQuery(
            "SELECT pkg_sync.sync_klf_feature_plain(" +
                    ":pSyncId, " +
                    ":pId, " +
                    ":pName, " +
                    ":pIdFeatureCategory, " +
                    ":pCreatedBy, " +
                    ":pCreatedAt, " +
                    ":pUpdatedBy, " +
                    ":pUpdatedAt" +
                    ")"
        )

        query.setParameter("pSyncId", pSyncId)
        query.setParameter("pId", dto.id)
        query.setParameter("pName", dto.name)
        query.setParameter("pIdFeatureCategory", dto.id_feature_category)
        query.setParameter("pCreatedBy", dto.created_by)
        query.setParameter("pCreatedAt", dto.created_at?.let { microsToOffsetDateTime(it) })
        query.setParameter("pUpdatedBy", dto.updated_by)
        query.setParameter("pUpdatedAt", dto.updated_at?.let { microsToOffsetDateTime(it) })

        return query.singleResult as Int
    }

    @Transactional
    fun syncKlfFeatureTree(pSyncId: Int, dto: KlfFeatureTreeAvroMessage): Int {
        val query = entityManager.createNativeQuery(
            "SELECT pkg_sync.sync_klf_feature_tree(" +
                    ":pSyncId, " +
                    ":pId, " +
                    ":pIdParent, " +
                    ":pIdFeatureCategory, " +
                    ":pIdFeaturePlain, " +
                    ":pValidityPeriod, " +
                    ":pCreatedBy, " +
                    ":pCreatedAt, " +
                    ":pUpdatedBy, " +
                    ":pUpdatedAt" +
                    ")"
        )

        query.setParameter("pSyncId", pSyncId)
        query.setParameter("pId", dto.id)
        query.setParameter("pIdParent", dto.id_parent)
        query.setParameter("pIdFeatureCategory", dto.id_feature_category)
        query.setParameter("pIdFeaturePlain", dto.id_feature_plain)
        query.setParameter("pValidityPeriod", dto.validity_period)
        query.setParameter("pCreatedBy", dto.created_by)
        query.setParameter("pCreatedAt", dto.created_at?.let { microsToOffsetDateTime(it) })
        query.setParameter("pUpdatedBy", dto.updated_by)
        query.setParameter("pUpdatedAt", dto.updated_at?.let { microsToOffsetDateTime(it) })

        return query.singleResult as Int
    }

    @Transactional
    fun syncKlfFeatureCatToRt(pSyncId: Int, dto: KlfFeatureCatToRtAvroMessage): Int {
        val query = entityManager.createNativeQuery(
            "SELECT pkg_sync.sync_klf_feature_cat_to_rt(" +
                    ":pSyncId, " +
                    ":pId, " +
                    ":pIdRightType, " +
                    ":pIdFeatureCategory, " +
                    ":pIdDefFeature, " +
                    ":pCreatedBy, " +
                    ":pCreatedAt, " +
                    ":pUpdatedBy, " +
                    ":pUpdatedAt" +
                    ")"
        )

        query.setParameter("pSyncId", pSyncId)
        query.setParameter("pId", dto.id)
        query.setParameter("pIdRightType", dto.id_right_type)
        query.setParameter("pIdFeatureCategory", dto.id_feature_category)
        query.setParameter("pIdDefFeature", dto.id_def_feature)
        query.setParameter("pCreatedBy", dto.created_by)
        query.setParameter("pCreatedAt", dto.created_at?.let { microsToOffsetDateTime(it) })
        query.setParameter("pUpdatedBy", dto.updated_by)
        query.setParameter("pUpdatedAt", dto.updated_at?.let { microsToOffsetDateTime(it) })

        return query.singleResult as Int
    }

    @Transactional
    fun syncKlfOipHierarchy(pSyncId: Int, dto: KlfOipHierarchyAvroMessage): Int {
        val query = entityManager.createNativeQuery(
            "SELECT pkg_sync.sync_klf_oip_hierarchy(" +
                    ":pSyncId, " +
                    ":pId, " +
                    ":pIdParent, " +
                    ":pIdOip, " +
                    ":pCreatedBy, " +
                    ":pCreatedAt, " +
                    ":pUpdatedBy, " +
                    ":pUpdatedAt" +
                    ")"
        )

        query.setParameter("pSyncId", pSyncId)
        query.setParameter("pId", dto.id)
        query.setParameter("pIdParent", dto.id_parent)
        query.setParameter("pIdOip", dto.id_oip)
        query.setParameter("pCreatedBy", dto.created_by)
        query.setParameter("pCreatedAt", dto.created_at?.let { microsToOffsetDateTime(it) })
        query.setParameter("pUpdatedBy", dto.updated_by)
        query.setParameter("pUpdatedAt", dto.updated_at?.let { microsToOffsetDateTime(it) })

        return query.singleResult as Int
    }

    private fun microsToOffsetDateTime(micros: Long): OffsetDateTime {
        return OffsetDateTime.ofInstant(
            Instant.ofEpochSecond(micros / 1_000_000, (micros % 1_000_000) * 1000),
            ZoneOffset.UTC
        )
    }

    private fun microsToOffsetDateTime(micros: Instant): OffsetDateTime {
        return OffsetDateTime.ofInstant(micros, ZoneOffset.UTC)
    }

    private fun microsToHms(micros: Long): String {
        var us = micros
        val sign = if (us < 0) { us = -us; "-" } else ""
        val hours = us / 3_600_000_000
        us %= 3_600_000_000
        val minutes = us / 60_000_000
        us %= 60_000_000
        val seconds = us / 1_000_000
        return sign + String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }
}