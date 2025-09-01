package me.rightsflow.intersync.repository

import me.rightsflow.intersync.entity.KafkaBindingControl
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface KafkaBindingControlRepository : JpaRepository<KafkaBindingControl, Int>