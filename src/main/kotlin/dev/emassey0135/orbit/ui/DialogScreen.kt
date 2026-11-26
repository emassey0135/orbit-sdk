package dev.emassey0135.orbit.ui

import dev.emassey0135.orbit.EditBox
import dev.emassey0135.orbit.Key
import dev.emassey0135.orbit.ListFinder
import dev.emassey0135.orbit.OrbitDialog
import dev.emassey0135.orbit.OrbitReader

/**
 * One control of a [DialogScreen], as the app holds it now: read afresh each time the dialog is,
 * so a control the app changed underneath (a line completed, a choice the app moved) is told as it
 * now stands.
 */
public sealed class DialogControl(
    /** What names it across a change. */
    public val key: String,
    /** What Orbit names it. */
    public val label: String,
    /** Its own actions, first on the menu key's menu while it is in hand, as a row's are. */
    public val actions: List<Action>,
) {
    /** An edit box holding [value]; several lines when [multiLine], where Dot 8 starts a line. [hint] is said after it. */
    public class Line(
        key: String,
        label: String,
        public val value: String = "",
        public val hint: String = "",
        public val multiLine: Boolean = false,
        actions: List<Action> = emptyList(),
    ) : DialogControl(key, label, actions)

    /**
     * A combo box of [options], [selected] in hand; a list box when [listBox], whose option is
     * picked with Select. On the Player a list box's options are rows of their own, each read as
     * [optionRows] has it (an option with why it is offered).
     */
    public class Choice(
        key: String,
        label: String,
        public val options: List<String>,
        public val selected: Int = 0,
        public val listBox: Boolean = false,
        public val optionRows: List<String> = options,
        actions: List<Action> = emptyList(),
    ) : DialogControl(key, label, actions)

    /** A check box, [checked] or not. */
    public class Check(
        key: String,
        label: String,
        public val checked: Boolean = false,
        actions: List<Action> = emptyList(),
    ) : DialogControl(key, label, actions)

    /** A button; Space (or Dot 8) with [letter] presses it from anywhere in the dialog. */
    public class Button(
        key: String,
        label: String,
        public val letter: Char? = null,
        actions: List<Action> = emptyList(),
    ) : DialogControl(key, label, actions)

    /** A line to read, [value], which holds nothing. */
    public class Text(key: String, public val value: String, actions: List<Action> = emptyList()) : DialogControl(key, "", actions)
}

/**
 * A dialog as Orbit's own are, for whichever device the keys say this is, over controls the app
 * keeps ([controls], [focus]) and changes as the person works them. Subclass it, or use
 * [OrbitUi.form] and [OrbitUi.confirm], which are built on it.
 *
 * On the Speak it is told as a dialog ([prompt]), then each control as it is reached, in its role:
 * Tab and Shift-Tab go round them, each active as soon as it is reached, as in the Editor's Save
 * As, and each key does what [OrbitDialog.does] says. A line types at once in the SDK's [EditBox];
 * Select in a line of one is [lineDone]. The menu key offers the control in hand's own actions.
 *
 * The Player has no Tab, so there the controls are the rows of a list: a line opens an edit box of
 * its own, a combo box a list of its options, a list box's options are rows themselves, a check box
 * turns over with Select, a button presses. Where [lineFills], a dialog that is one line and its
 * buttons, the line is the whole screen and the menu key offers the buttons. A dialog that is a page
 * of settings rather than a question is told as a list there ([toldAsDialog]).
 *
 * Which device the person has is the last key's, so the same dialog serves both. Escape, Space
 * with E or Z, and Dot 7 off a line are [cancelled]; Back is the stack's, through [back].
 */
public abstract class DialogScreen : ListScreen("", { emptyList() }, "") {
    /** What Orbit is told as the dialog opens: its title, then what is read before its first control. */
    public open val prompt: String get() = title

    /** The controls in tab order, as they stand now. */
    public abstract fun controls(): List<DialogControl>

    /** The control in hand, by its place in [controls]. */
    public abstract val focus: Int

    /** The person reached the control at [at]: Tab, Shift-Tab, or a row of it on the Player. */
    protected abstract fun focusOn(at: Int)

    /** [line] typed to [text], the caret at [caret], as it changes. */
    protected open fun typed(line: DialogControl.Line, text: String, caret: Int) {}

    /** Select in a line of one on the Speak, or the whole-screen line confirmed on the Player. */
    protected abstract fun lineDone(line: DialogControl.Line, text: String)

    /** The Player's edit box of [line] confirmed: the line holds [text], nothing pressed. */
    protected abstract fun lineSet(line: DialogControl.Line, text: String)

    /** [choice] moved to [option]; false refuses it, with the error tone. */
    protected abstract fun chose(choice: DialogControl.Choice, option: Int): Boolean

    /** [check] turned over: what it holds now, or null where it was refused. */
    protected abstract fun toggled(check: DialogControl.Check): Boolean?

    /** A button pressed, or a list box's option picked: Select or Dot 8 on it, or its letter. */
    protected abstract fun pressed(control: DialogControl)

    /** Escape, Space with E or Z, or Dot 7 off a line: closed unanswered. */
    protected open fun cancelled(): Unit = ui.close(this)

    /** The next option of [choice] starting with [letter], from the one in hand; null for none. */
    protected open fun findOption(choice: DialogControl.Choice, letter: Char): Int? =
        ListFinder.next(choice.options, choice.selected, letter.toString())

    /** Whether there is something to complete in [line] now, for a line that completes; null for one that never does. */
    protected open fun completing(line: DialogControl.Line): (() -> Boolean)? = null

    /** The completions of [line], asked for with Down: put one in with [putInLine]. */
    protected open fun complete(line: DialogControl.Line) {}

    /** On the Player, whether the dialog's line is the whole screen, its menu its buttons. */
    public open val lineFills: Boolean get() = false

    /** On the Player, whether it is told as a dialog (a question) rather than a list (a page of settings). */
    public open val toldAsDialog: Boolean get() = true

    /** [text] put into the line [key], the caret at [caret]: a completion chosen. */
    public fun putInLine(key: String, text: String, caret: Int) {
        val line = controls().firstOrNull { it.key == key } as? DialogControl.Line ?: return
        editorOf(line).replace(text, caret)
    }

    private val inPlace: Boolean get() = !ui.phoneKeypad

    private val held: DialogControl? get() = controls().getOrNull(focus)

    private var announced = false

    /** The control last told, so a refresh that moved the control in hand tells it and one that did not says nothing. */
    private var toldControl: String? = null

    // ---- the Player: the controls as rows ----------------------------------------------

    /** The dialog's line: the one in hand, else its first. */
    private fun line(): DialogControl.Line? =
        held as? DialogControl.Line ?: controls().firstNotNullOfOrNull { it as? DialogControl.Line }

    /** Whether the Player shows the dialog's line as the whole screen. */
    private val lineShown: Boolean get() = !inPlace && lineFills && line() != null

    override fun items(): List<Item> {
        if (inPlace || lineShown) return emptyList()
        return controls().flatMapIndexed { at, control -> rowsOf(at, control) }
    }

    private fun rowsOf(at: Int, control: DialogControl): List<Item> = when (control) {
        is DialogControl.Line -> listOf(
            Item(control.key, control.label, {
                focusOn(at)
                ui.askLine(control.label, control.value, control.hint, control.multiLine) { typed ->
                    if (typed != null) lineSet(control, typed)
                }
            }, control.actions, detail = control.value.ifEmpty { ui.orbitWords.blank }, title = control.label),
        )
        is DialogControl.Choice -> if (control.listBox) {
            control.options.indices.map { option ->
                Item("${control.key}:$option", control.optionRows.getOrNull(option) ?: control.options[option], {
                    focusOn(at)
                    if (chose(control, option)) pressed(control) else ui.refuse()
                }, control.actions)
            }
        } else {
            listOf(
                Item(control.key, control.label, {
                    focusOn(at)
                    ui.choose(control.label, control.options, control.selected) { option ->
                        if (option != null && !chose(control, option)) ui.refuse()
                    }
                }, control.actions, detail = control.options.getOrNull(control.selected).orEmpty(), title = control.label),
            )
        }
        is DialogControl.Check -> listOf(
            Item(
                control.key, control.label, actions = control.actions,
                detail = if (control.checked) ui.orbitWords.checked else ui.orbitWords.notChecked,
                toggle = {
                    focusOn(at)
                    if (toggled(control) == null) ui.refuse() else refresh(true)
                },
                title = control.label,
            ),
        )
        is DialogControl.Button -> listOf(
            Item(control.key, control.label, {
                focusOn(at)
                pressed(control)
            }, control.actions, control.letter),
        )
        is DialogControl.Text -> listOf(Item(control.key, control.value, actions = control.actions))
    }

    override fun moved(item: Item) {
        val at = controls().indexOfFirst { item.key == it.key || item.key.startsWith(it.key + ":") }
        if (at >= 0 && at != focus) focusOn(at)
    }

    /** The control in hand is the app's: on the Player its row, on the Speak what Orbit is told of. */
    override fun inHand(): String? = held?.key

    override fun inHandActions(): List<Action> = if (inPlace) held?.actions.orEmpty() else super.inHandActions()

    /** On the Player's whole-screen line, the menu is the dialog's buttons. */
    override fun actions(): List<Action> {
        if (!lineShown) return super.actions()
        return controls().filterIsInstance<DialogControl.Button>().map { button ->
            Action(button.label, button.letter) {
                takeLine()
                pressed(button)
            }
        }
    }

    // ---- what Orbit is told ---------------------------------------------------------------

    /**
     * Newly shown. On the Speak the dialog is told once, as it opens, then the control in hand
     * whenever it is reached. On the Player its whole-screen line is told as an edit box; otherwise
     * its rows are a list, told as a dialog first where it is one.
     */
    override fun tellShown() {
        if (inPlace) {
            if (!announced) reader.dialog(prompt, "", "")
            announced = true
            tellControl()
            return
        }
        if (lineShown) return tellLine()
        if (!toldAsDialog) return super.tellShown()
        rebuild()
        if (!announced) reader.dialog(prompt, "", current?.line.orEmpty())
        announced = true
        tellMoved()
    }

    override fun refresh(tell: Boolean) {
        if (inPlace) {
            if (tell && held?.key != toldControl) tellControl()
            return
        }
        // The whole-screen line is told as it opens, its typing echoed as it goes.
        if (lineShown) return
        super.refresh(tell)
    }

    override fun land(key: String?) {
        if (inPlace) return refresh(true)
        if (lineShown) return
        super.land(key)
    }

    /** The control in hand, told to Orbit in its role. */
    private fun tellControl() {
        val control = held ?: return
        toldControl = control.key
        when (control) {
            is DialogControl.Line -> {
                ui.braille?.follow()
                editorOf(control).focus()
                if (control.hint.isNotEmpty()) ui.afterTelling { if (ui.top === this && toldControl == control.key) reader.speakQueued(control.hint) }
            }
            is DialogControl.Choice -> reader.focusChoice(
                control.label, if (control.listBox) OrbitReader.ROLE_LIST_BOX else OrbitReader.ROLE_COMBO_BOX,
                control.options.size, control.selected, control.options.getOrNull(control.selected).orEmpty(),
            )
            is DialogControl.Check -> reader.focusCheck(control.label, control.checked)
            is DialogControl.Button -> reader.focusControl(control.label, OrbitReader.ROLE_BUTTON)
            is DialogControl.Text -> reader.focusText(control.value)
        }
    }

    /** On the Player, the whole-screen line: told as an edit box, its hint after it. */
    private fun tellLine() {
        val line = line() ?: return
        val at = controls().indexOf(line)
        if (at >= 0 && at != focus) focusOn(at)
        ui.braille?.follow()
        editorOf(line).focus()
        // What Orbit is told goes through its event queue, speech does not: the hint waits for the field.
        if (line.hint.isNotEmpty()) ui.afterTelling { if (ui.top === this) reader.speakQueued(line.hint) }
    }

    // ---- the keys -----------------------------------------------------------------------

    override fun key(key: Key): Boolean {
        if (!inPlace) {
            if (!lineShown) {
                // Left never leaves a dialog, on the Player either, whose Back does.
                return key == Key.Left || super.key(key)
            }
            val line = line() ?: return false
            // The whole-screen line's menu is the dialog's buttons; every other key is the line's.
            if (key == Key.Menu) return false
            editorOf(line).key(key)
            return true
        }
        val control = held
        if (control == null) {
            return when (OrbitDialog.does(key, OrbitDialog.Control.TEXT)) {
                OrbitDialog.Does.Next -> moveFocus(1)
                OrbitDialog.Does.Previous -> moveFocus(-1)
                OrbitDialog.Does.Cancel -> if (key == Key.Escape) true.also { cancelled() } else false
                else -> false
            }
        }
        when (val does = OrbitDialog.does(key, kindOf(control))) {
            OrbitDialog.Does.Next -> return moveFocus(1)
            OrbitDialog.Does.Previous -> return moveFocus(-1)
            OrbitDialog.Does.Cancel -> cancelled()
            OrbitDialog.Does.NotTaken -> return false
            OrbitDialog.Does.Nothing -> {}
            OrbitDialog.Does.Edit -> return control is DialogControl.Line && editorOf(control).key(key)
            // A combo box holds what it was moved to already: Select presses a button or picks a list box's option.
            OrbitDialog.Does.Press -> if (control is DialogControl.Button || (control is DialogControl.Choice && control.listBox)) {
                takeLine()
                pressed(control)
            }
            OrbitDialog.Does.Toggle -> if (control is DialogControl.Check) {
                val now = toggled(control)
                if (now == null) ui.refuse() else reader.focusCheck(control.label, now)
            }
            is OrbitDialog.Does.Step -> if (control is DialogControl.Choice) choose(control, control.selected + does.by)
            OrbitDialog.Does.First -> if (control is DialogControl.Choice) choose(control, 0)
            OrbitDialog.Does.Last -> if (control is DialogControl.Choice) choose(control, control.options.size - 1)
            is OrbitDialog.Does.Find -> if (control is DialogControl.Choice) {
                val letter = ui.letters.of(does.dots)
                val at = letter?.let { findOption(control, it) }
                if (at == null) ui.refuse() else choose(control, at)
            }
        }
        return true
    }

    /** Space (or Dot 8) with a button's letter presses it, from anywhere in the dialog. */
    override fun letter(letter: Char): Boolean {
        val button = controls().firstOrNull { it is DialogControl.Button && it.letter?.lowercaseChar() == letter } ?: return false
        takeLine()
        pressed(button)
        return true
    }

    private fun kindOf(control: DialogControl): OrbitDialog.Control = when (control) {
        is DialogControl.Line -> OrbitDialog.Control.EDIT
        is DialogControl.Choice -> if (control.listBox) OrbitDialog.Control.LIST_BOX else OrbitDialog.Control.COMBO_BOX
        is DialogControl.Check -> OrbitDialog.Control.CHECK_BOX
        is DialogControl.Button -> OrbitDialog.Control.BUTTON
        is DialogControl.Text -> OrbitDialog.Control.TEXT
    }

    /** The line in hand, what is being typed in it taken in: before leaving it or pressing a button, so a word still in cells is not lost. */
    private fun takeLine() {
        val line = (if (lineShown) line() else held) as? DialogControl.Line ?: return
        val editor = editors[line.key]?.editor ?: return
        val text = editor.text()
        if (text != line.value) typed(line, text, text.length)
    }

    private fun moveFocus(by: Int): Boolean {
        val count = controls().size
        if (count == 0) return true
        takeLine()
        focusOn(Math.floorMod(focus + by, count))
        tellControl()
        return true
    }

    /** The choice moved to [to], told as Orbit's own combo boxes tell it; stopping at its ends with the error tone. */
    private fun choose(choice: DialogControl.Choice, to: Int) {
        if (to !in choice.options.indices || !chose(choice, to)) return ui.refuse()
        reader.itemChanged(choice.label, choice.options.size, to, choice.options[to])
    }

    // ---- the lines ----------------------------------------------------------------------

    private class Editing(val editor: EditBox, val from: String, val phone: Boolean)

    private val editors = HashMap<String, Editing>()

    /**
     * The edit box of [line], made anew only when the app changed the line underneath, or the keys
     * turned out to be the other device's.
     */
    private fun editorOf(line: DialogControl.Line): EditBox {
        val editing = editors[line.key]
        if (editing != null && editing.phone == ui.phoneKeypad && (line.value == editing.from || line.value == editing.editor.display())) {
            return editing.editor
        }
        val editor = ui.editBox(
            line.label, line.value, line.multiLine,
            confirmed = { typed -> lineDone(line, typed) },
            cancelled = ::cancelled,
            changed = { typed, caret ->
                val at = controls().indexOfFirst { it.key == line.key }
                if (at >= 0 && at != focus) focusOn(at)
                typed(line, typed, caret)
            },
            completing = completing(line),
            complete = { complete(line) },
        )
        editors[line.key] = Editing(editor, line.value, ui.phoneKeypad)
        return editor
    }

    /** Orbit is told the dialog closed, where it was told one opened. */
    override fun closed() {
        if (announced) reader.dialogClosed()
    }
}

/** [OrbitUi.form] and [OrbitUi.confirm]: a dialog holding its own controls, [closing] called once as it closes. */
internal class FormDialog(
    override val title: String,
    fields: List<Field>,
    buttons: List<Pair<String, String>>,
    start: Int,
    private val default: String?,
    private val closing: (FormDialog, String?) -> Unit,
) : DialogScreen() {
    private var shown: MutableList<DialogControl> = (fields.map(::controlOf) + buttons.map { (key, label) -> DialogControl.Button(key, label) }).toMutableList()
    private var pressedKey: String? = null

    private var inHandAt = start.coerceIn(0, (shown.size - 1).coerceAtLeast(0))

    override val focus: Int get() = inHandAt

    override fun controls(): List<DialogControl> = shown

    override fun focusOn(at: Int) {
        inHandAt = at
    }

    /** What the form holds now. */
    fun values(): FormValues = FormValues(
        shown.filterIsInstance<DialogControl.Line>().associate { it.key to it.value },
        shown.filterIsInstance<DialogControl.Choice>().associate { it.key to it.selected },
        shown.filterIsInstance<DialogControl.Check>().associate { it.key to it.checked },
    )

    private fun put(control: DialogControl) {
        val at = shown.indexOfFirst { it.key == control.key }
        if (at >= 0) shown[at] = control
    }

    override fun typed(line: DialogControl.Line, text: String, caret: Int) =
        put(DialogControl.Line(line.key, line.label, text, line.hint, line.multiLine))

    override fun lineDone(line: DialogControl.Line, text: String) {
        lineSet(line, text)
        default?.let { key -> shown.firstOrNull { it.key == key }?.let(::pressed) }
    }

    override fun lineSet(line: DialogControl.Line, text: String) = typed(line, text, text.length)

    override fun chose(choice: DialogControl.Choice, option: Int): Boolean {
        put(DialogControl.Choice(choice.key, choice.label, choice.options, option))
        return true
    }

    override fun toggled(check: DialogControl.Check): Boolean {
        put(DialogControl.Check(check.key, check.label, !check.checked))
        return !check.checked
    }

    override fun pressed(control: DialogControl) {
        pressedKey = control.key
        ui.close(this)
    }

    override fun closed() {
        super.closed()
        closing(this, pressedKey)
    }

    private companion object {
        fun controlOf(field: Field): DialogControl = when (field) {
            is Field.Line -> DialogControl.Line(field.key, field.label, field.value, field.hint, field.multiLine)
            is Field.Choice -> DialogControl.Choice(field.key, field.label, field.options, field.selected.coerceIn(0, (field.options.size - 1).coerceAtLeast(0)))
            is Field.Check -> DialogControl.Check(field.key, field.label, field.checked)
            is Field.Text -> DialogControl.Text(field.key, field.value)
        }
    }
}
