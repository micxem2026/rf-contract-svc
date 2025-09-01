package me.rightsflow.contracts.repository

import me.rightsflow.contracts.entity.LicenseRtFeatures
import org.springframework.data.jpa.repository.JpaRepository

interface LicenseRtFeaturesRepository : JpaRepository<LicenseRtFeatures, Long> {
    fun findByIdFeatureSet(idFeatureSet: Long): List<LicenseRtFeatures>
}