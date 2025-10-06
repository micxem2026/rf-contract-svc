package me.rightsflow.contracts.entity

import jakarta.persistence.*
import me.rightsflow.common.entity.BaseAudit
import org.hibernate.Hibernate

@Entity
@Table(
    name = "LICENSE_OIP",
    uniqueConstraints = [UniqueConstraint(columnNames = ["ID_LICENSE", "ID_OIP"])]
)
class LicenseOip(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", nullable = false)
    var id: Long? = null,

    @Column(name = "ID_LICENSE", nullable = false)
    var idLicense: Long,

    @Column(name = "ID_OIP", nullable = false)
    var idOip: Int,

    @Column(name = "ID_ROOT_OIP", nullable = false)
    var idRootOip: Int

) : BaseAudit() {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_LICENSE", referencedColumnName = "ID", insertable = false, updatable = false)
    var license: License? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_OIP", referencedColumnName = "ID", insertable = false, updatable = false)
    var oip: Oip? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_ROOT_OIP", referencedColumnName = "ID", insertable = false, updatable = false)
    var rootOip: Oip? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false

        if (Hibernate.getClass(this) != Hibernate.getClass(other)) return false

        other as LicenseOip

        return id != null && id == other.id
    }

    override fun hashCode(): Int = javaClass.hashCode()
}