package io.github.koollsl.lsl.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import io.github.koollsl.lsl.LslLanguage
import io.github.koollsl.lsl.preprocessor.LslPreprocessorEngine
import io.github.koollsl.lsl.psi.*
import io.github.koollsl.lsl.references.LslReferenceUtils

class LslRedeclaredIdentifierInspection : LocalInspectionTool() {
    override fun getDisplayName(): String = "Redeclared identifier"
    override fun getGroupDisplayName(): String = LslLanguage.INSTANCE.displayName
    override fun isEnabledByDefault(): Boolean = true
    override fun getStaticDescription(): String = getDisplayName()

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        // 1. Fetch file and preprocessor service ONCE per inspection pass
        val file = holder.file
        val preprocessorEngine = file.project.service<LslPreprocessorEngine>()

        return object : LslElementVisitor() {
            override fun visitGlobalVariable(variable: LslGlobalVariable) {
                // 2. Preprocessor check FIRST before evaluating declarations
                if (preprocessorEngine.isDisabledText(file, variable.textRange)) return
                checkNamedElement(variable, holder, file, preprocessorEngine)
            }

            override fun visitStatementVariable(variable: LslStatementVariable) {
                if (preprocessorEngine.isDisabledText(file, variable.textRange)) return
                checkNamedElement(variable, holder, file, preprocessorEngine)
            }

            override fun visitFunction(function: LslFunction) {
                if (preprocessorEngine.isDisabledText(file, function.textRange)) return
                checkNamedElement(function, holder, file, preprocessorEngine)
            }

            override fun visitArgument(argument: LslArgument) {
                if (preprocessorEngine.isDisabledText(file, argument.textRange)) return
                checkNamedElement(argument, holder, file, preprocessorEngine)
            }

            override fun visitStateCustom(stateCustom: LslStateCustom) {
                if (preprocessorEngine.isDisabledText(file, stateCustom.textRange)) return
                checkNamedElement(stateCustom, holder, file, preprocessorEngine)
            }
        }
    }

    private fun checkNamedElement(
        element: LslNamedElement,
        holder: ProblemsHolder,
        file: PsiFile,
        preprocessorEngine: LslPreprocessorEngine
    ) {
        if (element.textRange.isEmpty) return

        val name = element.name ?: return
        val existingIdentifier = LslReferenceUtils.findNamedElement(element, name) ?: return

        if (existingIdentifier == element) {
            return
        }

        // Ignore if the existing declaration is inside a disabled preprocessor block
        if (preprocessorEngine.isDisabledText(file, existingIdentifier.textRange)) {
            return
        }

        // Highlight as an error if it's in the same scope, otherwise a warning
        val highlightType = if (existingIdentifier.parent == element.parent) {
            ProblemHighlightType.GENERIC_ERROR
        } else {
            ProblemHighlightType.WARNING
        }

        holder.registerProblem(
            element,
            "Redeclared identifier '$name'",
            highlightType,
            element.identifyingElement?.textRangeInParent,
            NavigateToElementFix(existingIdentifier)
        )
    }

    class NavigateToElementFix(element: PsiElement) : LocalQuickFixOnPsiElement(element) {
        override fun startInWriteAction(): Boolean = false

        override fun getText(): String = familyName

        override fun getFamilyName(): String = "Navigate to previous declaration"

        override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
            if (startElement is Navigatable) {
                startElement.navigate(true)
            }
        }
    }
}