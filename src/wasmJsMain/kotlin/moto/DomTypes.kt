package moto

external interface HTMLElement {
    var textContent: String?
    var innerHTML: String
    var hidden: Boolean
    fun addEventListener(type: String, listener: (Event) -> Unit)
}

external interface HTMLInputElement : HTMLElement {
    var value: String
    var files: FileList?
    fun click()
}

external interface FileList {
    val length: Int
    fun item(index: Int): File?
}

external interface File {
    val name: String
}

external interface HTMLSelectElement : HTMLElement {
    var selectedIndex: Int
    var value: String
    override var innerHTML: String
}

external interface HTMLButtonElement : HTMLElement {
    var disabled: Boolean
    override fun addEventListener(type: String, listener: (Event) -> Unit)
}

external interface Event

external interface Document {
    fun getElementById(id: String): HTMLElement?
    fun createElement(tag: String): HTMLElement
    var body: HTMLElement
}

external val document: Document

external interface Window {
    fun alert(message: String)
}

external val window: Window
