package com.example.lawyer.domain.model

import jakarta.persistence.Column
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import java.time.OffsetDateTime

@MappedSuperclass
open class AuditableEntity(
    @Column(nullable = false, updatable = false)
    open var createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(nullable = false)
    open var updatedAt: OffsetDateTime = OffsetDateTime.now()
) {
    @PrePersist
    fun onCreate() {
        val now = OffsetDateTime.now()
        createdAt = now
        updatedAt = now
    }

    @PreUpdate
    fun onUpdate() {
        updatedAt = OffsetDateTime.now()
    }
}
