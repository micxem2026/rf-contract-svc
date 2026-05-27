package me.rightsflow.contracts.repository

import me.rightsflow.contracts.entity.LicenseRights
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional

interface LicenseRightsRepository : JpaRepository<LicenseRights, Long> {

    fun findByLicenseId(licenseId: Long, pageable: Pageable): Page<LicenseRights>

    /**
     * Получить право лицензии по ID с фильтрацией по организациям пользователя.
     * @param username JWT sub, или NULL для bypass (ADMIN/SERVICE)
     */
    @Query(
        value = """
            SELECT lr.*
            FROM   license_rights lr
            JOIN   license  l ON l.id  = lr.id_license
            JOIN   contract c ON c.id  = l.id_contract
            LEFT JOIN user_org_access uoa
                   ON  :username IS NOT NULL
                  AND  uoa.username = :username
                  AND (uoa.id_org = c.id_org OR uoa.id_org = c.id_org_party)
            WHERE  lr.id = :id
              AND  (:username IS NULL OR uoa.username IS NOT NULL)
        """,
        nativeQuery = true
    )
    fun findByIdForUser(
        @Param("id")       id:       Long,
        @Param("username") username: String?
    ): Optional<LicenseRights>

    /**
     * Поиск прав лицензии по ID лицензии с фильтрацией по организациям.
     * @param username JWT sub, или NULL для bypass (ADMIN/SERVICE)
     */
    @Query(
        value = """
            SELECT lr.*
            FROM   license_rights lr
            JOIN   license  l ON l.id  = lr.id_license
            JOIN   contract c ON c.id  = l.id_contract
            LEFT JOIN user_org_access uoa
                   ON  :username IS NOT NULL
                  AND  uoa.username = :username
                  AND (uoa.id_org = c.id_org OR uoa.id_org = c.id_org_party)
            WHERE  lr.id_license = :idLicense
              AND  (:username IS NULL OR uoa.username IS NOT NULL)
        """,
        countQuery = """
            SELECT COUNT(lr.id)
            FROM   license_rights lr
            JOIN   license  l ON l.id  = lr.id_license
            JOIN   contract c ON c.id  = l.id_contract
            LEFT JOIN user_org_access uoa
                   ON  :username IS NOT NULL
                  AND  uoa.username = :username
                  AND (uoa.id_org = c.id_org OR uoa.id_org = c.id_org_party)
            WHERE  lr.id_license = :idLicense
              AND  (:username IS NULL OR uoa.username IS NOT NULL)
        """,
        nativeQuery = true
    )
    fun findByLicenseIdForUser(
        @Param("idLicense") idLicense: Long,
        @Param("username")  username:  String?,
        pageable: Pageable
    ): Page<LicenseRights>
}