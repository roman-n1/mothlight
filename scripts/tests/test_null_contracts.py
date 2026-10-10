import importlib.util
from pathlib import Path
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / 'check_null_contracts.py'
SPEC = importlib.util.spec_from_file_location('null_contracts', SCRIPT)
guard = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(guard)


class NullContractsTest(unittest.TestCase):
    def test_rejects_nested_nullable_callback_return_and_cast(self):
        for source in ['val items: List<String?>', 'val cb: (() -> Unit)?',
                       'fun read():\n String ? = value', 'value as? String',
                       'val backtick: `Odd Type`?']:
            with self.subTest(source=source):
                self.assertTrue(guard.violations(source))

    def test_rejects_assertions_and_inferred_null(self):
        for source in ['val x = null', 'value!!', 'checkNotNull(value)', 'requireNotNull(value)']:
            self.assertTrue(guard.violations(source))

    def test_import_alias_and_specialized_optional_cannot_bypass(self):
        for source in ['import java.util.Optional as Value', 'typealias Value = arrow.core.Option<String>',
                       'val v: OptionalInt', 'val v: OptionalLong', 'val v: OptionalDouble',
                       'fun read() = Maybe.just(1)', 'val v = Some(1)', 'val v = None',
                       '@Nullable fun read() = value', 'class `Optional`']:
            self.assertTrue(guard.violations(source))

    def test_generic_bounds_cannot_silently_accept_nullable_values(self):
        for source in ['class Feed<T>', 'interface Sink<in T>', 'fun <T> read(): T',
                       'typealias Feed<T> = List<T>', 'class Pair<A : Any, B>']:
            self.assertTrue(guard.violations(source))
        self.assertEqual([], guard.violations('class Feed<T : Any>\nfun <T : Comparable<T>> read(): T'))

    def test_generic_sealed_presence_wrapper_cannot_rename_optional(self):
        self.assertTrue(guard.violations('sealed interface Presence<out T : Any>'))
        self.assertTrue(guard.violations('sealed class Presence<T : Any>'))
        self.assertEqual([], guard.violations('sealed interface HttpResult'))

    def test_comments_and_literals_are_data_including_nested_comments(self):
        source = '''// null !! Optional
        /* String? /* null */ Maybe */
        val text = "null !! String? Optional"
        val raw = """null, ${"safe literal"}, Option"""
        val char = '?'
        val escaped = "\\\" null"
        '''
        self.assertEqual([], guard.violations(source))

    def test_checks_executable_string_templates_including_nested_strings(self):
        for source in ['val x = "${null}"', 'val x = """${value!!}"""',
                       'val x = "${if (ready) "${null}" else "fine"}"']:
            self.assertTrue(guard.violations(source))

    def test_boundary_normalization_and_domain_states_are_allowed(self):
        self.assertEqual([], guard.violations('''
            val reading = platform.read()?.value ?: Reading.Unavailable
            sealed interface Cell { data object SqlNull : Cell; data class Text(val value: String) : Cell }
            class Feed<T : Any>
        '''))

    def test_reports_real_line_numbers(self):
        self.assertEqual(3, guard.violations('/*\n null */\nval x = null')[0][0])

    def test_unterminated_source_fails_closed(self):
        for source in ['/* missing', '"missing', '`missing', '"${missing']:
            with self.assertRaises(ValueError):
                guard.tokens(source)

    def test_discovers_android_kmp_new_source_sets_and_java_but_not_tests(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            included = ['mothlight-new/src/main/kotlin/A.kt', 'mothlight-new/src/iosMain/kotlin/B.kt',
                        'mothlight-new/src/customMain/java/C.java']
            excluded = ['mothlight-new/src/commonTest/kotlin/Test.kt', 'sample-new/src/main/kotlin/Demo.kt',
                        'mothlight-new/build/generated/E.kt']
            for name in included + excluded:
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text('class Example')
            self.assertEqual(set(included), {str(p.relative_to(root)) for p in guard.production_sources(root)})

    def test_java_strings_do_not_have_kotlin_interpolation(self):
        self.assertEqual([], guard.violations('String message = "${null}";', kotlin=False))

    def test_java_optional_and_null_annotations_are_prohibited(self):
        for source in ['return null;', 'Optional<String> value;', '@CheckForNull String value;']:
            self.assertTrue(guard.violations(source, kotlin=False))


if __name__ == '__main__':
    unittest.main()
