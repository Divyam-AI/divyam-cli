/**
 * Copyright 2025-2026 DivyamAI Technologies Private Limited
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyam.client.reflection

/**
 * Marks an entity as being used via reflection for GraalVM.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FIELD,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER
)
annotation class Reflectable()
