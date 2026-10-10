"""Reject null contracts in handwritten library sources (Python 3.9+).

Tokenize comments, strings and Kotlin interpolation so examples in documentation
do not trigger failures, while executable code inside string templates is checked.
There is deliberately no baseline, suppression annotation or per-file allow-list.
"""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
FORBIDDEN = {
    "null": "use a domain state or an object with empty behavior",
    "!!": "handle the boundary result explicitly",
    "checkNotNull": "do not convert absence into an assertion",
    "requireNotNull": "do not accept nullable input",
    "Optional": "optional containers are prohibited",
    "OptionalInt": "optional containers are prohibited",
    "OptionalLong": "optional containers are prohibited",
    "OptionalDouble": "optional containers are prohibited",
    "Option": "optional containers are prohibited",
    "Maybe": "optional containers are prohibited",
    "Some": "optional containers are prohibited",
    "None": "optional containers are prohibited",
    "Nullable": "nullable annotations and containers are prohibited",
    "CheckForNull": "nullable annotations are prohibited",
    "NullnessUnspecified": "unspecified nullability is prohibited",
}


def tokens(source, kotlin=True):
    result = []
    size = len(source)

    def quoted(i, raw=False, char=False):
        delimiter = '"""' if raw else ("'" if char else '"')
        i += len(delimiter)
        while i < size:
            if source.startswith(delimiter, i):
                return i + len(delimiter)
            if not raw and source[i] == "\\":
                i += 2
                continue
            if kotlin and not char and source.startswith("${", i):
                i = code(i + 2, template=True)
                continue
            i += 1
        raise ValueError("Unterminated quoted literal")

    def code(i, template=False):
        depth = 0
        while i < size:
            if source.startswith("//", i):
                end = source.find("\n", i)
                i = size if end == -1 else end
            elif source.startswith("/*", i):
                nesting = 1
                i += 2
                while i < size and nesting:
                    if source.startswith("/*", i):
                        nesting += 1
                        i += 2
                    elif source.startswith("*/", i):
                        nesting -= 1
                        i += 2
                    else:
                        i += 1
                if nesting:
                    raise ValueError("Unterminated block comment")
            elif source.startswith('"""', i):
                i = quoted(i, raw=True)
            elif source[i] in "\"'":
                i = quoted(i, char=source[i] == "'")
            elif source[i] == '`':
                end = source.find('`', i + 1)
                if end == -1:
                    raise ValueError("Unterminated identifier")
                result.append((source[i + 1:end], i))
                i = end + 1
            elif source[i].isspace():
                i += 1
            else:
                match = re.match(r"[A-Za-z_][A-Za-z_0-9]*|!!|\?\.|\?:|.", source[i:])
                value = match.group()
                if value == '}' and template and depth == 0:
                    return i + 1
                if value == '{':
                    depth += 1
                elif value == '}':
                    depth -= 1
                result.append((value, i))
                i += len(value)
        if template:
            raise ValueError("Unterminated string template")
        return i

    code(0)
    return result


def violations(source, kotlin=True):
    items = tokens(source, kotlin=kotlin)
    failures = []
    for index, (token, offset) in enumerate(items):
        reason = FORBIDDEN.get(token)
        if kotlin and token == '?':
            reason = "nullable types and nullable casts are prohibited"
        if reason:
            failures.append((source.count('\n', 0, offset) + 1, token, reason))
        # Kotlin's implicit bound is Any?. A nullable T would bypass a ban on '?'.
        if kotlin and token in ("class", "interface", "fun", "typealias"):
            cursor = index + 1
            if token != "fun":
                cursor += 1  # declaration name
            if cursor >= len(items) or items[cursor][0] != '<':
                continue
            cursor += 1
            parameters = [[]]
            depth = 0
            while cursor < len(items):
                current = items[cursor][0]
                if current == '>' and depth == 0:
                    break
                if current == ',' and depth == 0:
                    parameters.append([])
                else:
                    parameters[-1].append(current)
                    if current == '<':
                        depth += 1
                    elif current == '>':
                        depth -= 1
                cursor += 1
            for parameter in parameters:
                if ':' not in parameter:
                    failures.append((source.count('\n', 0, offset) + 1, token, "type parameters need an explicit non-null upper bound"))
            # A generic sealed presence wrapper merely renames Option/Maybe.
            if any(t[0] == 'sealed' for t in items[max(0, index - 2):index]) and token in ('class', 'interface'):
                failures.append((source.count('\n', 0, offset) + 1, token, "generic sealed containers are prohibited; model domain states"))
    return failures


def production_sources(root):
    for module in sorted(root.glob('mothlight-*')):
        for source_set in sorted((module / 'src').glob('*')):
            if source_set.name != 'main' and not source_set.name.endswith('Main'):
                continue
            for path in sorted(source_set.rglob('*')):
                if path.suffix in ('.kt', '.java'):
                    yield path


def check(root):
    files = list(production_sources(root))
    if not files:
        print('No library production sources found', file=sys.stderr)
        return 1
    errors = []
    for path in files:
        try:
            for line, token, reason in violations(path.read_text(), kotlin=path.suffix == '.kt'):
                errors.append(f'{path.relative_to(root)}:{line}: {token}: {reason}')
        except ValueError as failure:
            errors.append(f'{path.relative_to(root)}: {failure}')
    if errors:
        print('\n'.join(errors), file=sys.stderr)
        return 1
    print(f'Null contracts: {len(files)} production files checked; no nullable declarations, null, assertions or optional containers')
    return 0


if __name__ == '__main__':
    sys.exit(check(ROOT))
