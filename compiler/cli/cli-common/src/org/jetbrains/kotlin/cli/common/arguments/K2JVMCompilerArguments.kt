/*
 * Copyright 2010-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.cli.common.arguments

import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.config.*

class K2JVMCompilerArguments : CommonCompilerArguments() {
    companion object {
        @JvmStatic
        private val serialVersionUID = 0L
    }

    @Argument(value = "-d", valueDescription = "<directory|jar>", description = "Destination for generated class files")
    var destination: String? by NullableStringFreezableVar(null)

    @Argument(
        value = "-classpath",
        shortName = "-cp",
        valueDescription = "<path>",
        description = "List of directories and JAR/ZIP archives to search for user class files")
    var classpath: String? by NullableStringFreezableVar(null)

    @GradleOption(DefaultValues.BooleanFalseDefault::class)
    @Argument(value = "-include-runtime", description = "Include Kotlin runtime into the resulting JAR")
    var includeRuntime: Boolean by FreezableVar(false)

    @GradleOption(DefaultValues.StringNullDefault::class)
    @Argument(
        value = "-jdk-home",
        valueDescription = "<path>",
        description = "Include a custom JDK from the specified location into the classpath instead of the default JAVA_HOME"
    )
    var jdkHome: String? by NullableStringFreezableVar(null)

    @GradleOption(DefaultValues.BooleanFalseDefault::class)
    @Argument(value = "-no-jdk", description = "Don't automatically include the Java runtime into the classpath")
    var noJdk: Boolean by FreezableVar(false)

    @GradleOption(DefaultValues.BooleanTrueDefault::class)
    @Argument(value = "-no-stdlib", description = "Don't automatically include the Kotlin/JVM stdlib and Kotlin reflection into the classpath")
    var noStdlib: Boolean by FreezableVar(false)

    @GradleOption(DefaultValues.BooleanTrueDefault::class)
    @Argument(value = "-no-reflect", description = "Don't automatically include Kotlin reflection into the classpath")
    var noReflect: Boolean by FreezableVar(false)

    @Argument(
        value = "-expression",
        shortName = "-e",
        description = "Evaluate the given string as a Kotlin script"
    )
    var expression: String? by FreezableVar(null)

    @Argument(
        value = "-script-templates",
        valueDescription = "<fully qualified class name[,]>",
        description = "Script definition template classes"
    )
    var scriptTemplates: Array<String>? by FreezableVar(null)

    @Argument(value = "-module-name", valueDescription = "<name>", description = "Name of the generated .kotlin_module file")
    var moduleName: String? by NullableStringFreezableVar(null)

    @GradleOption(DefaultValues.JvmTargetVersions::class)
    @Argument(
        value = "-jvm-target",
        valueDescription = "<version>",
        description = "Target version of the generated JVM bytecode (1.6, 1.8, 9, 10, 11, 12, 13 or 14), default is 1.6"
    )
    var jvmTarget: String? by NullableStringFreezableVar(JvmTarget.DEFAULT.description)

    @GradleOption(DefaultValues.BooleanFalseDefault::class)
    @Argument(value = "-java-parameters", description = "Generate metadata for Java 1.8 reflection on method parameters")
    var javaParameters: Boolean by FreezableVar(false)

    // Advanced options

    @GradleOption(DefaultValues.BooleanFalseDefault::class)
    @Argument(value = "-Xuse-ir", description = "Use the IR backend")
    var useIR: Boolean by FreezableVar(false)

    @Argument(value = "-Xno-use-ir", description = "Do not use the IR backend. Useful for a custom-built compiler where IR backend is enabled by default")
    var noUseIR: Boolean by FreezableVar(false)

    @Argument(
        value = "-Xir-check-local-names",
        description = "Check that names of local classes and anonymous objects are the same in the IR backend as in the old backend"
    )
    var irCheckLocalNames: Boolean by FreezableVar(false)

    @Argument(
        value = "-Xallow-jvm-ir-dependencies",
        description = "When not using the IR backend, do not report errors on those classes in dependencies, " +
                "which were compiled by the IR backend"
    )
    var allowJvmIrDependencies: Boolean by FreezableVar(false)

    @Argument(
        value = "-Xir-binary-with-stable-abi",
        description = "When using the IR backend, produce binaries which can be read by non-IR backend.\n" +
                "The author is responsible for verifying that the resulting binaries do indeed have the correct ABI"
    )
    var isIrWithStableAbi: Boolean by FreezableVar(false)

    @Argument(value = "-Xmodule-path", valueDescription = "<path>", description = "Paths where to find Java 9+ modules")
    var javaModulePath: String? by NullableStringFreezableVar(null)

    @Argument(
        value = "-Xadd-modules",
        valueDescription = "<module[,]>",
        description = "Root modules to resolve in addition to the initial modules,\n" +
                "or all modules on the module path if <module> is ALL-MODULE-PATH"
    )
    var additionalJavaModules: Array<String>? by FreezableVar(null)

    @Argument(value = "-Xno-call-assertions", description = "Don't generate not-null assertions for arguments of platform types")
    var noCallAssertions: Boolean by FreezableVar(false)

    @Argument(
        value = "-Xno-receiver-assertions",
        description = "Don't generate not-null assertion for extension receiver arguments of platform types"
    )
    var noReceiverAssertions: Boolean by FreezableVar(false)

    @Argument(
        value = "-Xno-param-assertions",
        description = "Don't generate not-null assertions on parameters of methods accessible from Java"
    )
    var noParamAssertions: Boolean by FreezableVar(false)

    @Argument(value = "-Xstrict-java-nullability-assertions", description = "Generate nullability assertions for non-null Java expressions")
    var strictJavaNullabilityAssertions: Boolean by FreezableVar(false)

    @Argument(value = "-Xno-optimize", description = "Disable optimizations")
    var noOptimize: Boolean by FreezableVar(false)

    @Argument(
        value = "-Xnormalize-constructor-calls",
        valueDescription = "{disable|enable}",
        description = "Normalize constructor calls (disable: don't normalize; enable: normalize),\n" +
                "default is 'disable' in language version 1.2 and below,\n" +
                "'enable' since language version 1.3"
    )
    var constructorCallNormalizationMode: String? by NullableStringFreezableVar(null)

    @Argument(
        value = "-Xassertions", valueDescription = "{always-enable|always-disable|jvm|legacy}",
        description = "Assert calls behaviour\n" +
                "-Xassertions=always-enable:  enable, ignore jvm assertion settings;\n" +
                "-Xassertions=always-disable: disable, ignore jvm assertion settings;\n" +
                "-Xassertions=jvm:            enable, depend on jvm assertion settings;\n" +
                "-Xassertions=legacy:         calculate condition on each call, check depends on jvm assertion settings in the kotlin package;\n" +
                "default: legacy"
    )
    var assertionsMode: String? by NullableStringFreezableVar(JVMAssertionsMode.DEFAULT.description)

    @Argument(
        value = "-Xbuild-file",
        deprecatedName = "-module",
        valueDescription = "<path>",
        description = "Path to the .xml build file to compile"
    )
    var buildFile: String? by NullableStringFreezableVar(null)

    @Argument(value = "-Xmultifile-parts-inherit", description = "Compile multifile classes as a hierarchy of parts and facade")
    var inheritMultifileParts: Boolean by FreezableVar(false)

    @Argument(value = "-Xuse-type-table", description = "Use type table in metadata serialization")
    var useTypeTable: Boolean by FreezableVar(false)

    @Argument(
        value = "-Xskip-runtime-version-check",
        description = "Allow Kotlin runtime libraries of incompatible versions in the classpath"
    )
    var skipRuntimeVersionCheck: Boolean by FreezableVar(false)

    @Argument(
        value = "-Xuse-old-class-files-reading",
        description = "Use old class files reading implementation. This may slow down the build and cause problems with Groovy interop.\n" +
                "Should be used in case of problems with the new implementation"
    )
    var useOldClassFilesReading: Boolean by FreezableVar(false)

    @Argument(
        value = "-Xdump-declarations-to",
        valueDescription = "<path>",
        description = "Path to JSON file to dump Java to Kotlin declaration mappings"
    )
    var declarationsOutputPath: String? by NullableStringFreezableVar(null)

    @Argument(value = "-Xsingle-module", description = "Combine modules for source files and binary dependencies into a single module")
    var singleModule: Boolean by FreezableVar(false)

    @Argument(
        value = "-Xsuppress-missing-builtins-error",
        description = "Suppress the \"cannot access built-in declaration\" error (useful with -no-stdlib)"
    )
    var suppressMissingBuiltinsError: Boolean by FreezableVar(false)

    @Argument(
        value = "-Xscript-resolver-environment",
        valueDescription = "<key=value[,]>",
        description = "Script resolver environment in key-value pairs (the value could be quoted and escaped)"
    )
    var scriptResolverEnvironment: Array<String>? by FreezableVar(null)

    // Javac options
    @Argument(value = "-Xuse-javac", description = "Use javac for Java source and class files analysis")
    var useJavac: Boolean by FreezableVar(false)

    @Argument(value = "-Xcompile-java", description = "Reuse javac analysis and compile Java source files")
    var compileJava by FreezableVar(false)

    @Argument(
        value = "-Xjavac-arguments",
        valueDescription = "<option[,]>",
        description = "Java compiler arguments"
    )
    var javacArguments: Array<String>? by FreezableVar(null)


    @Argument(
        value = "-Xjava-source-roots",
        valueDescription = "<path>",
        description = "Paths to directories with Java source files"
    )
    var javaSourceRoots: Array<String>? by FreezableVar(null)

    @Argument(
        value = "-Xjava-package-prefix",
        description = "Package prefix for Java files"
    )
    var javaPackagePrefix: String? by FreezableVar(null)

    @Argument(
        value = "-Xjsr305",
        deprecatedName = "-Xjsr305-annotations",
        valueDescription = "{ignore/strict/warn}" +
                "|under-migration:{ignore/strict/warn}" +
                "|@<fq.name>:{ignore/strict/warn}",
        description = "Specify behavior for JSR-305 nullability annotations:\n" +
                "-Xjsr305={ignore/strict/warn}                   globally (all non-@UnderMigration annotations)\n" +
                "-Xjsr305=under-migration:{ignore/strict/warn}   all @UnderMigration annotations\n" +
                "-Xjsr305=@<fq.name>:{ignore/strict/warn}        annotation with the given fully qualified class name\n" +
                "Modes:\n" +
                "  * ignore\n" +
                "  * strict (experimental; treat as other supported nullability annotations)\n" +
                "  * warn (report a warning)"
    )
    var jsr305: Array<String>? by FreezableVar(null)

    @Argument(
        value = "-Xsupport-compatqual-checker-framework-annotations",
        valueDescription = "enable|disable",
        description = "Specify behavior for Checker Framework compatqual annotations (NullableDecl/NonNullDecl).\n" +
                "Default value is 'enable'"
    )
    var supportCompatqualCheckerFrameworkAnnotations: String? by NullableStringFreezableVar(null)

    @Argument(
        value = "-Xno-exception-on-explicit-equals-for-boxed-null",
        description = "Do not throw NPE on explicit 'equals' call for null receiver of platform boxed primitive type"
    )
    var noExceptionOnExplicitEqualsForBoxedNull by FreezableVar(false)

    @Argument(
        value = "-Xjvm-default",
        valueDescription = "{all|all-compatibility|disable|enable|compatibility}",
        description = """Emit JVM default methods for interface declarations with bodies.
-Xjvm-default=all-compatibility  Generate both a default method in the interface, and a compatibility accessor
                                 in the DefaultImpls class.
                                 In case of inheritance from a Kotlin interface compiled in the old scheme
                                 (DefaultImpls, no default methods), the compatibility accessor in DefaultImpls
                                 will delegate to the DefaultImpls method of the superinterface. Otherwise the
                                 compatibility accessor will invoke the default method on the interface, with
                                 standard JVM runtime resolution semantics.
                                 Note that if interface delegation is used, all interface methods are delegated.
                                 The only exception are methods annotated with the deprecated @JvmDefault annotation.
-Xjvm-default=all                Generate default methods for all interface declarations with bodies.
                                 Do not generate DefaultImpls classes at all.
                                 BREAKS BINARY COMPATIBILITY if some client code relies on the presence of
                                 DefaultImpls classes. Also prohibits the produced binaries to be read by Kotlin
                                 compilers earlier than 1.4.
                                 Note that if interface delegation is used, all interface methods are delegated.
                                 The only exception are methods annotated with the deprecated @JvmDefault annotation.
-Xjvm-default=disable            Do not generate JVM default methods and prohibit @JvmDefault annotation usage.
 The following modes are DEPRECATED:
-Xjvm-default=enable             Allow usages of @JvmDefault; only generate the default method
                                 for annotated method in the interface
                                 (annotating an existing method can break binary compatibility)
-Xjvm-default=compatibility      Allow usages of @JvmDefault; generate a compatibility accessor
                                 in the 'DefaultImpls' class in addition to the default interface method"""
    )
    var jvmDefault: String by FreezableVar(JvmDefaultMode.DEFAULT.description)

    @Argument(value = "-Xdisable-standard-script", description = "Disable standard kotlin script support")
    var disableStandardScript: Boolean by FreezableVar(false)

    @Argument(
        value = "-Xgenerate-strict-metadata-version",
        description = "Generate metadata with strict version semantics (see kdoc on Metadata.extraInt)"
    )
    var strictMetadataVersionSemantics: Boolean by FreezableVar(false)

    @Argument(
        value = "-Xsanitize-parentheses",
        description = "Transform '(' and ')' in method names to some other character sequence.\n" +
                "This mode can BREAK BINARY COMPATIBILITY and is only supposed to be used to workaround\n" +
                "problems with parentheses in identifiers on certain platforms"
    )
    var sanitizeParentheses: Boolean by FreezableVar(false)

    @Argument(
        value = "-Xfriend-paths",
        valueDescription = "<path>",
        description = "Paths to output directories for friend modules (whose internals should be visible)"
    )
    var friendPaths: Array<String>? by FreezableVar(null)

    @Argument(
        value = "-Xallow-no-source-files",
        description = "Allow no source files"
    )
    var allowNoSourceFiles: Boolean by FreezableVar(false)

    @Argument(
        value = "-Xemit-jvm-type-annotations",
        description = "Emit JVM type annotations in bytecode"
    )
    var emitJvmTypeAnnotations: Boolean by FreezableVar(false)

    @Argument(
        value = "-Xklib",
        valueDescription = "<path>",
        description = "Paths to cross-platform libraries in .klib format"
    )
    var klibLibraries: String? by NullableStringFreezableVar(null)

    @Argument(
        value = "-Xno-optimized-callable-references",
        description = "Do not use optimized callable reference superclasses available from 1.4"
    )
    var noOptimizedCallableReferences: Boolean by FreezableVar(false)

    @Argument(
        value = "-Xno-kotlin-nothing-value-exception",
        description = "Do not use KotlinNothingValueException available since 1.4"
    )
    var noKotlinNothingValueException: Boolean by FreezableVar(false)

    override fun configureAnalysisFlags(collector: MessageCollector): MutableMap<AnalysisFlag<*>, Any> {
        val result = super.configureAnalysisFlags(collector)
        result[JvmAnalysisFlags.strictMetadataVersionSemantics] = strictMetadataVersionSemantics
        result[JvmAnalysisFlags.jsr305] = Jsr305Parser(collector).parse(
            jsr305,
            supportCompatqualCheckerFrameworkAnnotations
        )
        result[AnalysisFlags.ignoreDataFlowInAssert] = JVMAssertionsMode.fromString(assertionsMode) != JVMAssertionsMode.LEGACY
        JvmDefaultMode.fromStringOrNull(jvmDefault)?.let {
            result[JvmAnalysisFlags.jvmDefaultMode] = it
            if (it == JvmDefaultMode.ENABLE || it == JvmDefaultMode.ENABLE_WITH_DEFAULT_IMPLS) {
                collector.report(
                    CompilerMessageSeverity.WARNING,
                    "'-Xjvm-default=$jvmDefault' mode is deprecated. Please considering to switch to new modes: 'all' and 'all-compatibility'"
                )
            }
        } ?: collector.report(
            CompilerMessageSeverity.ERROR,
            "Unknown @JvmDefault mode: $jvmDefault, " +
                    "supported modes: ${JvmDefaultMode.values().map { it.description }}"
        )
        result[JvmAnalysisFlags.inheritMultifileParts] = inheritMultifileParts
        result[JvmAnalysisFlags.sanitizeParentheses] = sanitizeParentheses
        result[JvmAnalysisFlags.suppressMissingBuiltinsError] = suppressMissingBuiltinsError
        result[JvmAnalysisFlags.irCheckLocalNames] = irCheckLocalNames
        result[AnalysisFlags.reportErrorsOnIrDependencies] = !useIR && !useFir && !allowJvmIrDependencies
        return result
    }

    override fun configureLanguageFeatures(collector: MessageCollector): MutableMap<LanguageFeature, LanguageFeature.State> {
        val result = super.configureLanguageFeatures(collector)
        if (strictJavaNullabilityAssertions) {
            result[LanguageFeature.StrictJavaNullabilityAssertions] = LanguageFeature.State.ENABLED
        }
        return result
    }

    override fun checkIrSupport(languageVersionSettings: LanguageVersionSettings, collector: MessageCollector) {
        if (!useIR) return

        if (languageVersionSettings.languageVersion < LanguageVersion.KOTLIN_1_3
            || languageVersionSettings.apiVersion < ApiVersion.KOTLIN_1_3
        ) {
            collector.report(
                CompilerMessageSeverity.STRONG_WARNING,
                "IR backend does not support language or API version lower than 1.3. " +
                        "This can lead to unexpected behavior or compilation failures"
            )
        }
    }
}
