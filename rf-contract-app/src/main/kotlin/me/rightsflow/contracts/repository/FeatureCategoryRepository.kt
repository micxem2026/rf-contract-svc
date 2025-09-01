package me.rightsflow.contracts.repository

import me.rightsflow.contracts.entity.FeatureCategory
import org.springframework.data.jpa.repository.JpaRepository

interface FeatureCategoryRepository : JpaRepository<FeatureCategory, Int>