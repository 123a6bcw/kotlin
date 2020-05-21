/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.kotlin.idea.refactoring.extractClass.fixUsage

import com.intellij.refactoring.util.FixableUsageInfo
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtPsiFactory

class MakeDelegate(private val element: KtElement, private val delegate: String, private val ktPsiFactory: KtPsiFactory) :
    FixableUsageInfo(element) {

    override fun fixUsage() {
        if (element is KtFunction) {
            makeFunctionDelegate()
        }
    }

    private fun makeFunctionDelegate() {
        if (element !is KtFunction) {
            return
        }

        element.bodyExpression?.delete()
        element.bodyBlockExpression?.delete()

        val delegation = StringBuilder()
        delegation.append("= ")
        val methodName = element.name
        delegation.append("$delegate.$methodName(")
        delegation.append(element.valueParameters.joinToString(separator = ",", transform = { parameter -> parameter.name!! }))
        delegation.append(");")

        val delegationExpression = ktPsiFactory.createExpression(delegation.toString())
        element.add(delegationExpression)
    }

}