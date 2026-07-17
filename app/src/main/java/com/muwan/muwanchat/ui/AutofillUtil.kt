package com.muwan.muwanchat.ui

import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.AutofillNode
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalAutofillTree

// Gmail/Password-Manager se saved email/password suggest karwane ke liye —
// yeh field ko OS ke Autofill framework se register karta hai.
fun Modifier.autofill(
    autofillTypes: List<AutofillType>,
    onFill: (String) -> Unit
): Modifier = composed {
    val autofill = LocalAutofill.current
    val autofillNode = remember { AutofillNode(onFill = onFill, autofillTypes = autofillTypes) }
    LocalAutofillTree.current += autofillNode

    this
        .onGloballyPositioned { autofillNode.boundingBox = it.boundsInWindow() }
        .then(
            Modifier.onFocusEventCompat { focused ->
                autofill?.run {
                    if (focused) requestAutofillForNode(autofillNode)
                    else cancelAutofillForNode(autofillNode)
                }
            }
        )
}

private fun Modifier.onFocusEventCompat(onFocus: (Boolean) -> Unit): Modifier =
    this.onFocusChanged { onFocus(it.isFocused) }
