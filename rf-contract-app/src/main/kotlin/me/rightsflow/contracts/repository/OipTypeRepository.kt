package me.rightsflow.contracts.repository

import me.rightsflow.contracts.entity.OipType
import org.springframework.data.jpa.repository.JpaRepository

interface OipTypeRepository : JpaRepository<OipType, Int>