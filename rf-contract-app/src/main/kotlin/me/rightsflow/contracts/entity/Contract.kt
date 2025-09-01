package me.rightsflow.contracts.entity

import com.fasterxml.jackson.annotation.JsonValue
import io.hypersistence.utils.hibernate.type.range.PostgreSQLRangeType
import io.hypersistence.utils.hibernate.type.range.Range
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.persistence.*
import me.rightsflow.common.entity.BaseAudit
import org.hibernate.Hibernate
import org.hibernate.annotations.Type
import java.time.LocalDate


@Entity
@Table(
    name = "CONTRACT",
    uniqueConstraints = [UniqueConstraint(columnNames = ["GUID"])]
)
class Contract(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    var id: Long? = null,

    @Column(name = "GUID", length = 255, unique = true)
    var guid: String? = null,

    @Column(name = "NUM", nullable = false, length = 255)
    var num: String,

    @Column(name = "ID_ORG", nullable = false)
    var idOrg: Int,

    @Type(PostgreSQLRangeType::class)
    @Column(name = "VALIDITY_PERIOD", nullable = false, columnDefinition = "daterange")
    var validityPeriod: Range<LocalDate> = Range.emptyRange(LocalDate::class.java),

    @Column(name = "SIGN_DATE")
    var signDate: LocalDate? = null,

    @Column(name = "ID_CONTRACT_TYPE", nullable = false)
    var idContractType: Int,

    @Column(name = "ID_CONTRACT_STATUS", nullable = false)
    var idContractStatus: Int,

    @Enumerated(EnumType.STRING)
    @Column(name = "IN_OUT", nullable = false, length = 1)
    var inOut: ContractKind,

    @Column(name = "DESCRIPTION", length = 511)
    var description: String? = null

) : BaseAudit() {

    @Schema(enumAsRef = true)
    enum class ContractKind {
        S, P;

        @JsonValue
        override fun toString(): String {
            return this.name
        }
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_ORG", referencedColumnName = "ID", insertable = false, updatable = false)
    var organization: Organization? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_CONTRACT_TYPE", referencedColumnName = "ID", insertable = false, updatable = false)
    var contractType: ContractType? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_CONTRACT_STATUS", referencedColumnName = "ID", insertable = false, updatable = false)
    var contractStatus: ContractStatus? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        if (Hibernate.getClass(this) != Hibernate.getClass(other)) return false
        other as Contract
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
