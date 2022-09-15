package de.jensklingenberg.ktorfit.parser

import KtorfitProcessor
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import de.jensklingenberg.ktorfit.model.FunctionData
import de.jensklingenberg.ktorfit.model.KtorfitError
import de.jensklingenberg.ktorfit.model.KtorfitError.Companion.BODY_PARAMETERS_CANNOT_BE_USED_WITH_FORM_OR_MULTI_PART_ENCODING
import de.jensklingenberg.ktorfit.model.KtorfitError.Companion.HEADERS_VALUE_MUST_BE_IN_FORM
import de.jensklingenberg.ktorfit.model.KtorfitError.Companion.FIELD_MAP_PARAMETERS_CAN_ONLY_BE_USED_WITH_FORM_ENCODING
import de.jensklingenberg.ktorfit.model.KtorfitError.Companion.FIELD_PARAMETERS_CAN_ONLY_BE_USED_WITH_FORM_ENCODING
import de.jensklingenberg.ktorfit.model.KtorfitError.Companion.FORM_URL_ENCODED_CAN_ONLY_BE_SPECIFIED_ON_HTTP_METHODS_WITH_REQUEST_BODY
import de.jensklingenberg.ktorfit.model.KtorfitError.Companion.FOR_STREAMING_THE_RETURN_TYPE_MUST_BE_HTTP_STATEMENT
import de.jensklingenberg.ktorfit.model.KtorfitError.Companion.MISSING_EITHER_KEYWORD_URL_OrURL_PARAMETER
import de.jensklingenberg.ktorfit.model.KtorfitError.Companion.MISSING_X_IN_RELATIVE_URL_PATH
import de.jensklingenberg.ktorfit.model.KtorfitError.Companion.MULTIPART_CAN_ONLY_BE_SPECIFIED_ON_HTTPMETHODS
import de.jensklingenberg.ktorfit.model.KtorfitError.Companion.MULTIPLE_URL_METHOD_ANNOTATIONS_FOUND
import de.jensklingenberg.ktorfit.model.KtorfitError.Companion.NON_BODY_HTTP_METHOD_CANNOT_CONTAIN_BODY
import de.jensklingenberg.ktorfit.model.KtorfitError.Companion.NO_HTTP_ANNOTATION_AT
import de.jensklingenberg.ktorfit.model.KtorfitError.Companion.ONLY_ONE_ENCODING_ANNOTATION_IS_ALLOWED
import de.jensklingenberg.ktorfit.model.KtorfitError.Companion.ONLY_ONE_HTTP_METHOD_IS_ALLOWED
import de.jensklingenberg.ktorfit.model.KtorfitError.Companion.ONLY_ONE_REQUEST_BUILDER_IS_ALLOWED
import de.jensklingenberg.ktorfit.model.KtorfitError.Companion.PATH_CAN_ONLY_BE_USED_WITH_RELATIVE_URL_ON
import de.jensklingenberg.ktorfit.model.KtorfitError.Companion.URL_CAN_ONLY_BE_USED_WITH_EMPY
import de.jensklingenberg.ktorfit.model.TypeData
import de.jensklingenberg.ktorfit.model.annotations.*
import de.jensklingenberg.ktorfit.model.ktorfitError
import de.jensklingenberg.ktorfit.utils.*

/**
 * Collect all [HttpMethodAnnotation] from a [KSFunctionDeclaration]
 * @return list of [HttpMethodAnnotation]
 */
fun getHttpMethodAnnotations(func: KSFunctionDeclaration): List<HttpMethodAnnotation> {
    val getAnno = func.parseHTTPMethodAnno("GET")
    val putAnno = func.parseHTTPMethodAnno("PUT")
    val postAnno = func.parseHTTPMethodAnno("POST")
    val deleteAnno = func.parseHTTPMethodAnno("DELETE")
    val headAnno = func.parseHTTPMethodAnno("HEAD")
    val optionsAnno = func.parseHTTPMethodAnno("OPTIONS")
    val patchAnno = func.parseHTTPMethodAnno("PATCH")
    val httpAnno = func.parseHTTPMethodAnno("HTTP")

    return listOfNotNull(getAnno, postAnno, putAnno, deleteAnno, headAnno, optionsAnno, patchAnno, httpAnno)
}

data class MyType(val qualifiedName: String, val typeArgs: List<MyType> = emptyList()){
    override fun toString(): String {
        var qua = qualifiedName
       val args = typeArgs.joinToString() { it.toString() }
        val args2 = if(args.isNotEmpty()){

            "listOf($args)"
        }else {
            ""
        }
        return """MyType("$qua",$args2)"""
    }
}

//https://kotlinlang.org/docs/packages.html
fun defaultImports() = listOf(
    "kotlin.*",
    "kotlin.annotation.*",
    "kotlin.collections.*",
    "kotlin.comparisons.*",
    "kotlin.io.*",
    "kotlin.ranges.*",
    "kotlin.sequences.*",
    "kotlin.text.*"
)

fun getMyType(text: String, imports: List<String>, packageName: String): MyType {
    val classImports = imports + defaultImports()
    var className = text.substringBefore("<", "")
    if (className.isEmpty()) {
        className = text.substringBefore(",", "")
    }
    if (className.isEmpty()) {
        className = text
    }
    val type = (text.removePrefix(className)).substringAfter("<").substringBeforeLast(">")
    val argumentsTypes = mutableListOf<MyType>()
    if (type.contains("<")) {
        argumentsTypes.add(getMyType(type, classImports, packageName))
    } else if (type.contains(",")) {
        type.split(",").forEach {
            argumentsTypes.add(getMyType(it, classImports, packageName))
        }
    } else if (type.isNotEmpty()) {
        argumentsTypes.add(getMyType(type, classImports, packageName))
    }


    //Look in package
    val found =
        KtorfitProcessor.rresolver.getClassDeclarationByName("$packageName.$className")?.qualifiedName?.asString()
    found?.let {
        className = it
    }

    //Look in imports


    //Wildcards
    val isWildCard = className == "*"
    if(!isWildCard){
        classImports.forEach {
            if (it.substringAfterLast(".") == className) {
                className = it
            }

            val packageName = it.substringBeforeLast(".")
            val found2 =
                KtorfitProcessor.rresolver.getClassDeclarationByName("$packageName.$className")?.qualifiedName?.asString()
            found2?.let {
                className = it
            }
        }
    }


    return MyType(className,  argumentsTypes)
}

fun getFunctionDataList(
    ksFunctionDeclarationList: List<KSFunctionDeclaration>,
    logger: KSPLogger,
    imports: List<String>,
    packageName: String
): List<FunctionData> {

    return ksFunctionDeclarationList.map { funcDeclaration ->

        val functionName = funcDeclaration.simpleName.asString()
        val functionParameters = funcDeclaration.parameters.map { getParameterData(it, logger) }
        KtorfitProcessor.rresolver
        val unqualified = getMyType(
            funcDeclaration.returnType?.resolve().resolveTypeName().replace("\\s".toRegex(), ""),
            imports,
            packageName
        )
//unqualified.toString().replace("typeArgs=[","listOf<MyType>(").replace("]","").replace(", listOf","\", listOf").replace("qualifiedName=","\"")
       logger.info(unqualified.toString())

        val returnType = TypeData(
            funcDeclaration.returnType?.resolve().resolveTypeName(),
            unqualified.toString()
        )

        val functionAnnotationList = mutableListOf<FunctionAnnotation>()

        with(funcDeclaration) {
            if (funcDeclaration.typeParameters.isNotEmpty()) {
                logger.ktorfitError(
                    KtorfitError.FUNCTION_OR_PARAMETERS_TYPES_MUST_NOT_INCLUDE_ATYPE_VARIABLE_OR_WILDCARD,
                    funcDeclaration
                )
            }

            this.getHeadersAnnotation()?.let { headers ->
                headers.path.forEach {
                    //Check if headers are in valid format

                    try {
                        val (key, value) = it.split(":")
                    } catch (exception: Exception) {
                        logger.ktorfitError(HEADERS_VALUE_MUST_BE_IN_FORM + it, funcDeclaration)
                    }
                }
                functionAnnotationList.add(headers)
            }

            this.getFormUrlEncodedAnnotation()?.let { formUrlEncoded ->
                if (functionParameters.none { it.hasAnnotation<Field>() || it.hasAnnotation<FieldMap>() }) {
                    logger.ktorfitError(
                        KtorfitError.FORM_ENCODED_METHOD_MUST_CONTAIN_AT_LEAST_ONE_FIELD_OR_FIELD_MAP,
                        funcDeclaration
                    )
                }

                functionAnnotationList.add(formUrlEncoded)
            }

            this.getStreamingAnnotation()?.let { streaming ->
                if (returnType.name != "HttpStatement") {
                    logger.ktorfitError(
                        FOR_STREAMING_THE_RETURN_TYPE_MUST_BE_HTTP_STATEMENT,
                        funcDeclaration
                    )
                }
                functionAnnotationList.add(streaming)
            }

            this.getMultipartAnnotation()?.let {
                functionAnnotationList.add(it)
            }
        }

        val httpMethodAnnoList = getHttpMethodAnnotations(funcDeclaration)

        if (httpMethodAnnoList.isEmpty()) {
            logger.ktorfitError(NO_HTTP_ANNOTATION_AT(functionName), funcDeclaration)
        }

        if (httpMethodAnnoList.size > 1) {
            logger.ktorfitError(ONLY_ONE_HTTP_METHOD_IS_ALLOWED + "Found: " + httpMethodAnnoList.joinToString { it.httpMethod.keyword } + " at " + functionName,
                funcDeclaration)
        }

        val httpMethodAnno = httpMethodAnnoList.first()

        if (httpMethodAnno.path.isEmpty() && functionParameters.none { it.hasAnnotation<Url>() }) {
            logger.ktorfitError(
                MISSING_EITHER_KEYWORD_URL_OrURL_PARAMETER(httpMethodAnno.httpMethod.keyword),
                funcDeclaration
            )
        }

        if (functionParameters.filter { it.hasRequestBuilderAnno }.size > 1) {
            logger.ktorfitError(ONLY_ONE_REQUEST_BUILDER_IS_ALLOWED + " Found: " + httpMethodAnnoList.joinToString { it.toString() } + " at " + functionName,
                funcDeclaration)
        }

        when (httpMethodAnno.httpMethod) {
            HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH -> {}
            else -> {
                if (httpMethodAnno is CustomHttp && httpMethodAnno.hasBody) {
                    //Do nothing
                } else if (functionParameters.any { it.hasAnnotation<Body>() }) {
                    logger.ktorfitError(NON_BODY_HTTP_METHOD_CANNOT_CONTAIN_BODY, funcDeclaration)
                }

                if (functionAnnotationList.any { it is Multipart }) {
                    logger.ktorfitError(
                        MULTIPART_CAN_ONLY_BE_SPECIFIED_ON_HTTPMETHODS,
                        funcDeclaration
                    )
                }

                if (funcDeclaration.getFormUrlEncodedAnnotation() != null) {
                    logger.ktorfitError(
                        FORM_URL_ENCODED_CAN_ONLY_BE_SPECIFIED_ON_HTTP_METHODS_WITH_REQUEST_BODY,
                        funcDeclaration
                    )
                }
            }
        }

        if (functionParameters.any { it.hasAnnotation<Path>() } && httpMethodAnno.path.isEmpty()) {
            logger.ktorfitError(
                PATH_CAN_ONLY_BE_USED_WITH_RELATIVE_URL_ON + "@${httpMethodAnno.httpMethod.keyword}",
                funcDeclaration
            )
        }

        functionParameters.filter { it.hasAnnotation<Path>() }.forEach {
            val pathAnnotation = it.findAnnotationOrNull<Path>()
            if (!httpMethodAnno.path.contains("{${pathAnnotation?.value ?: ""}}")) {
                logger.ktorfitError(
                    MISSING_X_IN_RELATIVE_URL_PATH(pathAnnotation?.value ?: ""),
                    funcDeclaration
                )
            }
        }

        if (funcDeclaration.getFormUrlEncodedAnnotation() != null && funcDeclaration.getMultipartAnnotation() != null) {
            logger.ktorfitError(ONLY_ONE_ENCODING_ANNOTATION_IS_ALLOWED, funcDeclaration)
        }

        if (functionParameters.any { it.hasAnnotation<Url>() }) {
            if (functionParameters.filter { it.hasAnnotation<Url>() }.size > 1) {
                logger.ktorfitError(MULTIPLE_URL_METHOD_ANNOTATIONS_FOUND, funcDeclaration)
            }
            if (httpMethodAnno.path.isNotEmpty()) {
                logger.ktorfitError(
                    URL_CAN_ONLY_BE_USED_WITH_EMPY(httpMethodAnno.httpMethod.keyword),
                    funcDeclaration
                )
            }
        }

        if (functionParameters.any { it.hasAnnotation<Field>() } && funcDeclaration.getFormUrlEncodedAnnotation() == null) {
            logger.ktorfitError(FIELD_PARAMETERS_CAN_ONLY_BE_USED_WITH_FORM_ENCODING, funcDeclaration)
        }

        if (functionParameters.any { it.hasAnnotation<FieldMap>() } && funcDeclaration.getFormUrlEncodedAnnotation() == null) {
            logger.ktorfitError(FIELD_MAP_PARAMETERS_CAN_ONLY_BE_USED_WITH_FORM_ENCODING, funcDeclaration)
        }

        if (functionParameters.any { it.hasAnnotation<Body>() } && funcDeclaration.getFormUrlEncodedAnnotation() != null) {
            logger.ktorfitError(BODY_PARAMETERS_CANNOT_BE_USED_WITH_FORM_OR_MULTI_PART_ENCODING, funcDeclaration)
        }

        return@map FunctionData(
            functionName,
            returnType,
            funcDeclaration.isSuspend,
            functionParameters,
            functionAnnotationList,
            httpMethodAnno
        )

    }
}
