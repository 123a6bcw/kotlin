/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.refactoring.extractClass

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.refactoring.introduce.extractClass.KotlinExtractSuperHandlerBase
import org.jetbrains.kotlin.idea.refactoring.introduce.extractClass.ui.KotlinExtractSuperclassDialog
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject

object KotlinExtractClassHandler : KotlinExtractSuperHandlerBase(false) {
    val REFACTORING_NAME = KotlinBundle.message("name.extract.class")

    override fun getErrorMessage(klass: KtClassOrObject): String? {
        val superMessage = super.getErrorMessage(klass)
        if (superMessage != null) return superMessage
        if (klass is KtClass && klass.isAnnotation()) return KotlinBundle.message("error.text.interface.cannot.be.extracted.from.an.annotation.class")
        return null
    }

    override fun createDialog(klass: KtClassOrObject, targetParent: PsiElement) =
        KotlinExtractSuperclassDialog(
            originalClass = klass,
            targetParent = targetParent,
            conflictChecker = { checkConflicts(klass, it) }
        )
}