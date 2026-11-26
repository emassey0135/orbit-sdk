package dev.emassey0135.orbit.ui

import dev.emassey0135.orbit.OrbitWords
import dev.emassey0135.orbit.Reader

/**
 * A [Reader] that tells [base] everything, and hands [show] the same as text for a sighted helper:
 * the Speak and the Player have no screen, so what Orbit voices is seen only on a mirror or in a
 * screenshot, where an app may draw this. Each description is its parts a paragraph apart: the
 * list, dialog or field it is in, the row or control in hand, and its place in [words]' own terms
 * ([OrbitWords.place], a check box's [OrbitWords.checked]), as the reader says them. Give it to
 * [OrbitUi.on] as its `reader`.
 */
public class HelperText(
    private val base: Reader,
    private val words: OrbitWords,
    private val show: (String) -> Unit,
) : Reader by base {
    private fun shown(vararg parts: String) = show(parts.filter { it.isNotEmpty() }.joinToString("\n\n"))

    private fun place(count: Int, selected: Int): String = if (count > 0) words.place(selected + 1, count) else ""

    override fun focusList(name: String, count: Int, selected: Int, item: String) {
        base.focusList(name, count, selected, item)
        shown(name, item, place(count, selected))
    }

    override fun itemChanged(name: String, count: Int, selected: Int, item: String) {
        base.itemChanged(name, count, selected, item)
        shown(name, item, place(count, selected))
    }

    override fun dialog(name: String, message: String, focused: String) {
        base.dialog(name, message, focused)
        shown(name, message, focused)
    }

    override fun focusMenu(item: String, subMenu: Boolean, accessKey: Char?, position: Int, total: Int) {
        base.focusMenu(item, subMenu, accessKey, position, total)
        shown(words.contextMenu, if (subMenu) "$item …" else item, place(total, position - 1))
    }

    override fun focusEdit(name: String, content: String, cursor: Int, line: String, multiline: Boolean) {
        base.focusEdit(name, content, cursor, line, multiline)
        shown(name, content)
    }

    override fun editChanged(fieldName: String, content: String, cursor: Int, changeType: String) {
        base.editChanged(fieldName, content, cursor, changeType)
        shown(fieldName, content)
    }

    override fun focusControl(name: String, role: Int) {
        base.focusControl(name, role)
        shown(name)
    }

    override fun focusText(content: String) {
        base.focusText(content)
        shown(content)
    }

    override fun focusCheck(name: String, checked: Boolean) {
        base.focusCheck(name, checked)
        shown(name, if (checked) words.checked else words.notChecked)
    }

    override fun focusChoice(name: String, role: Int, count: Int, selected: Int, item: String) {
        base.focusChoice(name, role, count, selected, item)
        shown(name, item, place(count, selected))
    }
}
