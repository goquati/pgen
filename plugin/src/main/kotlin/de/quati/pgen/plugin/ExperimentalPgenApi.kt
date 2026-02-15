package de.quati.pgen.plugin

@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "This API is experimental and may change in the future."
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.CONSTRUCTOR
)
public annotation class ExperimentalPgenApi
