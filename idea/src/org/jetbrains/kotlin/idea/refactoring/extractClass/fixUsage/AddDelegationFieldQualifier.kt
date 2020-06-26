/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.refactoring.extractClass.fixUsage

import com.intellij.psi.PsiElement
import com.intellij.refactoring.util.FixableUsageInfo
import org.jetbrains.kotlin.psi.KtPsiFactory

class AddDelegationFieldQualifier(private val reference: PsiElement, private val delegationFieldName: String) :
    FixableUsageInfo(reference) {
    override fun fixUsage() {
        val ktFactory = KtPsiFactory(reference.project)

        val parent = reference.parent
        val updatedReference = StringBuilder()
        for (child in parent.children) {
            if (child == reference) {
                updatedReference.append("$delegationFieldName.${reference.text}")
            } else {
                updatedReference.append(child.text)
            }
        }

        reference.replace(ktFactory.createExpression(updatedReference.toString()))
    }

}
