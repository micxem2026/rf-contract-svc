package me.rightsflow.contracts.entity

import jakarta.persistence.*
import me.rightsflow.common.entity.BaseAudit
import org.hibernate.Hibernate
import java.time.LocalDate

@Entity
@Table(
    name = "LICENSE_RT",
    uniqueConstraints = [UniqueConstraint(columnNames = ["ID_LICENSE", "ID_RIGHT_TYPE"])]
)
class LicenseRt(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", nullable = false)
    var id: Long? = null,

    @Column(name = "ID_LICENSE", nullable = false)
    var idLicense: Long,

    @Column(name = "ID_RIGHT_TYPE", nullable = false)
    var idRightType: Int,

    @Column(name = "HB_START_DATE")
    var hbStartDate: LocalDate? = null,

    @Column(name = "HB_DAYS")
    var hbDays: Int? = null

) : BaseAudit() {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_LICENSE", referencedColumnName = "ID", insertable = false, updatable = false)
    var license: License? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_RIGHT_TYPE", referencedColumnName = "ID", insertable = false, updatable = false)
    var rightType: RightType? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false

        if (Hibernate.getClass(this) != Hibernate.getClass(other)) return false

        other as LicenseRt

        return id != null && id == other.id
    }

    override fun hashCode(): Int = javaClass.hashCode()
}