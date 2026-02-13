package org.multipaz.web

import react.Props
import web.dom.Element

@OptIn(ExperimentalStdlibApi::class)
@JsExternalInheritorsOnly
external interface MultipazProps : Props {
    var definition: Element
}