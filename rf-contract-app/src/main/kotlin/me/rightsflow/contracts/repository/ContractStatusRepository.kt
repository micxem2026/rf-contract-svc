package me.rightsflow.contracts.repository

import me.rightsflow.contracts.entity.ContractStatus
import org.springframework.data.jpa.repository.JpaRepository

interface ContractStatusRepository : JpaRepository<ContractStatus, Int>
