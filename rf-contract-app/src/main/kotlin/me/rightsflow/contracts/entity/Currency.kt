package me.rightsflow.contracts.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.Hibernate

@Entity
@Table(name = "LOV_CURRENCY")
class Currency(

    @Id
    @Column(name = "ID")
    val id: Int,

    @Column(name = "ISO_CHAR_CODE", nullable = false, length = 3, unique = true)
    val isoCharCode: String,

    @Column(name = "NAME", nullable = false, length = 255)
    val name: String,

    @Column(name = "DEF", nullable = false)
    val def: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false

        if (Hibernate.getClass(this) != Hibernate.getClass(other)) return false

        other as Currency

        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}