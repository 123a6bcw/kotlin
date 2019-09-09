/*
 * Copyright 2010-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.nj2k.tree

import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiType
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.builtins.PrimitiveType
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.util.getJavaClassDescriptor
import org.jetbrains.kotlin.idea.refactoring.fqName.getKotlinFqName
import org.jetbrains.kotlin.j2k.ast.Nullability
import org.jetbrains.kotlin.nj2k.JKSymbolProvider
import org.jetbrains.kotlin.nj2k.symbols.JKClassSymbol
import org.jetbrains.kotlin.nj2k.symbols.JKMultiverseClassSymbol
import org.jetbrains.kotlin.nj2k.symbols.JKMultiverseKtClassSymbol
import org.jetbrains.kotlin.nj2k.symbols.JKUniverseClassSymbol
import org.jetbrains.kotlin.nj2k.tree.impl.*
import org.jetbrains.kotlin.nj2k.types.*
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.jvm.JvmPrimitiveType
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.isSubtypeOf
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

fun JKExpression.type(typeFactory: JKTypeFactory): JKType? =
    when (this) {
        is JKLiteralExpression -> type.toJkType(typeFactory)
        is JKOperatorExpression -> {
            when (val operator = operator) {
                is JKKtOperatorImpl -> operator.returnType
                is JKKtSpreadOperator -> (this as JKPrefixExpression).expression.type(typeFactory)//TODO ger real type
                else -> error("Cannot get type of ${operator::class}, it should be first converted to KtOperator")
            }
        }
        is JKMethodCallExpression -> identifier.returnType
        is JKFieldAccessExpressionImpl -> identifier.fieldType
        is JKQualifiedExpressionImpl -> selector.type(typeFactory)
        is JKKtThrowExpression -> typeFactory.types.nothing
        is JKClassAccessExpression ->
            JKClassTypeImpl(identifier, emptyList(), Nullability.NotNull)
        is JKJavaNewExpression -> JKClassTypeImpl(classSymbol)
        is JKKtIsExpression -> typeFactory.types.boolean
        is JKParenthesizedExpression -> expression.type(typeFactory)
        is JKTypeCastExpression -> type.type
        is JKThisExpression -> null// TODO return actual type
        is JKSuperExpression -> null// TODO return actual type
        is JKStubExpression -> null
        is JKIfElseExpression -> thenBranch.type(typeFactory)// TODO return actual type
        is JKArrayAccessExpression ->
            (expression.type(typeFactory) as? JKParametrizedType)?.parameters?.lastOrNull()
        is JKClassLiteralExpression -> {
            val symbol = when (literalType) {
                JKClassLiteralExpression.LiteralType.KOTLIN_CLASS ->
                    typeFactory.symbolProvider.provideClassSymbol(KotlinBuiltIns.FQ_NAMES.kClass.toSafe())
                JKClassLiteralExpression.LiteralType.JAVA_CLASS,
                JKClassLiteralExpression.LiteralType.JAVA_PRIMITIVE_CLASS, JKClassLiteralExpression.LiteralType.JAVA_VOID_TYPE ->
                    typeFactory.symbolProvider.provideClassSymbol("java.lang.Class")
            }
            JKClassTypeImpl(symbol, listOf(classType.type), Nullability.NotNull)
        }
        is JKKtAnnotationArrayInitializerExpression -> JKNoTypeImpl //TODO
        is JKLambdaExpression -> returnType.type
        is JKLabeledStatement ->
            statement.safeAs<JKExpressionStatement>()?.expression?.type(typeFactory)
        is JKMethodReferenceExpression -> JKNoTypeImpl //TODO
        else -> TODO(this::class.java.toString())
    }

fun JKType.asTypeElement() =
    JKTypeElementImpl(this)

fun JKClassSymbol.asType(nullability: Nullability = Nullability.Default): JKClassType =
    JKClassTypeImpl(this, emptyList(), nullability)

fun JKType.isSubtypeOf(other: JKType, typeFactory: JKTypeFactory): Boolean =
    other.toKtType(typeFactory)
        ?.let { otherType -> this.toKtType(typeFactory)?.isSubtypeOf(otherType) } == true


val PsiType.isKotlinFunctionalType: Boolean
    get() {
        val fqName = safeAs<PsiClassType>()?.resolve()?.getKotlinFqName() ?: return false
        return functionalTypeRegex.matches(fqName.asString())
    }


private val functionalTypeRegex = """(kotlin\.jvm\.functions|kotlin)\.Function[\d+]""".toRegex()


fun KtTypeReference.toJK(typeFactory: JKTypeFactory): JKType? =
    analyze(BodyResolveMode.PARTIAL)
        .get(BindingContext.TYPE, this)
        ?.let { typeFactory.fromKotlinType(it) }


fun JKType.toKtType(typeFactory: JKTypeFactory): KotlinType? = when (this) {
    is JKClassType -> classReference.toKtType()
    is JKJavaPrimitiveType -> typeFactory.fromPrimitiveType(this).toKtType(typeFactory)
    else -> null
}

infix fun JKJavaPrimitiveType.isStrongerThan(other: JKJavaPrimitiveType) =
    jvmPrimitiveTypesPriority.getValue(this.jvmPrimitiveType.primitiveType) >
            jvmPrimitiveTypesPriority.getValue(other.jvmPrimitiveType.primitiveType)

private val jvmPrimitiveTypesPriority =
    mapOf(
        PrimitiveType.BOOLEAN to -1,
        PrimitiveType.CHAR to 0,
        PrimitiveType.BYTE to 1,
        PrimitiveType.SHORT to 2,
        PrimitiveType.INT to 3,
        PrimitiveType.LONG to 4,
        PrimitiveType.FLOAT to 5,
        PrimitiveType.DOUBLE to 6
    )


fun JKClassSymbol.toKtType(): KotlinType? {
    val classDescriptor = when (this) {
        is JKMultiverseKtClassSymbol -> {
            val bindingContext = target.analyze()
            bindingContext[BindingContext.DECLARATION_TO_DESCRIPTOR, target] as ClassDescriptor
        }
        is JKMultiverseClassSymbol ->
            target.getJavaClassDescriptor()
        is JKUniverseClassSymbol ->
            target.psi<PsiClass>()?.getJavaClassDescriptor()//TODO null in case of a fake package
        else -> TODO(this::class.java.toString())
    }
    return classDescriptor?.defaultType
}

fun JKType.applyRecursive(transform: (JKType) -> JKType?): JKType =
    transform(this) ?: when (this) {
        is JKTypeParameterTypeImpl -> this
        is JKClassTypeImpl ->
            JKClassTypeImpl(
                classReference,
                parameters.map { it.applyRecursive(transform) },
                nullability
            )
        is JKNoType -> this
        is JKJavaVoidType -> this
        is JKJavaPrimitiveType -> this
        is JKJavaArrayType -> JKJavaArrayTypeImpl(type.applyRecursive(transform), nullability)
        is JKContextType -> JKContextType
        is JKJavaDisjunctionType ->
            JKJavaDisjunctionTypeImpl(disjunctions.map { it.applyRecursive(transform) }, nullability)
        is JKStarProjectionType -> this
        else -> TODO(this::class.toString())
    }

inline fun <reified T : JKType> T.updateNullability(newNullability: Nullability): T =
    if (nullability == newNullability) this
    else when (this) {
        is JKTypeParameterTypeImpl -> JKTypeParameterTypeImpl(identifier, newNullability)
        is JKClassTypeImpl -> JKClassTypeImpl(classReference, parameters, newNullability)
        is JKNoType -> this
        is JKJavaVoidType -> this
        is JKJavaPrimitiveType -> this
        is JKJavaArrayType -> JKJavaArrayTypeImpl(type, newNullability)
        is JKContextType -> JKContextType
        is JKJavaDisjunctionType -> this
        else -> TODO(this::class.toString())
    } as T

@Suppress("UNCHECKED_CAST")
fun <T : JKType> T.updateNullabilityRecursively(newNullability: Nullability): T =
    applyRecursive {
        when (it) {
            is JKTypeParameterTypeImpl -> JKTypeParameterTypeImpl(it.identifier, newNullability)
            is JKClassTypeImpl ->
                JKClassTypeImpl(
                    it.classReference,
                    it.parameters.map { it.updateNullabilityRecursively(newNullability) },
                    newNullability
                )
            is JKJavaArrayType -> JKJavaArrayTypeImpl(it.type.updateNullabilityRecursively(newNullability), newNullability)
            else -> null
        }
    } as T

fun JKType.isStringType(): Boolean =
    (this as? JKClassType)?.classReference?.isStringType() == true

fun JKClassSymbol.isStringType(): Boolean =
    fqName == CommonClassNames.JAVA_LANG_STRING
            || fqName == KotlinBuiltIns.FQ_NAMES.string.asString()

fun JKJavaPrimitiveType.toLiteralType(): JKLiteralExpression.LiteralType? =
    when (this) {
        JKJavaPrimitiveType.CHAR -> JKLiteralExpression.LiteralType.CHAR
        JKJavaPrimitiveType.BOOLEAN -> JKLiteralExpression.LiteralType.BOOLEAN
        JKJavaPrimitiveType.INT -> JKLiteralExpression.LiteralType.INT
        JKJavaPrimitiveType.LONG -> JKLiteralExpression.LiteralType.LONG
        JKJavaPrimitiveType.CHAR -> JKLiteralExpression.LiteralType.CHAR
        JKJavaPrimitiveType.DOUBLE -> JKLiteralExpression.LiteralType.DOUBLE
        JKJavaPrimitiveType.FLOAT -> JKLiteralExpression.LiteralType.FLOAT
        else -> null
    }


fun JKType.asPrimitiveType(): JKJavaPrimitiveType? =
    if (this is JKJavaPrimitiveType) this
    else when ((this as? JKClassType)?.classReference?.fqName) {
        KotlinBuiltIns.FQ_NAMES._char.asString(), CommonClassNames.JAVA_LANG_CHARACTER -> JKJavaPrimitiveTypeImpl.CHAR
        KotlinBuiltIns.FQ_NAMES._boolean.asString(), CommonClassNames.JAVA_LANG_BOOLEAN -> JKJavaPrimitiveTypeImpl.BOOLEAN
        KotlinBuiltIns.FQ_NAMES._int.asString(), CommonClassNames.JAVA_LANG_INTEGER -> JKJavaPrimitiveTypeImpl.INT
        KotlinBuiltIns.FQ_NAMES._long.asString(), CommonClassNames.JAVA_LANG_LONG -> JKJavaPrimitiveTypeImpl.LONG
        KotlinBuiltIns.FQ_NAMES._float.asString(), CommonClassNames.JAVA_LANG_FLOAT -> JKJavaPrimitiveTypeImpl.FLOAT
        KotlinBuiltIns.FQ_NAMES._double.asString(), CommonClassNames.JAVA_LANG_DOUBLE -> JKJavaPrimitiveTypeImpl.DOUBLE
        KotlinBuiltIns.FQ_NAMES._byte.asString(), CommonClassNames.JAVA_LANG_BYTE -> JKJavaPrimitiveTypeImpl.BYTE
        KotlinBuiltIns.FQ_NAMES._short.asString(), CommonClassNames.JAVA_LANG_SHORT -> JKJavaPrimitiveTypeImpl.SHORT
        else -> null
    }

fun JKJavaPrimitiveType.isNumberType() =
    this == JKJavaPrimitiveType.INT ||
            this == JKJavaPrimitiveType.LONG ||
            this == JKJavaPrimitiveType.FLOAT ||
            this == JKJavaPrimitiveType.DOUBLE


val primitiveTypes =
    listOf(
        JvmPrimitiveType.BOOLEAN,
        JvmPrimitiveType.CHAR,
        JvmPrimitiveType.BYTE,
        JvmPrimitiveType.SHORT,
        JvmPrimitiveType.INT,
        JvmPrimitiveType.FLOAT,
        JvmPrimitiveType.LONG,
        JvmPrimitiveType.DOUBLE
    )

fun JKType.arrayFqName(): String =
    if (this is JKJavaPrimitiveType)
        PrimitiveType.valueOf(jvmPrimitiveType.name).arrayTypeFqName.asString()
    else KotlinBuiltIns.FQ_NAMES.array.asString()

fun JKClassSymbol.isArrayType(): Boolean =
    fqName in arrayFqNames

private val arrayFqNames = JKJavaPrimitiveTypeImpl.KEYWORD_TO_INSTANCE.values
    .filterIsInstance<JKJavaPrimitiveType>()
    .map { PrimitiveType.valueOf(it.jvmPrimitiveType.name).arrayTypeFqName.asString() } +
        KotlinBuiltIns.FQ_NAMES.array.asString()

fun JKType.isArrayType() =
    when (this) {
        is JKClassType -> classReference.isArrayType()
        is JKJavaArrayType -> true
        else -> false
    }

val JKType.isCollectionType: Boolean
    get() = safeAs<JKClassType>()?.classReference?.fqName in collectionFqNames

private val collectionFqNames = setOf(
    KotlinBuiltIns.FQ_NAMES.mutableIterator.asString(),
    KotlinBuiltIns.FQ_NAMES.mutableList.asString(),
    KotlinBuiltIns.FQ_NAMES.mutableCollection.asString(),
    KotlinBuiltIns.FQ_NAMES.mutableSet.asString(),
    KotlinBuiltIns.FQ_NAMES.mutableMap.asString(),
    KotlinBuiltIns.FQ_NAMES.mutableMapEntry.asString(),
    KotlinBuiltIns.FQ_NAMES.mutableListIterator.asString()
)

fun JKType.arrayInnerType(): JKType? =
    when (this) {
        is JKJavaArrayType -> type
        is JKClassType ->
            if (this.classReference.isArrayType()) this.parameters.singleOrNull()
            else null
        else -> null
    }

fun JKClassSymbol.isInterface(): Boolean {
    val target = target
    return when (target) {
        is PsiClass -> target.isInterface
        is KtClass -> target.isInterface()
        is JKClass -> target.classKind == JKClass.ClassKind.INTERFACE
        else -> false
    }
}

fun JKType.isInterface(): Boolean =
    (this as? JKClassType)?.classReference?.isInterface() ?: false


fun JKType.replaceJavaClassWithKotlinClassType(symbolProvider: JKSymbolProvider): JKType =
    applyRecursive { type ->
        if (type is JKClassType && type.classReference.fqName == "java.lang.Class") {
            JKClassTypeImpl(
                symbolProvider.provideClassSymbol(KotlinBuiltIns.FQ_NAMES.kClass.toSafe()),
                type.parameters.map { it.replaceJavaClassWithKotlinClassType(symbolProvider) },
                Nullability.NotNull
            )
        } else null
    }
