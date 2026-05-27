package me.rightsflow.contracts.repository

import me.rightsflow.contracts.entity.LicenseRtFeatureSet
import me.rightsflow.contracts.entity.LicenseRtFeatures
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional

interface LicenseRtFeaturesRepository : JpaRepository<LicenseRtFeatures, Long> {
    fun findByIdFeatureSet(idFeatureSet: Long): List<LicenseRtFeatures>

    /**
     * Получить набор характеристик по ID с фильтрацией по организациям пользователя.
     * @param username JWT sub, или NULL для bypass (ADMIN/SERVICE)
     */
    @Query(
        value = """
            SELECT f.*
            FROM   license_rt_features f
            JOIN   license_rt_feature_set fs ON fs.id = f.id_feature_set
            JOIN   license_rights lr on lr.id = fs.id_lic_rights
            JOIN   license        l  ON l.id = lr.id_license
            JOIN   contract       c  ON c.id = l.id_contract
            LEFT JOIN user_org_access uoa
                   ON  :username IS NOT NULL
                  AND  uoa.username = :username
                  AND (uoa.id_org = c.id_org OR uoa.id_org = c.id_org_party)
            WHERE  f.id = :id
              AND  (:username IS NULL OR uoa.username IS NOT NULL)
        """,
        nativeQuery = true
    )
    fun findByIdForUser(
        @Param("id")       id:       Long,
        @Param("username") username: String?
    ): Optional<LicenseRtFeatures>

    /**
     * Поиск наборов характеристик по ID права лицензии с фильтрацией по организациям.
     * @param username JWT sub, или NULL для bypass (ADMIN/SERVICE)
     */
    @Query(
        value = """
            SELECT f.*
            FROM   license_rt_features f
            JOIN   license_rt_feature_set fs ON fs.id = f.id_feature_set
            JOIN   license_rights lr on lr.id = fs.id_lic_rights
            JOIN   license        l  ON l.id = lr.id_license
            JOIN   contract       c  ON c.id = l.id_contract
            LEFT JOIN user_org_access uoa
                   ON  :username IS NOT NULL
                  AND  uoa.username = :username
                  AND (uoa.id_org = c.id_org OR uoa.id_org = c.id_org_party)
            WHERE  f.id_feature_set = :idFeatureSet
              AND  (:username IS NULL OR uoa.username IS NOT NULL)
        """,
        countQuery = """
            SELECT COUNT(f.id)
            FROM   license_rt_features f
            JOIN   license_rt_feature_set fs ON fs.id = f.id_feature_set
            JOIN   license_rights lr on lr.id = fs.id_lic_rights
            JOIN   license        l  ON l.id = lr.id_license
            JOIN   contract       c  ON c.id = l.id_contract
            LEFT JOIN user_org_access uoa
                   ON  :username IS NOT NULL
                  AND  uoa.username = :username
                  AND (uoa.id_org = c.id_org OR uoa.id_org = c.id_org_party)
            WHERE  f.id_feature_set = :idFeatureSet
              AND  (:username IS NULL OR uoa.username IS NOT NULL)
        """,
        nativeQuery = true
    )
    fun findByIdFeatureSetForUser(
        @Param("idFeatureSet") idFeatureSet: Long,
        @Param("username")     username:     String?
    ): List<LicenseRtFeatures>
}