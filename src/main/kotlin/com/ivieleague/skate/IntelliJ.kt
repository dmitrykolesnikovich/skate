package com.ivieleague.skate

import org.redundent.kotlin.xml.Node
import java.io.File

object IntelliJ {

    /*

    PLAN for IntelliJ Editing

    - Create a temp folder for project
    - Set up project structure
    - Hard link relevant files into project folders

    */

    fun launch(withFileOrFolder: File) {
        val osName = System.getProperty("os.name")
        val executable = when {
            osName.contains("win", true) -> File("C:\\Program Files\\JetBrains")
                .takeIf { it.exists() }
                ?.listFiles()?.asSequence()
                ?.filter { it.isDirectory }
                ?.sortedByDescending { it.name }
                ?.firstOrNull()
                ?.resolve("bin/idea64.exe")
            else -> null
        }?.absolutePath ?: "idea"
        println("Launching IntelliJ using: $executable \"$withFileOrFolder\"")
        ProcessBuilder().command(executable, withFileOrFolder.toString()).start()
    }

    fun singleModuleProject(
        sources: List<File>,
        libraries: List<Library>,
        folder: File,
        mainClass: String? = null
    ): File {
        folder.mkdirs()
        folder.listFiles()?.forEach { it.deleteRecursively() }
        val srcFolder = folder.resolve("src").also { it.mkdirs() }
        for (file in sources) {
            srcFolder.resolve(file.name)
                .let {
                    var current = it
                    var num = 2
                    while (current.exists()) {
                        current = srcFolder.resolve(file.nameWithoutExtension + num.toString() + "." + file.extension)
                        num++
                    }
                    current
                }
                .createSymbolicLinkTo(file)
        }
        val moduleFile = folder.resolve("project.iml")
        moduleFile.writeText(moduleFile(folder, listOf(srcFolder), libraries).toString(prettyFormat = true))
        val ideaFolder = folder.resolve(".idea").also { it.mkdirs() }
        ideaFolder.resolve("kotlinc.xml").writeText(kotlincFile().toString(prettyFormat = true))
        ideaFolder.resolve("modules.xml")
            .writeText(modulesFile(folder, listOf(moduleFile)).toString(prettyFormat = true))
        ideaFolder.resolve("misc.xml").writeText(miscFile(folder).toString(prettyFormat = true))
        mainClass?.let {
            ideaFolder.resolve("workspace.xml").writeText(workspaceFile(it).toString(prettyFormat = true))
        }
        val libFolder = ideaFolder.resolve("libraries")
        libFolder.mkdirs()
        for (lib in libraries) {
            libFolder.resolve(lib.fileSafeName + ".xml")
                .writeText(libraryTableFile(listOf(lib)).toString(prettyFormat = true))
        }
        return folder
    }

    fun modulesFile(projectRoot: File, modules: List<File>): Node = Node("project").apply {
        includeXmlProlog = true
        attributes["version"] = "4"
        "component"("name" to "ProjectModuleManager") {
            "modules"() {
                for (module in modules) {
                    val rel = module.relativeTo(projectRoot).invariantSeparatorsPath
                    "module"(
                        "fileurl" to "file://${module.invariantSeparatorsPath}",
                        "filepath" to module.invariantSeparatorsPath
                    )
                }
            }
        }
    }

    fun workspaceFile(mainClass: String): Node = Node("project").apply {
        includeXmlProlog = true
        attributes["version"] = "4"
        "component"("name" to "RunManager") {
            "configuration"("name" to "Run", "type" to "JetRunConfigurationType", "factoryName" to "Kotlin") {
                "module"("name" to "project")
                "option"("name" to "VM_PARAMETERS")
                "option"("name" to "PROGRAM_PARAMETERS")
                "option"("name" to "ALTERNATIVE_JRE_PATH_ENABLED")
                "option"("name" to "ALTERNATIVE_JRE_PATH")
                "option"("name" to "PASS_PARENT_ENVS", "value" to "true")
                "option"("name" to "MAIN_CLASS_NAME", "value" to mainClass)
                "option"("name" to "WORKING_DIRECTORY")
                "method"("v" to "2") {
                    "option"("name" to "Make", "enabled" to "true")
                }
            }
        }
    }

    fun kotlincFile(): Node = Node("project").apply {
        includeXmlProlog = true
        attributes["version"] = "4"
        "component"("name" to "Kotlin2JvmCompilerArguments") {
            "option"("name" to "jvmTarget", "value" to "1.8")
        }
        "component"("name" to "KotlinCommonCompilerArguments") {
            "option"("name" to "apiVersion", "value" to "1.3")
            "option"("name" to "languageVersion", "value" to "1.3")
        }
    }

    fun miscFile(projectRoot: File): Node = Node("project").apply {
        includeXmlProlog = true
        attributes["version"] = "4"
        "component"(
            "name" to "ProjectRootManager",
            "version" to "2",
            "languageLevel" to "JDK_12",
            "default" to "true",
            "project-jdk-name" to "12",
            "project-jdk-type" to "JavaSDK"
        ) {
            "output"("url" to "file://${projectRoot.invariantSeparatorsPath}/out")
        }
    }

    fun libraryTableFile(libraries: List<Library>): Node = Node("component").apply {
        attributes["name"] = "libraryTable"
        for (lib in libraries) {
            addNode(library(lib))
        }
    }

    fun library(library: Library): Node = Node("library").apply {
        attributes["name"] = library.fileSafeName
        "CLASSES" {
            "root"("url" to "jar://${library.default.invariantSeparatorsPath}!/")
        }
        "JAVADOC" {
            if (library.javadoc != null) {
                "root"("url" to "jar://${library.javadoc.invariantSeparatorsPath}!/")
            }
        }
        "SOURCES" {
            if (library.sources != null) {
                "root"("url" to "jar://${library.sources.invariantSeparatorsPath}!/")
            }
        }
    }

    fun moduleFile(projectRoot: File, sourceDirectories: List<File>, libraries: List<Library>): Node {
        return Node("module").apply {
            includeXmlProlog = true
            attributes["type"] = "JAVA_MODULE"
            attributes["version"] = "4"
            "component"("name" to "NewModuleRootManager", "inherit-compiler-output" to "true") {
                "exclude-output"()
                "content"("url" to "file://${projectRoot.invariantSeparatorsPath}") {
                    for (src in sourceDirectories) {
                        val rel = src.relativeTo(projectRoot).invariantSeparatorsPath
                        "sourceFolder"("url" to "file://${src.invariantSeparatorsPath}", "isTestSource" to "false")
                    }
                }
                "orderEntry"("type" to "inheritedJdk")
                "orderEntry"("type" to "sourceFolder", "forTests" to "false")
                for (lib in libraries) {
                    addNode(orderEntryLibraryReference(lib))
                }
            }
        }
    }

    fun orderEntryLibraryReference(library: Library): Node = Node("orderEntry").apply {
        attributes["type"] = "library"
        attributes["level"] = "project"
        attributes["name"] = library.fileSafeName
    }
}
