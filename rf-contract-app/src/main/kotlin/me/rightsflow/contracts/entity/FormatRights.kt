package me.rightsflow.contracts.entity

import jakarta.persistence.*
import me.rightsflow.common.entity.BaseAudit
import org.hibernate.Hibernate

@Entity
@Table(
    name = "FORMAT_RIGHTS"
)
class FormatRights(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", nullable = false)
    var id: Long? = null,

    @Column(name = "ID_LIC_FORMAT", nullable = false)
    var idLicFormat: Long,

) : BaseAudit() {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_LIC_FORMAT", referencedColumnName = "ID", insertable = false, updatable = false)
    var licenseFormat: LicenseFormat? = null

    @OneToMany(
        mappedBy = "fmtRights", // Указывает на поле в классе FormatRightsRt
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
        fetch = FetchType.LAZY
    )
    var rights: MutableSet<FormatRightsRt> = mutableSetOf()

    fun addRight(right: FormatRightsRt) {
        rights.add(right)
        right.fmtRights = this // Устанавливаем обратную ссылку
    }

    fun removeRight(right: FormatRightsRt) {
        rights.remove(right)
        right.fmtRights = null // Убираем обратную ссылку
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false

        if (Hibernate.getClass(this) != Hibernate.getClass(other)) return false

        other as FormatRights

        return id != null && id == other.id
    }

    override fun hashCode(): Int = javaClass.hashCode()
}