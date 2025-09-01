package me.rightsflow.intersync.scheduler

import org.apache.avro.generic.GenericRecord

interface DlqHandler {
    val topic: String
    fun process(key: String, value: GenericRecord?)
}
