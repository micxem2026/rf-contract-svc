package me.rightsflow.contracts.repository

import me.rightsflow.contracts.entity.ContractCounterparty
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.*

interface ContractCounterpartyRepository : JpaRepository<ContractCounterparty, Long> {

    fun findByIdContract(IdContract: Long): List<ContractCounterparty>
    fun findByIdContractIn(idContracts: Collection<Long>): List<ContractCounterparty>

    /**
     * Получить контрагентов контракта по ID с фильтрацией по организациям пользователя.
     * @param username JWT sub, или NULL для bypass (ADMIN/SERVICE)
     */
    @Query(
        value = """
            SELECT cc.*
            FROM   contract_counterparty cc
            JOIN   contract c ON c.id = cc.id_contract
            LEFT JOIN user_org_access uoa
                   ON (:username IS NOT NULL)
                  AND (uoa.username = :username)
                  AND (uoa.id_org = c.id_org OR uoa.id_org = c.id_org_party)
            WHERE  (cc.id_contract = :id)
              AND  (:username IS NULL OR uoa.username IS NOT NULL)
        """,
        nativeQuery = true
    )
    fun findByIdForUser(
        @Param("id")       id:       Long,
        @Param("username") username: String?
    ): Optional<ContractCounterparty>

    /**
     * Поиск лицензий по контракту с фильтрацией по организациям пользователя.
     * @param username JWT sub, или NULL для bypass (ADMIN/SERVICE)
     */
    @Query(
        value = """
            SELECT cc.*
            FROM  contract_counterparty cc
            JOIN  contract c ON c.id = cc.id_contract
            LEFT JOIN user_org_access uoa
                   ON (:username IS NOT NULL)
                  AND (uoa.username = :username)
                  AND (uoa.id_org = c.id_org OR uoa.id_org = c.id_org_party)
            WHERE  (cc.id_contract = :idContract)
              AND  (:username IS NULL OR uoa.username IS NOT NULL)
        """,
        countQuery = """
            SELECT COUNT(cc.id)
            FROM   contract_counterparty cc
            JOIN   contract c ON c.id = cc.id_contract
            LEFT JOIN user_org_access uoa
                   ON (:username IS NOT NULL)
                  AND (uoa.username = :username)
                  AND (uoa.id_org = c.id_org OR uoa.id_org = c.id_org_party)
            WHERE  (cc.id_contract = :idContract)
              AND  (:username IS NULL OR uoa.username IS NOT NULL)
        """,
        nativeQuery = true
    )
    fun findByIdContractForUser(
        @Param("idContract") idContract: Long,
        @Param("username")   username:   String?
    ): List<ContractCounterparty>
}
