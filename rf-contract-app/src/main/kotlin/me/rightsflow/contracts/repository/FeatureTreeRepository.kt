package me.rightsflow.contracts.repository

import me.rightsflow.contracts.entity.FeatureTree
import org.springframework.data.jpa.repository.JpaRepository

interface FeatureTreeRepository : JpaRepository<FeatureTree, Int>