fun Any.`as`(s: String): String{}

fun foo(p: Any) {
    p as<caret>
}

// INVOCATION_COUNT: 0
// ELEMENT: *
// CHAR: ' '
