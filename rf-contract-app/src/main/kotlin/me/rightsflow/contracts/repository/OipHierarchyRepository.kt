package me.rightsflow.contracts.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import me.rightsflow.contracts.entity.OipHierarchy

interface OipHierarchyRepository : JpaRepository<OipHierarchy, Int> {

}