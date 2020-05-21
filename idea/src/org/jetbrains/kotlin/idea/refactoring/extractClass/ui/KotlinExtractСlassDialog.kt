/*
 * Copyright 2010-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.idea.refactoring.extractClass.ui

import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.refactoring.RefactorJBundle
import com.intellij.refactoring.classMembers.AbstractMemberInfoModel
import com.intellij.refactoring.move.moveClassesOrPackages.DestinationFolderComboBox
import com.intellij.refactoring.ui.PackageNameReferenceEditorCombo
import com.intellij.refactoring.ui.RefactoringDialog
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.ReferenceEditorComboWithBrowseButton
import org.jetbrains.kotlin.idea.refactoring.extractClass.KotlinExtractClassProcessor
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KotlinMemberInfo
import org.jetbrains.kotlin.idea.refactoring.memberInfo.extractClassMembers
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JTextField
import javax.swing.event.DocumentEvent

class KotlinExtractClassDialog(val sourceClass: KtClassOrObject) : RefactoringDialog(sourceClass.project, true) {
    private val memberInfos: List<KotlinMemberInfo> = listOf()

    private val extractedClassNameField = JTextField()
    private val extractedClassName: String
        get() {
            return extractedClassNameField.text.trim()
        }

    private val extractedClassPackageField: ReferenceEditorComboWithBrowseButton
    private val extractedPackageName: String
        get() {
            return extractedClassPackageField.text.trim()
        }

    private val fixVisibility: JCheckBox by lazy {
        val result = JCheckBox()
        result
    }

    private val createInThisFile: JCheckBox by lazy {
        val result = JCheckBox()
        result
    }

    private val destinationFolderComboBox = object : DestinationFolderComboBox() {
        override fun getTargetPackage(): String {
            return extractedPackageName
        }
    }

    private val extractMemberInfoModel = object : AbstractMemberInfoModel<KtNamedDeclaration, KotlinMemberInfo>() {
        val memberInfos: List<KotlinMemberInfo> = extractClassMembers(sourceClass)
    }

    private val members
        get() = extractMemberInfoModel.memberInfos.filter { it.isChecked }

    init {
        isModal = true
        title = "Extract Delegate" //TODO

        val file = sourceClass.containingFile
        val text = if (file is KtFile) file.packageFqName.asString() else ""

        extractedClassPackageField = PackageNameReferenceEditorCombo(
            text, myProject, "ExtractClass.RECENTS_KEY",
            RefactorJBundle.message("choose.destination.package.label")
        )

        extractedClassPackageField.childComponent.document
            .addDocumentListener(object : DocumentListener {
                override fun documentChanged(e: com.intellij.openapi.editor.event.DocumentEvent) {
                    validateButtons()
                }
            })

        destinationFolderComboBox.setData(
            myProject, sourceClass.containingFile.containingDirectory,
            extractedClassPackageField.childComponent
        )

        extractedClassNameField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                validateButtons()
            }
        })
    }

    override fun doAction() {
        KotlinExtractClassProcessor(sourceClass, memberInfos, targetFile,)
    }

    override fun createCenterPanel(): JComponent? {
        TODO("Not yet implemented")
    }

}