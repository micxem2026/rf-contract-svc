package me.rightsflow.contracts.entity

import io.hypersistence.utils.hibernate.type.range.PostgreSQLRangeType
import io.hypersistence.utils.hibernate.type.range.Range
import jakarta.persistence.*
import me.rightsflow.common.entity.BaseAudit
import org.hibernate.Hibernate
import org.hibernate.annotations.Type
import java.time.LocalDate

@Entity
@Table(name = "FORMAT_RT_FEATURE_SET")
class FormatRtFeatureSet(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", nullable = false)
    var id: Long? = null,

    @Column(name = "ID_FMT_RIGHTS", nullable = false)
    var idFmtRights: Long,

    @Column(name = "IS_EXCLUSIVE", nullable = false)
    var isExclusive: Boolean = false,

    @Column(name = "IS_USE_RIGHT", nullable = false)
    var isUseRight: Boolean = false,

    @Type(PostgreSQLRangeType::class)
    @Column(name = "VALIDITY_PERIOD", nullable = false, columnDefinition = "daterange")
    var validityPeriod: Range<LocalDate> = Range.emptyRange(LocalDate::class.java)

) : BaseAudit() {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_FMT_RIGHTS", referencedColumnName = "ID", insertable = false, updatable = false)
    var fmtRights: FormatRights? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false

        if (Hibernate.getClass(this) != Hibernate.getClass(other)) return false

        other as FormatRtFeatureSet

        return id != null && id == other.id
    }

    override fun hashCode(): Int = javaClass.hashCode()
}