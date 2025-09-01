package me.rightsflow.contracts.repository

import me.rightsflow.contracts.entity.FormatRtFeatureSet
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface FormatRtFeatureSetRepository : JpaRepository<FormatRtFeatureSet, Long> {

    fun findByIdFmtRt(idFmtRt: Long, pageable: Pageable): Page<FormatRtFeatureSet>
}