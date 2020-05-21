/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.refactoring.extractClass

import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import com.intellij.refactoring.util.DocCommentPolicy
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KotlinMemberInfo
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtTypeParameter

class KotlinExtractClassProcessor(
    private val sourceClass: KtClassOrObject,
    val memberInfos: List<KotlinMemberInfo>,
    private val targetFile: PsiElement,
    private val targetClassName: String,
    private val targetPackage: String,
    private val leaveDelegates: Boolean,
    val docPolicy: DocCommentPolicy<*>  //TODO realise docPolicy usage (or remove)
) {
    //TODO conflicts checking

    private val project = sourceClass.project

    private val extractedMemberDeclarations: List<KtDeclaration> = memberInfos.map { info -> info.member }

    private val KtDeclaration.inCompanionObject: Boolean
        get() {
            val parent = parentOfType<KtClassOrObject>()
            return parent != sourceClass && parent is KtObjectDeclaration && parent.isCompanion()
        }

    private lateinit var extractedClassBuilder: ExtractedClassBuilder
    private var companionObjectBuilder: ExtractedClassBuilder? = null

    private fun collectDataForBuilding() {
        val companionObjectExtractedDeclarations = extractedMemberDeclarations.filter { it.inCompanionObject }

        companionObjectBuilder =
            if (companionObjectExtractedDeclarations.isNotEmpty() && sourceClass.companionObjects.isNotEmpty()) {
                val companionObject = sourceClass.companionObjects[0]

                ExtractedClassBuilder.analyzeContextAndGetBuilder(
                    companionObject,
                    true,
                    companionObjectExtractedDeclarations,
                    extractedMemberDeclarations,
                    listOf(),
                    companionObject.name,
                    targetPackage,
                    leaveDelegates
                )
            } else {
                null
            }

        val extractedClassTypeParameters = collectTypeParameters()
        val sourceExtractedDeclarations = extractedMemberDeclarations.filter { !it.inCompanionObject }

        extractedClassBuilder =
            ExtractedClassBuilder.analyzeContextAndGetBuilder(
                sourceClass,
                false,
                sourceExtractedDeclarations,
                extractedMemberDeclarations,
                extractedClassTypeParameters,
                targetClassName,
                targetPackage,
                leaveDelegates
            )
    }

    private fun collectTypeParameters(): List<KtTypeParameter> {
        //TODO
        return listOf()
    }

    private fun applyRefactoring(extractedClassBuilder: ExtractedClassBuilder, companionObjectBuilder: ExtractedClassBuilder? = null) {
        val extractedClass = extractedClassBuilder.createExtractedClass()

        if (companionObjectBuilder != null) {
            val companionObject = companionObjectBuilder.createExtractedClass()
            extractedClass.addDeclaration(companionObject)
        }

        targetFile.add(extractedClass)
    }

    fun run() {
        //TODO
        project.executeWriteCommand("name") {
        }
    }
}