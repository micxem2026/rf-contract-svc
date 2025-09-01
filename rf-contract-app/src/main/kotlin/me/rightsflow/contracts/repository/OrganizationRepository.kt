package me.rightsflow.contracts.repository

import me.rightsflow.contracts.entity.Organization
import org.springframework.data.jpa.repository.JpaRepository

interface OrganizationRepository : JpaRepository<Organization, Int>
