package me.rightsflow.contracts.repository

import me.rightsflow.contracts.entity.RightType
import org.springframework.data.jpa.repository.JpaRepository

interface RightTypeRepository : JpaRepository<RightType, Int>