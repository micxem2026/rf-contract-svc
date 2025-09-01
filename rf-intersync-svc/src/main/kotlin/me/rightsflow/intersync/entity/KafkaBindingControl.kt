package me.rightsflow.intersync.entity

import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(name = "kafka_bindings_control")
data class KafkaBindingControl(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    val id: Int,

    @Column(name = "binding_name", nullable = false, length = 255)
    val bindingName: String,

    @Column(name = "binding_state", nullable = false, length = 10)
    @Enumerated(EnumType.STRING)
    val bindingState: BindingState,

    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_at")
    var updatedAt: OffsetDateTime? = null

) {
    enum class BindingState {
        PAUSE, RESUME
    }
}