package me.rightsflow.contracts.repository

import me.rightsflow.contracts.entity.UserOrgAccess
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UserOrgAccessRepository : JpaRepository<UserOrgAccess, Long> {

    /**
     * Все привязки для заданного пользователя.
     */
    fun findByUsername(username: String): List<UserOrgAccess>


    @Query("select * from pkg_contract.get_org_id(:idOrg)", nativeQuery = true)
    fun getIdOrg(@Param("idOrg") idOrg: String): Int

    /**
     * Все привязки для заданной организации (с пагинацией).
     */
    fun findByIdOrg(idOrg: Int, pageable: Pageable): Page<UserOrgAccess>

    /**
     * Проверка существования конкретной привязки.
     */
    fun existsByUsernameAndIdOrg(username: String, idOrg: Int): Boolean

    /**
     * Удаление конкретной привязки пользователь + организация.
     */
    @Modifying
    @Query("DELETE FROM UserOrgAccess u WHERE u.username = :username AND u.idOrg = :idOrg")
    fun deleteByUsernameAndIdOrg(
        @Param("username") username: String,
        @Param("idOrg") idOrg: Int
    )

    /**
     * Удаление всех привязок пользователя.
     */
    @Modifying
    @Query("DELETE FROM UserOrgAccess u WHERE u.username = :username")
    fun deleteAllByUsername(@Param("username") username: String)

    /**
     * Список username пользователей, привязанных к организации.
     * Используется для отображения в UI администрирования.
     */
    @Query("SELECT DISTINCT u.username FROM UserOrgAccess u WHERE u.idOrg = :idOrg")
    fun findUsernamesByIdOrg(@Param("idOrg") idOrg: Int): List<String>

    /**
     * Список ID организаций для пользователя.
     */
    @Query("SELECT u.idOrg FROM UserOrgAccess u WHERE u.username = :username")
    fun findOrgIdsByUsername(@Param("username") username: String): List<Int>
}
