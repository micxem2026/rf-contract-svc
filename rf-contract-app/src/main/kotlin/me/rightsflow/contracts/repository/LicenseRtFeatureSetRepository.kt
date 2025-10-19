package me.rightsflow.contracts.repository

import me.rightsflow.contracts.entity.LicenseRtFeatureSet
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface LicenseRtFeatureSetRepository : JpaRepository<LicenseRtFeatureSet, Long> {
    fun findByIdLicRights(idLicRights: Long, pageable: Pageable): Page<LicenseRtFeatureSet>
}