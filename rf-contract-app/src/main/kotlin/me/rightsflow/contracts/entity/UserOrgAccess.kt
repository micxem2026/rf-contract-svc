package me.rightsflow.contracts.entity

import jakarta.persistence.*
import java.time.OffsetDateTime

/**
 * Привязка пользователя к организации для разграничения доступа к контрактам.
 *
 * Пользователь может видеть/изменять только те контракты, у которых
 * id_org ИЛИ id_org_party входит в его список организаций.
 *
 * Ролям ADMIN и SERVICE привязки не нужны — они имеют доступ ко всем контрактам
 * (bypass реализован в pkg_contract.get_user_org_ids).
 */
@Entity
@Table(
    name = "user_org_access",
    uniqueConstraints = [UniqueConstraint(
        name = "unq_user_org_access",
        columnNames = ["username", "id_org"]
    )]
)
class UserOrgAccess(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    /**
     * Username пользователя — соответствует JWT claim "sub" (используется в PL/pgSQL).
     */
    @Column(name = "username", nullable = false, length = 50)
    var username: String,

    /**
     * ID организации из таблицы sync__klf_organization.
     */
    @Column(name = "id_org", nullable = false)
    var idOrg: Int,

    /**
     * Username администратора, создавшего привязку.
     */
    @Column(name = "created_by", nullable = false, length = 50)
    var createdBy: String,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now()
)
