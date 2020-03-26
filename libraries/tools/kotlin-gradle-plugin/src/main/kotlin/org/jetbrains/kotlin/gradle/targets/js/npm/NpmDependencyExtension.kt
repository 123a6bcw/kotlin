/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.js.npm

import groovy.lang.Closure
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.reflect.TypeOf
import java.io.File

interface NpmDependencyExtension {
    operator fun invoke(name: String, version: String = "*"): NpmDependency

    operator fun invoke(name: String, directory: File): NpmDependency
}

fun Project.addNpmDependencyExtension() {
    val dependencies = this.dependencies as ExtensionAware

    val npmDependencyExtension: NpmDependencyExtension = object : NpmDependencyExtension, Closure<NpmDependency>(dependencies) {
        override operator fun invoke(name: String, version: String): NpmDependency =
            NpmDependency(
                project = this@addNpmDependencyExtension,
                name = name,
                version = version
            )

        override operator fun invoke(name: String, directory: File): NpmDependency =
            NpmDependency(
                project = this@addNpmDependencyExtension,
                name = name,
                directory = directory
            )

        override fun call(vararg args: Any?): NpmDependency {
            val size = args.size
            if (size > 2) throw npmDeclarationException(args)

            val name = args[0] as String
            val arg = if (size > 1) args[1] else null

            return when (arg) {
                null -> invoke(
                    name = name
                )
                is String -> invoke(
                    name = name,
                    version = arg
                )
                is File -> invoke(
                    name = name,
                    directory = arg
                )
                else -> throw npmDeclarationException(args)
            }
        }

        private fun npmDeclarationException(args: Array<out Any?>): IllegalArgumentException {
            return IllegalArgumentException(
                """
                            Unable to add NPM dependency by $args
                            - npm('name') -> name:*
                            - npm('name', 'version') -> name:version
                            - npm('name', File) -> name:File
                            """.trimIndent()
            )
        }
    }

    dependencies
        .extensions
        .add(
            TypeOf.typeOf<NpmDependencyExtension>(NpmDependencyExtension::class.java),
            "npm",
            npmDependencyExtension
        )
}