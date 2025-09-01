package me.rightsflow.contracts.repository

import me.rightsflow.contracts.entity.ContractType
import org.springframework.data.jpa.repository.JpaRepository

interface ContractTypeRepository : JpaRepository<ContractType, Int>
