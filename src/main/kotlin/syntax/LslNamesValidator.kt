package io.github.koollsl.lsl.syntax

import io.github.koollsl.lsl.definitions.LslDefinitionData
import com.intellij.lang.refactoring.NamesValidator
import com.intellij.openapi.project.Project

class LslNamesValidator : NamesValidator {
    companion object {
        private val identifierRegex = Regex("^[a-zA-Z][a-zA-Z0-9_]*$")
    }

    override fun isKeyword(name: String, project: Project): Boolean =
        listOf("default", "state", "jump", "return", "if", "else", "while", "do", "for", "print")
            .plus(LslDefinitionData.getInstance(project).events.keys)
            .contains(name)

    override fun isIdentifier(name: String, project: Project): Boolean =
        identifierRegex.matches(name) && !isKeyword(name, project)
}