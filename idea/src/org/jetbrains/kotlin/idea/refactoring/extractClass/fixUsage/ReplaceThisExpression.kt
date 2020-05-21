/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.refactoring.extractClass.fixUsage

import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.refactoring.util.FixableUsageInfo
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.idea.core.formatter.KotlinCodeStyleSettings
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtThisExpression

class ReplaceThisExpression(private val element: KtThisExpression, private val ktFactory: KtPsiFactory) : FixableUsageInfo(element) {

    override fun fixUsage() {
        element.mainReference?.resolve() //TODO
    }

}