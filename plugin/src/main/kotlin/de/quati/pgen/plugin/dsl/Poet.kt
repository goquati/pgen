package de.quati.pgen.plugin.dsl

import com.squareup.kotlinpoet.*


@JvmInline
value class PackageName(val name: String) {
    override fun toString(): String = name
    operator fun plus(subPackage: String) = PackageName("$name.$subPackage")
    fun className(vararg name: String) = ClassName(this.name, *name)
}

fun fileSpec(
    packageName: PackageName,
    name: String,
    block: FileSpec.Builder.() -> Unit,
) = FileSpec.builder(packageName = packageName.name, fileName = name).apply(block).build()

fun buildObject(
    name: String,
    block: TypeSpec.Builder.() -> Unit,
) = TypeSpec.objectBuilder(name).apply(block).build()

fun buildEnum(
    name: String,
    block: TypeSpec.Builder.() -> Unit,
) = TypeSpec.enumBuilder(name).apply(block).build()

fun buildInterface(
    name: String,
    block: TypeSpec.Builder.() -> Unit,
) = TypeSpec.interfaceBuilder(name).apply(block).build()

fun FileSpec.Builder.addInterface(
    name: String,
    block: TypeSpec.Builder.() -> Unit,
) = addType(TypeSpec.interfaceBuilder(name).apply(block).build())

fun buildClass(
    name: String,
    block: TypeSpec.Builder.() -> Unit,
) = TypeSpec.classBuilder(name).apply(block).build()

fun buildDataClass(
    name: String,
    block: TypeSpec.Builder.() -> Unit,
) = TypeSpec.classBuilder(name).apply { addModifiers(KModifier.DATA) }.apply(block).build()

fun buildValueClass(
    name: String,
    block: TypeSpec.Builder.() -> Unit,
) = TypeSpec.classBuilder(name).apply {
    addModifiers(KModifier.VALUE)
    addAnnotation(JvmInline::class)
    block()
}.build()

fun TypeSpec.Builder.addClass(
    name: String,
    block: TypeSpec.Builder.() -> Unit,
) = addType(buildClass(name = name, block = block))

fun FileSpec.Builder.addClass(
    name: String,
    block: TypeSpec.Builder.() -> Unit,
) = addType(buildClass(name = name, block = block))

fun FileSpec.Builder.addProperty(name: String, type: TypeName, block: PropertySpec.Builder.() -> Unit) =
    addProperty(PropertySpec.builder(name = name, type = type).apply(block).build())

fun TypeSpec.Builder.addProperty(name: String, type: TypeName, block: PropertySpec.Builder.() -> Unit) =
    addProperty(PropertySpec.builder(name = name, type = type).apply(block).build())

fun TypeSpec.Builder.addEnumConstant(
    name: String,
    block: TypeSpec.Builder.() -> Unit,
) = addEnumConstant(name, TypeSpec.anonymousClassBuilder().apply(block).build())

fun TypeSpec.Builder.primaryConstructor(
    block: FunSpec.Builder.() -> Unit,
) = primaryConstructor(FunSpec.constructorBuilder().apply(block).build())

fun TypeSpec.Builder.addCompanionObject(
    block: TypeSpec.Builder.() -> Unit,
) = addType(TypeSpec.companionObjectBuilder().apply(block).build())

fun TypeSpec.Builder.addObject(
    name: String,
    block: TypeSpec.Builder.() -> Unit,
) = addType(TypeSpec.objectBuilder(name).apply(block).build())

fun buildFunction(
    name: String,
    block: FunSpec.Builder.() -> Unit,
) = FunSpec.builder(name).apply(block).build()

fun TypeSpec.Builder.addFunction(
    name: String,
    block: FunSpec.Builder.() -> Unit,
) = addFunction(buildFunction(name = name, block = block))

fun FileSpec.Builder.addFunction(
    name: String,
    block: FunSpec.Builder.() -> Unit,
) = addFunction(buildFunction(name = name, block = block))

fun TypeSpec.Builder.addInitializerBlock(block: CodeBlock.Builder.() -> Unit) =
    addInitializerBlock(CodeBlock.builder().apply(block).build())

fun FunSpec.Builder.addParameter(
    name: String,
    type: TypeName,
    block: ParameterSpec.Builder.() -> Unit,
) = addParameter(ParameterSpec.builder(name, type).apply(block).build())

fun FunSpec.Builder.addCode(
    block: CodeBlock.Builder.() -> Unit
) = addCode(CodeBlock.builder().apply(block).build())

fun CodeBlock.Builder.addControlFlow(controlFlow: String, vararg args: Any, block: CodeBlock.Builder.() -> Unit) {
    beginControlFlow(controlFlow, *args)
    block()
    endControlFlow()
}

fun CodeBlock.Builder.indent(block: CodeBlock.Builder.() -> Unit) {
    indent()
    block()
    unindent()
}
