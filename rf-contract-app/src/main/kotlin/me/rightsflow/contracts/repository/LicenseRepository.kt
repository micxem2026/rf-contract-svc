package me.rightsflow.contracts.repository

import me.rightsflow.contracts.entity.License
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional

interface LicenseRepository : JpaRepository<License, Long> {

    @Query(
        """
        select l
        from License l
        where (l.idContract = :idContract) and
              (lower(l.num) like lower(concat('%', :numFilter, '%')) or :numFilter is null)
        """
    )
    fun findByFilter(
        @Param("idContract") idContract: Long,
        @Param("numFilter") numFilter: String?,
        pageable: Pageable
    ): Page<License>

    /**
     * Получить лицензию по ID с фильтрацией по организациям пользователя.
     * @param username JWT sub, или NULL для bypass (ADMIN/SERVICE)
     */
    @Query(
        value = """
            SELECT l.*
            FROM   license l
            JOIN   contract c ON c.id = l.id_contract
            LEFT JOIN user_org_access uoa
                   ON  :username IS NOT NULL
                  AND  uoa.username = :username
                  AND (uoa.id_org = c.id_org OR uoa.id_org = c.id_org_party)
            WHERE  l.id = :id
              AND  (:username IS NULL OR uoa.username IS NOT NULL)
        """,
        nativeQuery = true
    )
    fun findByIdForUser(
        @Param("id")       id:       Long,
        @Param("username") username: String?
    ): Optional<License>

    /**
     * Поиск лицензий по контракту с фильтрацией по организациям пользователя.
     * @param username JWT sub, или NULL для bypass (ADMIN/SERVICE)
     */
    @Query(
        value = """
            SELECT l.*
            FROM   license l
            JOIN   contract c ON c.id = l.id_contract
            LEFT JOIN user_org_access uoa
                   ON  :username IS NOT NULL
                  AND  uoa.username = :username
                  AND (uoa.id_org = c.id_org OR uoa.id_org = c.id_org_party)
            WHERE  l.id_contract = :idContract
              AND  (:numFilter IS NULL OR lower(l.num) LIKE lower(concat('%', :numFilter, '%')))
              AND  (:username IS NULL OR uoa.username IS NOT NULL)
        """,
        countQuery = """
            SELECT COUNT(l.id)
            FROM   license l
            JOIN   contract c ON c.id = l.id_contract
            LEFT JOIN user_org_access uoa
                   ON  :username IS NOT NULL
                  AND  uoa.username = :username
                  AND (uoa.id_org = c.id_org OR uoa.id_org = c.id_org_party)
            WHERE  l.id_contract = :idContract
              AND  (:numFilter IS NULL OR lower(l.num) LIKE lower(concat('%', :numFilter, '%')))
              AND  (:username IS NULL OR uoa.username IS NOT NULL)
        """,
        nativeQuery = true
    )
    fun findByFilterForUser(
        @Param("idContract") idContract: Long,
        @Param("numFilter")  numFilter:  String?,
        @Param("username")   username:   String?,
        pageable: Pageable
    ): Page<License>
}