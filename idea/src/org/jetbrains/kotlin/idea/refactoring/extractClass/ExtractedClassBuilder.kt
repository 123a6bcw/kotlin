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
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.refactoring.extractClass.fixUsage.AddDelegationFieldQualifier
import org.jetbrains.kotlin.idea.refactoring.extractClass.fixUsage.MakeDelegate
import org.jetbrains.kotlin.idea.refactoring.extractClass.fixUsage.RemoveElement
import org.jetbrains.kotlin.idea.refactoring.extractClass.fixUsage.ReplaceCompanionObjectReference
import org.jetbrains.kotlin.idea.refactoring.isInterfaceClass
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.js.resolve.diagnostics.findPsi
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector

class ExtractedClassBuilder(
    private val sourceClass: KtClassOrObject,
    private val thisClassExtractedDeclarations: List<KtDeclaration>,
    private val allExtractedDeclaration: List<KtDeclaration>,
    private val isCompanionObject: Boolean,
    private val targetName: String?,
    private val leaveDelegates: Boolean
) {
    private val argumentsAddedToExtractedClassConstructor: List<KtParameter>
    private val implementedClassesOrInterfaces: List<KtSuperTypeListEntry>
    private val usagesToFix: List<FixableUsageInfo>
    private val project = sourceClass.project
    private val ktFactory = KtPsiFactory(project)

    private val originalClass = if (isCompanionObject) {
        sourceClass.parent
    } else {
        sourceClass
    }

    init {
        argumentsAddedToExtractedClassConstructor = collectArgumentsForExtractedClassConstructor()
        implementedClassesOrInterfaces = getImplementedClassesOrInterfaces()
        usagesToFix = collectUsagesToFix()
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
            result.getPrimaryConstructorParameterList()?.addParameter(it)
        }

        implementedClassesOrInterfaces.forEach {
            result.addSuperTypeListEntry(it)
        }

        thisClassExtractedDeclarations.forEach {
            result.addDeclaration(it)
        }

        return result
    }

    private fun collectArgumentsForExtractedClassConstructor(): List<KtParameter> {
        val result = mutableListOf<KtParameter>()

        for (declaration in thisClassExtractedDeclarations) {
            if (declaration !is KtProperty || declaration.isVar) {
                continue
            }

            if (declaration.hasDelegateExpressionOrInitializer() || declaration.hasDelegate() || declaration.getter != null) {
                continue
            }

            //TODO find initializer and add this value to initializer
        }

        return result
    }

    private fun getImplementedClassesOrInterfaces(): List<KtSuperTypeListEntry> {
        val result = mutableListOf<KtSuperTypeListEntry>()

        for (superType in sourceClass.superTypeListEntries) {
            val superInterface: KtClassOrObject? = null //TODO
            var supported = true

            if (superInterface == null || !superInterface.isInterfaceClass()) {
                continue
            }

            val interfaceDeclarations = superInterface.declarations
            for (sourceDeclaration in sourceClass.declarations) {
                if (sourceDeclaration !is KtCallableDeclaration) {
                    continue
                }

                val descriptor = sourceDeclaration.resolveToDescriptorIfAny()
                if (descriptor == null || descriptor !is CallableMemberDescriptor) {
                    continue
                }

                for (overriddenDescriptor in descriptor.overriddenDescriptors) {
                    if (overriddenDescriptor.findPsi() in interfaceDeclarations) {
                        if (sourceDeclaration !in thisClassExtractedDeclarations) {
                            supported = false
                        }
                    }
                }
            }

            if (supported) {
                result.add(superType)
            }
        }

        return result
    }

    private fun fixUsages() {
        for (usage in usagesToFix) {
            usage.fixUsage()
        }
    }

    private fun inExtractedDeclarations(reference: PsiElement): Boolean {
        return allExtractedDeclaration.any { PsiTreeUtil.isAncestor(it, reference, false) }
    }

    private fun getDelegationFieldName(name: String?): String {
        val thisName = name ?: "companion" //TODO

        val simpleClassName = thisName.substring(thisName.lastIndexOf('.') + 1).decapitalize()
        //TODO

        return simpleClassName
    }

    private fun collectUsagesToFix(): List<FixableUsageInfo> {
        val result = mutableListOf<FixableUsageInfo>()

        for (declaration in thisClassExtractedDeclarations) {
            val scope = GlobalSearchScope.allScope(project)
            val references = ReferencesSearch.search(declaration, scope)

            for (reference in references) {
                val referenceElement = reference.element

                if (inExtractedDeclarations(referenceElement)) {
                    continue
                }

                if (isCompanionObject) {
                    result.add(ReplaceCompanionObjectReference(referenceElement, sourceClass, targetName))
                } else {
                    result.add(AddDelegationFieldQualifier(referenceElement, getDelegationFieldName(targetName)))
                }
            }

            declaration.accept(object : PsiRecursiveElementVisitor() {
                override fun visitElement(element: PsiElement) {
                    if (element is KtReferenceExpression) {
                        val qualifier = element.getQualifiedExpressionForSelector()
                        if (qualifier == null || qualifier is KtThisExpression) {
                            val resolved = element.mainReference.resolve()

                            if (resolved != null && PsiTreeUtil.isAncestor(originalClass, resolved, false)) {
                                if (qualifier == null) {
                                    result.add(AddDelegationFieldQualifier(element, getDelegationFieldName(targetName)))
                                }
                            }
                        }
                    }

                    super.visitElement(element)
                }
            })

            if (leaveDelegates || declaration.hasModifier(KtModifierKeywordToken.keywordModifier("override"))) {
                val delegate = if (isCompanionObject) {
                    targetName ?: ""
                } else {
                    getDelegationFieldName(targetName)
                }

                result.add(MakeDelegate(declaration, delegate, ktFactory))
            } else {
                result.add(RemoveElement(declaration))
            }
        }

        return result
    }
}