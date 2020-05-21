/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.refactoring.extractClass.fixUsage

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.refactoring.util.FixableUsageInfo
import org.jetbrains.kotlin.psi.KtClassOrObject

class ReplaceCompanionObjectReference(reference: PsiElement, sourceClass: KtClassOrObject, targetName: String?) : FixableUsageInfo(reference) {
    override fun fixUsage() {
        //TODO
    }

}
