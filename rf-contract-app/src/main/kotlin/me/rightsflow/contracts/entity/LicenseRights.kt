package me.rightsflow.contracts.entity

import jakarta.persistence.*
import me.rightsflow.common.entity.BaseAudit
import org.hibernate.Hibernate
import java.time.LocalDate

@Entity
@Table(
    name = "LICENSE_RIGHTS"
)
class LicenseRights(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", nullable = false)
    var id: Long? = null,

    @Column(name = "ID_LICENSE", nullable = false)
    var idLicense: Long,

    @Column(name = "HB_START_DATE")
    var hbStartDate: LocalDate? = null,

    @Column(name = "HB_DAYS")
    var hbDays: Int? = null

) : BaseAudit() {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_LICENSE", referencedColumnName = "ID", insertable = false, updatable = false)
    var license: License? = null

    @OneToMany(
        mappedBy = "licRights", // Указывает на поле в классе LicenseRightsRt
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
        fetch = FetchType.LAZY
    )
    var rights: MutableSet<LicenseRightsRt> = mutableSetOf()

    fun addRight(right: LicenseRightsRt) {
        rights.add(right)
        right.licRights = this // Устанавливаем обратную ссылку
    }

    fun removeRight(right: LicenseRightsRt) {
        rights.remove(right)
        right.licRights = null // Убираем обратную ссылку
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false

        if (Hibernate.getClass(this) != Hibernate.getClass(other)) return false

        other as LicenseRights

        return id != null && id == other.id
    }

    override fun hashCode(): Int = javaClass.hashCode()
}