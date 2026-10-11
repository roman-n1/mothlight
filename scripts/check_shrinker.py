"""Validate consumer-rule wiring and, in CI, packaged artifacts plus real R8 output."""
from pathlib import Path
import re
import os
import shutil
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
MODULES = ["core", "compose", "ktor", "memory", "retention", "shark", "room", "room3", "sqlite", "okhttp"]


def check_sources():
    rules = ROOT / "consumer-rules.pro"
    assert rules.is_file(), "Missing shared Android consumer-rules.pro"
    for name in MODULES:
        build = (ROOT / f"mothlight-{name}/build.gradle.kts").read_text()
        assert 'publish = true' in build, f"{name}: consumer publication is not enabled"
        assert 'file(rootProject.file("consumer-rules.pro"))' in build, f"{name}: rules not wired"
    print("Consumer rules: all Android modules wired")


def check_no_reflection():
    patterns = [r"java\.lang\.reflect", r"kotlin\.reflect", r"com\.sun\.jna",
                r"\.javaClass\b", r"::class\s*\.", r"Class\.forName", r"\.getClass\(",
                r"ServiceLoader\.load", r"MethodHandles\.lookup"]
    violations = []
    for module in ROOT.glob("mothlight-*"):
        sources = list(module.glob("src/*Main/**/*")) + list(module.glob("src/main/**/*"))
        for source in sources:
            if source.suffix not in (".kt", ".java"):
                continue
            text = re.sub(r"(?m)//.*$", "", source.read_text())
            if any(re.search(pattern, text) for pattern in patterns):
                violations.append(str(source.relative_to(ROOT)))
        build = module / "build.gradle.kts"
        if build.exists() and re.search(r"net\.java\.dev\.jna|kotlin-reflect", build.read_text()):
            violations.append(str(build.relative_to(ROOT)))
    assert not violations, "Reflection returned to library code: " + ", ".join(violations)
    print("Mothlight production sources: no reflective lookup or direct reflection dependency")


def check_artifacts():
    for name in MODULES + ["android"]:
        archives = list((ROOT / f"mothlight-{name}/build/outputs/aar").glob("*.aar"))
        assert archives, f"{name}: no packaged AAR to verify"
        for archive in archives:
            with zipfile.ZipFile(archive) as aar:
                assert "proguard.txt" in aar.namelist(), f"{archive}: missing consumer rules"
                if name == "compose":
                    for locale in ("values", "values-ru"):
                        resource = f"assets/composeResources/io.github.mothlight.compose.resources/{locale}/strings.commonMain.cvr"
                        assert resource in aar.namelist(), f"{archive}: missing {locale} Compose strings"
                rules = aar.read("proguard.txt").decode()
                assert "ProcessMemoryApi" not in rules, f"{archive}: JVM-only JNA rules leaked to Android"
    jars = list((ROOT / "mothlight-memory/build/libs").glob("*-jvm-*.jar"))
    jars = [p for p in jars if "sources" not in p.name]
    assert jars, "No memory JVM JAR to inspect"
    for jar in jars:
        with zipfile.ZipFile(jar) as archive:
            assert "META-INF/proguard/mothlight-memory.pro" not in archive.namelist(), "Obsolete JNA rules packaged"
            assert not any("ProcessMemoryApi" in name or "ProcessMemoryCounters" in name for name in archive.namelist()), "JNA binding is still packaged"
    retention_jars = [p for p in (ROOT / "mothlight-retention/build/libs").glob("*-jvm-*.jar") if "sources" not in p.name]
    assert retention_jars, "No retention JVM JAR to inspect"
    for jar in retention_jars:
        with zipfile.ZipFile(jar) as archive:
            schema_rules = archive.read("META-INF/proguard/mothlight-retention.pro").decode()
            assert "HeapWatchReference" in schema_rules and "watchId" in schema_rules and "sessionId" in schema_rules
    output = ROOT / "sample-android/build/outputs/mapping/release"
    configuration = (output / "configuration.txt").read_text()
    assert not re.search(r"(?m)^-dont(?:obfuscate|shrink|optimize)\b", configuration), "R8 protection was disabled"
    mapping = (output / "mapping.txt").read_text()
    renamed = [(a, b) for a, b in re.findall(r"(?m)^(io\.github\.mothlight\.[^ ]+) -> ([^:]+):$", mapping) if a != b and not b.startswith("R8$$") and not a.startswith("io.github.mothlight.sample.")]
    assert renamed, "No Mothlight classes were actually obfuscated"
    # R8 may omit identity member mappings. Inspect the output DEX, not that omission.
    seeds = (output / "seeds.txt").read_text()
    assert "io.github.mothlight.sample.android.MainActivity: java.lang.Object heldObject" in seeds, "Demo keep rule did not match"
    apks = list((ROOT / "sample-android/build/outputs/apk/release").glob("*.apk"))
    assert len(apks) == 1, "Expected one release APK to inspect"
    with zipfile.ZipFile(apks[0]) as apk:
        for locale in ("values", "values-ru"):
            resource = f"assets/composeResources/io.github.mothlight.compose.resources/{locale}/strings.commonMain.cvr"
            assert resource in apk.namelist(), f"Release APK: missing {locale} Compose strings"
    analyzer = shutil.which("apkanalyzer")
    if analyzer is None:
        sdk = Path(os.environ["ANDROID_HOME"])
        analyzer = str(sdk / "cmdline-tools/latest/bin/apkanalyzer")
    smali = subprocess.run([analyzer, "dex", "code", "--class", "io.github.mothlight.sample.android.MainActivity", str(apks[0])],
                           check=True, capture_output=True, text=True).stdout
    (output / "retention-root.smali").write_text(smali)
    assert re.search(r"(?m)^\.field[^\n]*\bheldObject:Ljava/lang/Object;", smali), "Demo root field absent from release DEX"
    assert re.search(r"iput-object[^\n]*->heldObject:Ljava/lang/Object;", smali), "Release DEX never stores the demo root"
    marker = subprocess.run([analyzer, "dex", "code", "--class", "io.github.mothlight.retention.HeapWatchReference", str(apks[0])],
                            check=True, capture_output=True, text=True).stdout
    (output / "retention-marker.smali").write_text(marker)
    for name, descriptor, instruction in [("watchId", "J", "iput-wide"), ("sessionId", "Ljava/lang/String;", "iput-object")]:
        assert re.search(r"(?m)^\.field[^\n]*\b" + name + ":" + re.escape(descriptor), marker), f"Heap schema field {name} missing"
        assert re.search(instruction + r"[^\n]*->" + name + ":" + re.escape(descriptor), marker), f"Heap schema field {name} is never written"
    print(f"Packaged consumer rules OK; R8 renamed {len(renamed)} Mothlight classes and preserved the deliberate demo root")


if __name__ == "__main__":
    check_sources()
    check_no_reflection()
    if "--artifacts" in sys.argv:
        check_artifacts()
