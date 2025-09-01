package me.rightsflow.contracts.repository

import me.rightsflow.contracts.entity.FeaturePlain
import org.springframework.data.jpa.repository.JpaRepository

interface FeaturePlainRepository : JpaRepository<FeaturePlain, Int>