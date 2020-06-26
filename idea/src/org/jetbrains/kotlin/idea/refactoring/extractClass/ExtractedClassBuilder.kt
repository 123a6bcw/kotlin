/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.refactoring.extractClass

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.util.FixableUsageInfo
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.codeInsight.DescriptorToSourceUtilsIde
import org.jetbrains.kotlin.idea.refactoring.extractClass.fixUsage.*
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.js.resolve.diagnostics.findPsi
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getChildrenOfType
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector
import org.jetbrains.kotlin.psi.psiUtil.isAncestor
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.callUtil.getType
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.typeUtil.isInterface
import org.jetbrains.uast.getContainingUAnnotationEntry

class ExtractedClassBuilder private constructor(
    private val sourceClass: KtClassOrObject,
    private val isCompanionObject: Boolean,
    private val thisClassExtractedMembers: List<KtElement>,
    private val allExtractedMembers: List<KtElement>,
    private val targetName: String,
    private val targetPackage: String, //TODO add package and FQ names usage, shorten references
    private val leaveDelegates: Boolean,
    private val fixVisibilities: Boolean,
    private val placeInSameFile: Boolean
) {
    private val project = sourceClass.project
    private val ktFactory = KtPsiFactory(project)

    private val argumentsAddedToExtractedClassConstructor = mutableListOf<KtParameter>()
    private val implementedClassesOrInterfaces = mutableListOf<KtSuperTypeListEntry>()
    private val usagesToFix = mutableListOf<FixableUsageInfo>()

    private val extractedClassTypeParameters = mutableListOf<KtTypeParameter>()

    private val bindingContext = sourceClass.analyze(BodyResolveMode.PARTIAL)

    private val originalClass = if (isCompanionObject) {
        sourceClass.parent
    } else {
        sourceClass
    }

    private var shouldCreateSourceReferenceInExtractedClass: Boolean = false

    init {
        collectReferenceUsagesToFix()
        getImplementedClassesOrInterfaces()
        collectExtractedClassTypeParameters()

        processValsWithOutOfPlaceInitialisationAndCollectExtractedClassConstructorParameters()

        if (shouldCreateSourceReferenceInExtractedClass) {
            argumentsAddedToExtractedClassConstructor.add(
                ktFactory.createParameter(
                    "private val " + getDelegationFieldName(sourceClass.name) + " : " + sourceClass.name
                )
            )
        }
    }

    private fun fixUsages() {
        for (usage in usagesToFix) {
            usage.fixUsage()
        }
    }

    fun createExtractedClass(): KtClassOrObject {
        fixUsages()

        val ktFactory = KtPsiFactory(sourceClass.project)

        val primaryConstructorEntry = if (argumentsAddedToExtractedClassConstructor.isNotEmpty()) "()" else ""

        val declarationSuffix = "$targetName$primaryConstructorEntry {}"

        val result = if (sourceClass is KtClass) {
            ktFactory.createClass("class $declarationSuffix")
        } else if (sourceClass is KtObjectDeclaration && sourceClass.isCompanion()) {
            ktFactory.createObject("companion object $declarationSuffix")
        } else {
            ktFactory.createObject("object $declarationSuffix")
        }

        argumentsAddedToExtractedClassConstructor.forEach {
            result.getPrimaryConstructorParameterList()?.addParameter(it.copy() as KtParameter)
        }
        implementedClassesOrInterfaces.forEach {
            result.addSuperTypeListEntry(it.copy() as KtSuperTypeListEntry)
        }

        thisClassExtractedMembers.forEach {
            result.addDeclaration(it.copy() as KtDeclaration)
        }

        return result
    }

    private fun collectExtractedClassTypeParameters(): List<KtTypeParameter> {
        return listOf()
    }

    private fun processValsWithOutOfPlaceInitialisationAndCollectExtractedClassConstructorParameters() {
        //TODO if property have var or function call in initializer, than it's a red flag:
        // user have to bother himself how to correctly init this.
        // Same for initialising in the several init's with local variables etc.
        // Otherwise, there is no problem

        val sourceClassInitParametersUsedInExtractedMemberInitializer = linkedSetOf<KtParameter>()

        for (member in thisClassExtractedMembers) {
            if (member !is KtProperty || !member.hasDelegateExpressionOrInitializer()) {
                continue
            }

            for (reference in member.getChildrenOfType<KtReferenceExpression>()) {
                val resolved = reference.mainReference.resolve()
                if (resolved is KtParameter && sourceClass.primaryConstructorParameters.contains(resolved) && !resolved.hasValOrVar()) {
                    sourceClassInitParametersUsedInExtractedMemberInitializer.add(resolved)
                }
            }
        }

        val declarationsMovedAsParameters = thisClassExtractedMembers.filterIsInstance<KtProperty>().filterNot {
            it.isVar || it.hasDelegateExpressionOrInitializer() || it.hasDelegate() || it.getter != null
        }

        val fromDeclarationToInitializerInClassInitializer = mutableMapOf<KtDeclaration, KtExpression>()
        val fromConstructorToDeclarationToInitializer = mutableMapOf<KtElement, MutableMap<KtDeclaration, KtExpression>>()

        var lastUsedAnonymousInitializer: KtAnonymousInitializer? = null

        fun processInitializer(constructorOrClassInitializer: KtElement, isConstructor: Boolean) {
            val assigmentExpressions =
                PsiTreeUtil.findChildrenOfType(constructorOrClassInitializer, KtBinaryExpression::class.java).filter {
                    it.operationToken == KtTokens.EQ && it.left is KtReferenceExpression && it.right != null
                }

            for (assigmentExpression in assigmentExpressions) {
                val resolved = assigmentExpression.left?.mainReference?.resolve() ?: continue

                if (declarationsMovedAsParameters.contains(resolved)) {
                    continue
                }

                usagesToFix.add(RemoveElement(assigmentExpression))

                if (isConstructor) {
                    assigmentExpression.right?.let {
                        fromConstructorToDeclarationToInitializer.putIfAbsent(constructorOrClassInitializer, mutableMapOf())
                        fromConstructorToDeclarationToInitializer[constructorOrClassInitializer]!![resolved as KtDeclaration] =
                            it
                    }
                } else {
                    assigmentExpression.right?.let {
                        lastUsedAnonymousInitializer = constructorOrClassInitializer as KtAnonymousInitializer
                        fromDeclarationToInitializerInClassInitializer[resolved as KtDeclaration] = it
                    }
                }
            }
        }

        for (initializer in sourceClass.getAnonymousInitializers()) {
            processInitializer(initializer, true)
        }

        for (initializer in sourceClass.secondaryConstructors) {
            processInitializer(initializer, false)
        }

        addExtractedClassFieldFix(
            fromDeclarationToInitializerInClassInitializer,
            fromConstructorToDeclarationToInitializer,
            sourceClassInitParametersUsedInExtractedMemberInitializer, lastUsedAnonymousInitializer
        )

        collectArgumentsAddedToExtractedClassConstructor(
            sourceClassInitParametersUsedInExtractedMemberInitializer,
            declarationsMovedAsParameters
        )
    }

    private fun collectArgumentsAddedToExtractedClassConstructor(
        sourceClassInitParametersUsedInExtractedMemberInitializer: LinkedHashSet<KtParameter>,
        declarationsMovedAsParameters: List<KtProperty>
    ) {
        sourceClassInitParametersUsedInExtractedMemberInitializer.forEach {
            argumentsAddedToExtractedClassConstructor.add(it.copy() as KtParameter)
        }

        declarationsMovedAsParameters.forEach { declaration ->
            argumentsAddedToExtractedClassConstructor.add(
                ktFactory.createParameter(
                    declaration.name + "Init : " + declaration.getType(bindingContext).toString()
                )
            ) //TODO Init0123...
        }
    }

    private fun addExtractedClassFieldFix(
        fromDeclarationToInitializerInClassInitializer: Map<KtDeclaration, KtExpression>,
        fromConstructorToDeclarationToInitializer: Map<KtElement, Map<KtDeclaration, KtExpression>>,
        sourceClassInitParametersUsedInExtractedMemberInitializer: LinkedHashSet<KtParameter>,
        lastUsedAnonymousInitializer: KtAnonymousInitializer?
    ) {
        if (fromDeclarationToInitializerInClassInitializer.isEmpty() && fromConstructorToDeclarationToInitializer.isEmpty()) {
            usagesToFix.add(
                AddExtractedClassFieldWithInitialisationByParameters(
                    sourceClass,
                    targetName,
                    getDelegationFieldName(targetName),
                    sourceClassInitParametersUsedInExtractedMemberInitializer,
                    shouldCreateSourceReferenceInExtractedClass
                )
            )
        } else {
            AddExtractedClassFieldDeclaration(sourceClass, targetName, getDelegationFieldName(targetName))

            if (fromConstructorToDeclarationToInitializer.isEmpty()) {
                usagesToFix.add(
                    AddExtractedClassFieldInitialisationInTheLastInit(
                        sourceClass,
                        sourceClassInitParametersUsedInExtractedMemberInitializer,
                        lastUsedAnonymousInitializer,
                        fromDeclarationToInitializerInClassInitializer,
                        shouldCreateSourceReferenceInExtractedClass
                    )
                )
            } else {
                for (constructor in sourceClass.secondaryConstructors) {
                    AddExtractedClassFieldInitialisationAtTheEndOfConstructor(
                        sourceClass,
                        sourceClassInitParametersUsedInExtractedMemberInitializer,
                        fromDeclarationToInitializerInClassInitializer,
                        fromConstructorToDeclarationToInitializer[constructor],
                        shouldCreateSourceReferenceInExtractedClass
                    )
                }
            }
        }
    }

    private fun getImplementedClassesOrInterfaces(): List<KtSuperTypeListEntry> {
        val result = mutableListOf<KtSuperTypeListEntry>()

        for (superTypeListEntry in sourceClass.superTypeListEntries) {
            val superInterfaceType =
                superTypeListEntry.analyze(BodyResolveMode.PARTIAL)[BindingContext.TYPE, superTypeListEntry.typeReference]
                    ?: continue

            if (!superInterfaceType.isInterface()) {
                continue
            }

            val superClassDescriptor = superInterfaceType.constructor.declarationDescriptor ?: continue
            val superInterface = DescriptorToSourceUtilsIde.getAnyDeclaration(project, superClassDescriptor) as? KtClass ?: continue

            var interfaceRealised = true

            val interfaceDeclarations = superInterface.declarations
            for (sourceDeclaration in sourceClass.declarations) {
                if (sourceDeclaration !is KtCallableDeclaration) {
                    continue
                }

                val descriptor = sourceDeclaration.resolveToDescriptorIfAny()
                if (descriptor == null || descriptor !is CallableMemberDescriptor) {
                    continue
                }

                //TODO Probably there is an easier way to check this (without descriptors)
                for (overriddenDescriptor in descriptor.overriddenDescriptors) {
                    if (overriddenDescriptor.findPsi() in interfaceDeclarations) {
                        if (sourceDeclaration !in thisClassExtractedMembers) {
                            interfaceRealised = false
                        }
                    }
                }
            }

            if (interfaceRealised) {
                result.add(superTypeListEntry)
            }
        }

        return result
    }

    private fun inExtractedDeclarations(referencedElement: PsiElement): Boolean {
        return allExtractedMembers.contains(referencedElement)
    }

    private fun getDelegationFieldName(name: String?): String {
        //TODO

        val thisName = name ?: "companion" //TODO come up with the name for companion objects

        val simpleClassName = thisName.substring(thisName.lastIndexOf('.') + 1).decapitalize()

        return simpleClassName
    }

    private fun collectReferenceUsagesToFix() {
        for (member in thisClassExtractedMembers) {
            collectFixesForExtractedMemberUsages(member)
            collectFixesForSourceUsagesInExtractedMember(member)
        }
    }

    private fun collectFixesForExtractedMemberUsages(member: KtElement) {
        val scope = GlobalSearchScope.allScope(project) //TODO fix scope (probably not "allScope")
        val references = ReferencesSearch.search(member, scope)

        var shouldBeInternal = false

        for (reference in references) {
            val referenceElement = reference.element
            val referencedElement = if (referenceElement is KtReferenceExpression) {
                referenceElement.mainReference.resolve() ?: continue
            } else {
                continue
            }

            if (inExtractedDeclarations(referencedElement)) {
                continue
            }

            shouldBeInternal = true

            if (isCompanionObject) {
                usagesToFix.add(ReplaceCompanionObjectReference(referenceElement, sourceClass, targetName))
            } else {
                usagesToFix.add(AddDelegationFieldQualifier(referenceElement, getDelegationFieldName(targetName)))
            }
        }

        if (fixVisibilities && shouldBeInternal) {
            usagesToFix.add(MakeAtLeastInternal(member))
        }
    }

    private fun collectFixesForSourceUsagesInExtractedMember(member: KtElement) {
        val sourceMembersShouldBeInternal = mutableSetOf<KtElement>()

        member.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element is KtReferenceExpression) {
                    val qualifier = element.getQualifiedExpressionForSelector()
                    if (qualifier == null || qualifier is KtThisExpression) {
                        val resolved = element.mainReference.resolve()

                        if (resolved != null
                            && !inExtractedDeclarations(resolved)
                            && sourceClass.declarations.contains(resolved) //TODO if companion object, should also check declarations in parent
                        ) {

                            sourceMembersShouldBeInternal.add(resolved as KtElement)

                            if (qualifier == null) {
                                shouldCreateSourceReferenceInExtractedClass = true
                                usagesToFix.add(AddDelegationFieldQualifier(element, getDelegationFieldName(sourceClass.name)))
                            }
                        }
                    }
                }

                super.visitElement(element)
            }
        })

        if (leaveDelegates || member is KtDeclaration && member.hasModifier(KtModifierKeywordToken.keywordModifier("override"))) {
            val delegate = if (isCompanionObject) {
                targetName ?: ""
            } else {
                getDelegationFieldName(targetName)
            }

            usagesToFix.add(MakeDelegate(member, delegate))
        } else {
            usagesToFix.add(RemoveElement(member))
        }

        if (fixVisibilities) {
            for (sourceMember in sourceMembersShouldBeInternal) {
                usagesToFix.add(MakeAtLeastInternal(sourceMember))
            }
        }
    }

    companion object FACTORY {
        fun analyzeContextAndGetBuilder(
            sourceClass: KtClassOrObject,
            isCompanionObject: Boolean,
            thisClassExtractedDeclarations: List<KtDeclaration>,
            allExtractedDeclaration: List<KtDeclaration>,
            targetName: String,
            targetPackage: String,
            leaveDelegates: Boolean,
            fixVisibilities: Boolean,
            placeInSameFile: Boolean
        ): ExtractedClassBuilder {
            return ExtractedClassBuilder(
                sourceClass,
                isCompanionObject,
                thisClassExtractedDeclarations,
                allExtractedDeclaration,
                targetName,
                targetPackage,
                leaveDelegates,
                fixVisibilities,
                placeInSameFile
            )
        }
    }
}