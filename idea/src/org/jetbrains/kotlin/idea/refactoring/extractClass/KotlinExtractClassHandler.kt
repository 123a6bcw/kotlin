/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.refactoring.extractClass

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.HelpID
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.lang.ElementsHandler
import com.intellij.refactoring.util.CommonRefactoringUtil
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.refactoring.SeparateFileWrapper
import org.jetbrains.kotlin.idea.refactoring.chooseContainerElementIfNecessary
import org.jetbrains.kotlin.idea.refactoring.extractClass.ui.KotlinExtractClassDialog
import org.jetbrains.kotlin.idea.refactoring.getExtractionContainers
import org.jetbrains.kotlin.idea.refactoring.showWithTransaction
import org.jetbrains.kotlin.idea.util.isExpectDeclaration
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType

object KotlinExtractClassHandler : RefactoringActionHandler, ElementsHandler {
    val REFACTORING_NAME = KotlinBundle.message("name.extract.class")

    fun createDialog(klass: KtClassOrObject) =
        KotlinExtractClassDialog(klass)

    override fun isEnabledOnElements(elements: Array<out PsiElement>) = elements.singleOrNull() is KtClassOrObject

    override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext?) {
        val offset = editor.caretModel.offset
        val element = file.findElementAt(offset) ?: return
        val klass = element.getNonStrictParentOfType<KtClassOrObject>() ?: return
        if (!checkClass(klass, editor)) return
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
        selectElements(klass, editor)
    }

    override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext?) {
        if (dataContext == null) return
        val editor = CommonDataKeys.EDITOR.getData(dataContext)
        val klass = PsiTreeUtil.findCommonParent(*elements)?.getNonStrictParentOfType<KtClassOrObject>() ?: return
        if (!checkClass(klass, editor)) return
        selectElements(klass, editor)
    }

    private fun checkClass(klass: KtClassOrObject, editor: Editor?): Boolean {
        val project = klass.project

        if (!CommonRefactoringUtil.checkReadOnlyStatus(project, klass)) return false

        getErrorMessage(klass)?.let {
            CommonRefactoringUtil.showErrorHint(
                project,
                editor,
                RefactoringBundle.getCannotRefactorMessage(it),
                REFACTORING_NAME,
                HelpID.ExtractClass
            )
            return false
        }

        return true
    }

    private fun doInvoke(klass: KtClassOrObject) {
        createDialog(klass).showWithTransaction()
    }

    private fun selectElements(klass: KtClassOrObject, editor: Editor?) {
        val containers = klass.getExtractionContainers(strict = true, includeAll = true) + SeparateFileWrapper(klass.manager)

        if (editor == null) return doInvoke(klass)

        chooseContainerElementIfNecessary(
            containers,
            editor,
            if (containers.first() is KtFile)
                KotlinBundle.message("text.select.target.file")
            else
                KotlinBundle.message("text.select.target.code.block.file"),
            true,
            { it },
            { doInvoke(klass) }
        )
    }

    private fun getErrorMessage(klass: KtClassOrObject): String? = when {
        klass.isExpectDeclaration() -> KotlinBundle.message("error.text.extraction.from.expect.class.is.not.yet.supported")
        klass.toLightClass() == null -> KotlinBundle.message("error.text.extraction.from.non.jvm.class.is.not.yet.supported")
        else -> null
    }
}