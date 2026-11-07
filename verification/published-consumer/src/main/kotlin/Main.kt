import io.github.mothlight.Mothlight

fun main() {
    val mothlight = Mothlight()
    mothlight.setEnabled(true)
    mothlight.startup.mark("consumer-ready", 1)
    check(mothlight.startup.spans.value.single().stage == "consumer-ready")
}
