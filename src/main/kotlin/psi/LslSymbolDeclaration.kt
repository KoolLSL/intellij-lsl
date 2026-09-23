package io.github.koollsl.lsl.psi

import com.intellij.model.Pointer
import com.intellij.model.Symbol
import com.intellij.model.psi.PsiSymbolDeclaration
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.createSmartPointer

interface LslSymbolDeclaration : PsiNameIdentifierOwner, PsiSymbolDeclaration, Symbol {
    override fun getDeclaringElement(): PsiElement = this

    override fun getRangeInDeclaringElement(): TextRange =
        identifyingElement?.textRangeInParent ?: TextRange.EMPTY_RANGE

    // By having the declaration implement Symbol, return 'this' directly
    override fun getSymbol(): Symbol = this

    override fun getOwnDeclarations(): Collection<PsiSymbolDeclaration> = listOf(this)

    // Standard pointer resolution for PSI-backed symbols
    override fun createPointer(): Pointer<out Symbol> {
        val pointer = this.createSmartPointer()
        return Pointer { pointer.element as? Symbol }
    }
}