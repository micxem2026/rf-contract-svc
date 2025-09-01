package me.rightsflow.contracts.entity

import io.hypersistence.utils.hibernate.type.interval.PostgreSQLIntervalType
import jakarta.persistence.*
import me.rightsflow.common.entity.BaseAudit
import org.hibernate.Hibernate
import org.hibernate.annotations.Type
import java.time.Duration

@Entity
@Table(
    name = "SYNC__KLF_OIP",
    uniqueConstraints = [UniqueConstraint(columnNames = ["GUID"])]
)
class Oip(

    @Id
    @Column(name = "ID", nullable = false)
    var id: Int? = null,

    @Column(name = "GUID", length = 255, unique = true)
    var guid: String? = null,

    @Column(name = "ID_OIP_SUPER_TYPE", nullable = false)
    var idOipSuperType: Int,

    @Column(name = "ID_OIP_TYPE", nullable = false)
    var idOipType: Int,

    @Column(name = "NAME", nullable = false, length = 512)
    var name: String,

    @Column(name = "PART_NUM", nullable = false)
    var partNum: Int = 0,

    @Column(name = "PART_COUNT", nullable = false)
    var partCount: Int = 0,

    @Type(PostgreSQLIntervalType::class)
    @Column(name = "DURATION", columnDefinition = "INTERVAL")
    var duration: Duration? = null

) : BaseAudit() {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_OIP_SUPER_TYPE", referencedColumnName = "ID", insertable = false, updatable = false)
    var oipSuperType: OipSuperType? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_OIP_TYPE", referencedColumnName = "ID", insertable = false, updatable = false)
    var oipType: OipType? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false

        if (Hibernate.getClass(this) != Hibernate.getClass(other)) return false

        other as Oip

        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}