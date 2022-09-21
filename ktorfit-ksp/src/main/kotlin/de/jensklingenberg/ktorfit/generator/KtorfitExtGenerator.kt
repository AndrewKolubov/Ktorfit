package de.jensklingenberg.ktorfit.generator

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.TypeVariableName
import de.jensklingenberg.ktorfit.model.ClassData
import de.jensklingenberg.ktorfit.model.KtorfitError.Companion.COULD_NOT_FIND_ANY_KTORFIT_ANNOTATIONS_IN_CLASS
import de.jensklingenberg.ktorfit.model.ktorfitClass
import de.jensklingenberg.ktorfit.model.ktorfitExtClass
import java.io.OutputStreamWriter

/**
 * This will generate the Ktorfit.create() extension function
 */
fun generateKtorfitExtClass(
    classDataList: List<ClassData>,
    isJS: Boolean = false,
    codeGenerator: CodeGenerator
) {
    val classNameReflectionMethod = if (isJS) {
        /**
         * On JS "simpleName" is used to get class name, because qualifiedName does not exist
         */
        //https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.reflect/-k-class/qualified-name.html
        "simpleName"
    } else {
        "qualifiedName"
    }


    /**
     * com.example.api.ExampleApi::class ->{
     * this.createExampleApi() as T
     * }
     */
    val whenCaseStatements = classDataList.joinToString("") {
        val packageName = it.packageName
        val className = it.name
        "${packageName}.${className}::class ->{\n" +
                "this.create${className}() as T\n" +
                "}\n"
    }

    val funSpec = FunSpec.builder("create")
        .addModifiers(KModifier.INLINE)
        .addTypeVariable(TypeVariableName("T").copy(reified = true))
        .receiver(TypeVariableName(ktorfitClass.name))
        .returns(TypeVariableName("T"))
        .beginControlFlow("return when(T::class){")
        .addStatement(whenCaseStatements)
        .addStatement("else ->{")
        .addStatement("throw IllegalArgumentException(\"${COULD_NOT_FIND_ANY_KTORFIT_ANNOTATIONS_IN_CLASS}\"+ T::class.$classNameReflectionMethod  )")
        .addStatement("}")
        .endControlFlow()
        .build()

    val fileSpec = FileSpec.builder(ktorfitExtClass.packageName, ktorfitExtClass.name)
        .addFileComment("Generated by Ktorfit")
        .addImports(classDataList.map { it.packageName + ".create" + it.name })
        .addFunction(funSpec)
        .build()

    codeGenerator.createNewFile(Dependencies.ALL_FILES, ktorfitExtClass.packageName, ktorfitExtClass.name, "kt")
        .use { output ->
            OutputStreamWriter(output).use { writer ->
                writer.write(fileSpec.toString())
            }
        }
}