package me.rightsflow.contracts.repository

import me.rightsflow.contracts.entity.OipSuperType
import org.springframework.data.jpa.repository.JpaRepository

interface OipSuperTypeRepository : JpaRepository<OipSuperType, Int>