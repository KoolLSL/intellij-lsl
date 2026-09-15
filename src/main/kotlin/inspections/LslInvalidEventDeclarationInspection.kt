package io.github.koollsl.lsl.inspections

import KwdbData
import LslLanguage
import LslPrimitiveType
import com.intellij.codeInsight.intention.FileModifier
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.endOffset
import com.intellij.psi.util.startOffset
import io.github.koollsl.lsl.parser.LslTypes
import io.github.koollsl.lsl.preprocessor.LslPreprocessorEngine
import io.github.koollsl.lsl.psi.LslArgument
import io.github.koollsl.lsl.psi.LslElementFactory
import io.github.koollsl.lsl.psi.LslElementVisitor
import io.github.koollsl.lsl.psi.LslEvent
import kotlin.math.min

class LslInvalidEventDeclarationInspection : LocalInspectionTool() {
    override fun getDisplayName(): String = "Invalid event declaration"
    override fun getGroupDisplayName(): String = LslLanguage.INSTANCE.displayName
    override fun isEnabledByDefault(): Boolean = true
    override fun getStaticDescription(): String = "Invalid event declaration"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        // 1. Fetch file and services ONCE per inspection pass
        val file = holder.file
        val preprocessorEngine = holder.project.service<LslPreprocessorEngine>()
        val kwdbData = KwdbData.getInstance(holder.project)

        return object : LslElementVisitor() {
            override fun visitEvent(event: LslEvent) {
                // 2. Preprocessor check FIRST before validating event declarations
                if (preprocessorEngine.isDisabledText(file, event.textRange)) return

                val definition = kwdbData.events[event.name]
                if (definition == null) {
                    holder.registerProblem(
                        event,
                        "Unknown event",
                        ProblemHighlightType.ERROR,
                        TextRange(0, event.textLength),
                        RemoveEventFix(event)
                    )
                    return
                }

                val arguments = event.arguments

                if (arguments.isNotEmpty()) {
                    (0 until min(arguments.size, definition.arguments.size)).forEach { i ->
                        val definitionType = definition.arguments[i].lslType
                        val argumentType = arguments[i].lslType

                        if (definitionType.operationTo(argumentType, LslTypes.ASSIGN) == LslPrimitiveType.INVALID) {
                            holder.registerProblem(
                                event,
                                "Type mismatch (expected %s, got %s)".format(definitionType, argumentType),
                                ProblemHighlightType.GENERIC_ERROR,
                                arguments[i].textRangeInParent,
                                ChangeTypeFix(arguments[i], definitionType)
                            )
                        }
                    }
                }

                if (arguments.size < definition.arguments.size) {
                    val missingDefs = definition.arguments.subList(arguments.size, definition.arguments.size)
                    val targetRange = event.parenthesesRightEl?.textRangeInParent
                        ?: TextRange(event.textLength - 1, event.textLength)

                    holder.registerProblem(
                        event,
                        "Wrong arguments count (expected ${definition.arguments.size}, got ${arguments.size})",
                        ProblemHighlightType.GENERIC_ERROR,
                        targetRange,
                        AddMissingArgumentsFix(
                            event,
                            missingDefs.map { "${it.lslType.name.lowercase()} ${it.name}" }
                        )
                    )
                } else if (arguments.size > definition.arguments.size) {
                    val firstExtraArgument = if (definition.arguments.isNotEmpty())
                        arguments[definition.arguments.size]
                    else
                        arguments.first()

                    val firstExtraArgumentComma = event.argumentsEl?.node?.getChildren(null)
                        ?.filter { it.elementType == LslTypes.COMMA }
                        ?.lastOrNull { it.psi.endOffset < firstExtraArgument.startOffset }
                        ?.psi

                    val lastExtraArgument = arguments.last()

                    val startOffset =
                        (firstExtraArgumentComma?.startOffset ?: firstExtraArgument.startOffset) - event.startOffset
                    val endOffset = lastExtraArgument.endOffset - event.startOffset

                    holder.registerProblem(
                        event,
                        "Wrong arguments count (expected ${definition.arguments.size}, got ${arguments.size})",
                        ProblemHighlightType.GENERIC_ERROR,
                        TextRange(startOffset.coerceAtLeast(0), endOffset.coerceAtMost(event.textLength)),
                        RemoveExtraArgumentsFix(
                            firstExtraArgumentComma ?: firstExtraArgument,
                            lastExtraArgument
                        )
                    )
                }
            }
        }
    }

    class RemoveEventFix(event: LslEvent) : LocalQuickFixOnPsiElement(event) {
        override fun getFamilyName(): String = "Remove event"
        override fun getText(): String = familyName

        override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
            startElement.delete()
        }
    }

    class ChangeTypeFix(argument: LslArgument, val type: LslPrimitiveType) : LocalQuickFixOnPsiElement(argument) {
        override fun getFamilyName(): String = "Change type to $type"
        override fun getText(): String = familyName

        override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
            val argument = startElement as? LslArgument ?: return
            argument.typeNameEl?.replace(LslElementFactory.createTypeName(project, type))
        }
    }

    class RemoveExtraArgumentsFix(startElement: PsiElement, endElement: PsiElement) :
        LocalQuickFixOnPsiElement(startElement, endElement) {
        override fun getFamilyName(): String = "Remove extra arguments"
        override fun getText(): String = familyName

        override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
            startElement.parent?.deleteChildRange(startElement, endElement)
        }
    }

    class AddMissingArgumentsFix(
        event: LslEvent,
        @FileModifier.SafeFieldForPreview private val missingArgStrings: List<String>
    ) : LocalQuickFixOnPsiElement(event) {

        override fun getFamilyName(): String = "Add missing arguments"
        override fun getText(): String = familyName

        override fun invoke(
            project: Project,
            file: PsiFile,
            startElement: PsiElement,
            endElement: PsiElement
        ) {
            val event = startElement as? LslEvent ?: return
            val argumentsEl = event.argumentsEl ?: return

            val existingArgsCount = event.arguments.size
            val prefix = if (existingArgsCount > 0) ", " else ""
            val formattedArgs = prefix + missingArgStrings.joinToString(", ")

            val dummyFile = LslElementFactory.createFile(project, "default { dummy($formattedArgs) {} }")
            val dummyEvent = PsiTreeUtil.findChildOfType(dummyFile, LslEvent::class.java) ?: return
            val dummyArgsEl = dummyEvent.argumentsEl ?: return

            val childrenArray = dummyArgsEl.children
            for (i in childrenArray.indices) {
                argumentsEl.add(childrenArray[i])
            }
        }
    }


}