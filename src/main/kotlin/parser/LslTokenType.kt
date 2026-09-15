package io.github.koollsl.lsl.parser

import LslLanguage
import com.intellij.psi.tree.IElementType
import org.jetbrains.annotations.NonNls

class LslTokenType(debugName: @NonNls String) : IElementType(debugName, LslLanguage.INSTANCE) {
}