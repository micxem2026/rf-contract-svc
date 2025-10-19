package me.rightsflow.contracts.entity

import jakarta.persistence.*
import me.rightsflow.common.entity.BaseAudit
import org.hibernate.Hibernate

@Entity
@Table(
    name = "FORMAT_RIGHTS_RT",
    uniqueConstraints = [UniqueConstraint(columnNames = ["ID_FMT_RIGHTS", "ID_RIGHT_TYPE"])]
)
class FormatRightsRt(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", nullable = false)
    var id: Long? = null,

    @Column(name = "ID_FMT_RIGHTS", nullable = false)
    var idFmtRights: Long,

    @Column(name = "ID_RIGHT_TYPE", nullable = false)
    var idRightType: Int

) : BaseAudit() {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_FMT_RIGHTS", referencedColumnName = "ID", insertable = false, updatable = false)
    var fmtRights: FormatRights? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_RIGHT_TYPE", referencedColumnName = "ID", insertable = false, updatable = false)
    var rightType: RightType? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false

        if (Hibernate.getClass(this) != Hibernate.getClass(other)) return false

        other as FormatRightsRt

        return id != null && id == other.id
    }

    override fun hashCode(): Int = javaClass.hashCode()
}