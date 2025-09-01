package me.rightsflow.contracts.entity

import jakarta.persistence.*
import me.rightsflow.common.entity.BaseAudit
import org.hibernate.Hibernate

@Entity
@Table(name = "LICENSE_FORMAT")
class LicenseFormat(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", nullable = false)
    var id: Long? = null,

    @Column(name = "NAME", nullable = false, length = 255)
    var name: String,

    @Column(name = "DESCRIPTION", length = 511)
    var description: String? = null

) : BaseAudit() {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false

        if (Hibernate.getClass(this) != Hibernate.getClass(other)) return false

        other as LicenseFormat

        return id != null && id == other.id
    }

    override fun hashCode(): Int = javaClass.hashCode()
}