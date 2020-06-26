/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.refactoring.extractClass

import com.intellij.psi.util.parentOfType
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.memberPullUp.PullUpProcessor
import org.jetbrains.kotlin.idea.codeInsight.shorten.performDelayedRefactoringRequests
import org.jetbrains.kotlin.idea.core.util.runSynchronouslyWithProgress
import org.jetbrains.kotlin.idea.refactoring.introduce.extractClass.KotlinExtractSuperclassHandler
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KotlinMemberInfo
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.jetbrains.kotlin.psi.*

class KotlinExtractClassProcessor(
    private val sourceClass: KtClassOrObject,
    val memberInfos: List<KotlinMemberInfo>,
    private val targetClassName: String,
    private val targetPackage: String,
    private val createInTheSameFile: Boolean,
    private val leaveDelegates: Boolean,
    private val fixVisibilities: Boolean,
    //TODO realise docPolicy usage (or remove)
) {
    //TODO conflicts checking

    private val project = sourceClass.project

    private val extractedMemberDeclarations: List<KtDeclaration> = memberInfos.map { info -> info.member }

    private val targetFile =
        if (createInTheSameFile) {
            sourceClass.containingKtFile
        } else {
            KtPsiFactory(project).createFile(targetClassName, "")
        }

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
                    companionObject.name ?: "companion", //TODO companion
                    targetPackage,
                    leaveDelegates,
                    fixVisibilities,
                    createInTheSameFile
                )
            } else {
                null
            }

        val sourceExtractedDeclarations = extractedMemberDeclarations.filter { !it.inCompanionObject }

        extractedClassBuilder =
            ExtractedClassBuilder.analyzeContextAndGetBuilder(
                sourceClass,
                false,
                sourceExtractedDeclarations,
                extractedMemberDeclarations,
                targetClassName,
                targetPackage,
                leaveDelegates,
                fixVisibilities,
                createInTheSameFile
            )
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
        project.runSynchronouslyWithProgress(
            RefactoringBundle.message("progress.text"),
            true
        ) { runReadAction { collectDataForBuilding() } }

        project.executeWriteCommand(KotlinExtractSuperclassHandler.REFACTORING_NAME) {
            applyRefactoring(extractedClassBuilder, companionObjectBuilder)
            performDelayedRefactoringRequests(project)
        }
    }
}