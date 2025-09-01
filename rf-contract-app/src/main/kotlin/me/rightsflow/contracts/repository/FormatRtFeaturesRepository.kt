package me.rightsflow.contracts.repository

import me.rightsflow.contracts.entity.FormatRtFeatures
import org.springframework.data.jpa.repository.JpaRepository

interface FormatRtFeaturesRepository : JpaRepository<FormatRtFeatures, Long> {
    fun findByIdFeatureSet(idFeatureSet: Long): List<FormatRtFeatures>
}