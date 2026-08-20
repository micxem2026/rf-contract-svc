package me.rightsflow.contracts.repository

import me.rightsflow.contracts.dto.response.LicensePartsInfo
import me.rightsflow.contracts.dto.response.LicenseProjection
import me.rightsflow.contracts.entity.License
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional

interface LicenseRepository : JpaRepository<License, Long> {
/*
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
*/
    /**
     * Получить лицензию по ID с фильтрацией по организациям пользователя.
     * Включает вычисляемые поля: partRanges, numParts.
     * @param username JWT sub, или NULL для bypass (ADMIN/SERVICE)
     */
    @Query(
        value = """
            SELECT
                l.id,
                l.id_contract                                AS idContract,
                c.num                                        AS contractNum,
                l.id_lic_format                              AS idLicFormat,
                lf.name                                      AS licFormatName,
                l.guid,
                l.num,
                l.name,
                l.price,
                l.vat_rate                                   AS vatRate,
                l.vat_amount                                 AS vatAmount,
                l.total_amount                               AS totalAmount,
                lower(l.validity_period)                     AS validityPeriodStart,
                upper(l.validity_period) - interval '1 day'  AS validityPeriodEnd,
                l.description,
                l.created_by                                 AS createdBy,
                l.created_at                                 AS createdAt,
                l.updated_by                                 AS updatedBy,
                l.updated_at                                 AS updatedAt,
                pkg_acl.compress_part_num_ranges(l.id)       AS partRanges,
                (SELECT COUNT(*) FROM license_oip lo WHERE lo.id_license = l.id) AS numParts,
                case when c.warning is null then coalesce(m.missing_flag, 0) else -1 end as missingFlag,
                m.missing_right_info                         AS missingRightInfo                 
            FROM  license l
            JOIN  contract c ON c.id = l.id_contract
            LEFT JOIN license_format lf ON lf.id = l.id_lic_format
            LEFT JOIN LATERAL (
                    SELECT
                        MAX(mr.missing_flag) AS missing_flag,
                        string_agg(mr.missing_right_info, E'\n---\n') AS missing_right_info
                    FROM missing_right mr
                    WHERE mr.id_license = l.id      
            ) m on true             
            WHERE  l.id = :id
              AND (
                    NULLIF(:username, '') IS NULL
                    OR EXISTS (
                        SELECT 1
                        FROM user_org_access u
                        WHERE u.username = :username
                          AND u.id_org = c.id_org
                    )
                    OR EXISTS (
                        SELECT 1
                        FROM user_org_access u
                        WHERE u.username = :username
                          AND u.id_org = c.id_org_party
                    )
              )
        """,
        nativeQuery = true
    )
    fun findByIdForUser(
        @Param("id")       id:       Long,
        @Param("username") username: String?
    ): Optional<LicenseProjection>

    /**
     * Поиск лицензий по контракту с фильтрацией по организациям пользователя.
     * @param username JWT sub, или NULL для bypass (ADMIN/SERVICE)
     */
    @Query(
        value = """
        SELECT
            l.id,
            l.id_contract                               AS idContract,
            c.num                                        AS contractNum,
            l.id_lic_format                              AS idLicFormat,
            lf.name                                      AS licFormatName,
            l.guid,
            l.num,
            l.name,
            l.price,
            l.vat_rate                                   AS vatRate,
            l.vat_amount                                 AS vatAmount,
            l.total_amount                               AS totalAmount,
            lower(l.validity_period)                     AS validityPeriodStart,
            upper(l.validity_period) - interval '1 day'  AS validityPeriodEnd,
            l.description,
            l.created_by                                 AS createdBy,
            l.created_at                                 AS createdAt,
            l.updated_by                                 AS updatedBy,
            l.updated_at                                 AS updatedAt,
            pkg_acl.compress_part_num_ranges(l.id)       AS partRanges,
            (SELECT COUNT(*) FROM license_oip lo WHERE lo.id_license = l.id) AS numParts,
            case when c.warning is null then coalesce(m.missing_flag, 0) else -1 end as missingFlag,
            m.missing_right_info                         AS missingRightInfo            
        FROM  license l
        JOIN  contract c ON c.id = l.id_contract
        LEFT JOIN license_format lf ON lf.id = l.id_lic_format
        LEFT JOIN LATERAL (
                SELECT
                    MAX(mr.missing_flag) AS missing_flag,
                    string_agg(mr.missing_right_info, E'\n---\n') AS missing_right_info
                FROM missing_right mr
                WHERE mr.id_license = l.id      
        ) m on true
        WHERE l.id_contract = :idContract
          AND (NULLIF(:numFilter, '') IS NULL OR l.num ILIKE concat('%', :numFilter, '%'))
          AND (
                NULLIF(:username, '') IS NULL
                OR EXISTS (
                    SELECT 1
                    FROM user_org_access u
                    WHERE u.username = :username
                      AND u.id_org = c.id_org
                )
                OR EXISTS (
                    SELECT 1
                    FROM user_org_access u
                    WHERE u.username = :username
                      AND u.id_org = c.id_org_party
                )
              )
    """,
        countQuery = """
        SELECT COUNT(l.id)
        FROM   license l
        JOIN   contract c ON c.id = l.id_contract
        WHERE  l.id_contract = :idContract
          AND  (NULLIF(:numFilter, '') IS NULL OR l.num ILIKE concat('%', :numFilter, '%'))
          AND (
                NULLIF(:username, '') IS NULL
                OR EXISTS (
                    SELECT 1
                    FROM user_org_access u
                    WHERE u.username = :username
                      AND u.id_org = c.id_org
                )
                OR EXISTS (
                    SELECT 1
                    FROM user_org_access u
                    WHERE u.username = :username
                      AND u.id_org = c.id_org_party
                )
              )
    """,
        nativeQuery = true
    )
    fun findByFilterForUser(
        @Param("idContract") idContract: Long,
        @Param("numFilter")  numFilter:  String?,
        @Param("username")   username:   String?,
        pageable: Pageable
    ): Page<LicenseProjection>

    /**
     * Лёгкий запрос только вычисляемых полей — используется в create/update,
     * где лицензия уже получена как entity через стандартный findById().
     */
    @Query(
        value = """
            SELECT
                pkg_acl.compress_part_num_ranges(l.id) AS partRanges,
                (SELECT COUNT(*) FROM license_oip lo WHERE lo.id_license = l.id) AS numParts,
                coalesce(m.missing_flag, 0)                  AS missingFlag, 
                m.missing_right_info                         AS missingRightInfo                
            FROM license l
            LEFT JOIN LATERAL (
                    SELECT
                        MAX(mr.missing_flag) AS missing_flag,
                        string_agg(mr.missing_right_info, E'\n---\n') AS missing_right_info
                    FROM missing_right mr
                    WHERE mr.id_license = l.id      
            ) m on true            
            WHERE l.id = :id
        """,
        nativeQuery = true
    )
    fun findPartsInfoById(@Param("id") id: Long): LicensePartsInfo
}