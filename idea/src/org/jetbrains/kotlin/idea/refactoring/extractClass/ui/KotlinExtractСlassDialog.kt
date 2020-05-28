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
import com.intellij.openapi.roots.JavaProjectRootsUtil
import com.intellij.refactoring.RefactorJBundle
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.classMembers.AbstractMemberInfoModel
import com.intellij.refactoring.classMembers.MemberInfoChange
import com.intellij.refactoring.move.moveClassesOrPackages.DestinationFolderComboBox
import com.intellij.refactoring.ui.PackageNameReferenceEditorCombo
import com.intellij.refactoring.ui.RefactoringDialog
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.ReferenceEditorComboWithBrowseButton
import com.intellij.ui.components.JBLabelDecorator
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.UIUtil
import org.jetbrains.kotlin.idea.refactoring.extractClass.KotlinExtractClassProcessor
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KotlinMemberInfo
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KotlinMemberSelectionPanel
import org.jetbrains.kotlin.idea.refactoring.memberInfo.extractClassMembers
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import java.awt.BorderLayout
import javax.swing.*
import javax.swing.event.DocumentEvent

class KotlinExtractClassDialog(val sourceClass: KtClassOrObject) : RefactoringDialog(sourceClass.project, true) {
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

    private val fixVisibilities: JCheckBox = JCheckBox("Fix visibilities") //TODO

    private val createInThisFile: JCheckBox = JCheckBox("Create in this file") //TODO

    private val leaveDelegates: JCheckBox = JCheckBox("Leave delegates") //TODO

    private val destinationFolderComboBox = object : DestinationFolderComboBox() {
        override fun getTargetPackage(): String {
            return extractedPackageName
        }
    }

    private val extractMemberInfoModel = object : AbstractMemberInfoModel<KtNamedDeclaration, KotlinMemberInfo>() {
        val memberInfos: List<KotlinMemberInfo> = extractClassMembers(sourceClass)
    }

    private val membersToExtract
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

        super.init()
        validateButtons()
    }

    override fun doAction() {
        val processor = KotlinExtractClassProcessor(
            sourceClass,
            membersToExtract,
            extractedClassName,
            extractedClassPackageField.text,
            createInThisFile.isSelected,
            leaveDelegates.isSelected,
            fixVisibilities.isSelected
        )

        processor.run()
    }

    override fun createNorthPanel(): JComponent? {
        val checkboxPanel = JPanel(BorderLayout())
        checkboxPanel.add(createInThisFile, BorderLayout.WEST)
        checkboxPanel.add(leaveDelegates, BorderLayout.EAST)
        checkboxPanel.add(fixVisibilities, BorderLayout.EAST)

        val builder = FormBuilder.createFormBuilder()
            .addComponent(
                JBLabelDecorator.createJBLabelDecorator(RefactorJBundle.message("extract.class.from.label", sourceClass.getQualifiedName()))
                    .setBold(true)
            )
            .addLabeledComponent(
                RefactorJBundle.message("name.for.new.class.label"),
                extractedClassNameField,
                UIUtil.LARGE_VGAP
            )
            .addLabeledComponent(JLabel(), checkboxPanel)
            .addLabeledComponent(RefactorJBundle.message("package.for.new.class.label"), extractedClassPackageField)

        if (JavaProjectRootsUtil.getSuitableDestinationSourceRoots(myProject).size > 1) {
            builder.addLabeledComponent(RefactoringBundle.message("target.destination.folder"), destinationFolderComboBox)
        }

        return builder.addVerticalGap(5).panel
    }

    override fun createCenterPanel(): JComponent? {
        extractMemberInfoModel.apply {
            memberInfoChanged(MemberInfoChange(memberInfos))
        }

        return JPanel(BorderLayout()).apply {

            val memberSelectionPanel = KotlinMemberSelectionPanel(
                "Extract delegate", //TODO
                extractMemberInfoModel.memberInfos,
                RefactoringBundle.message("make.abstract")
            )

            memberSelectionPanel.table.memberInfoModel = extractMemberInfoModel
            memberSelectionPanel.table.addMemberInfoChangeListener(extractMemberInfoModel)
            add(memberSelectionPanel, BorderLayout.CENTER)
        }
    }

    override fun getPreferredFocusedComponent(): JComponent? {
        return extractedClassNameField
    }

}