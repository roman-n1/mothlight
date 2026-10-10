"""Validate consumer-rule wiring and, in CI, packaged artifacts plus real R8 output."""
from pathlib import Path
import re
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
MODULES = ["core", "compose", "ktor", "memory", "retention", "room", "room3", "sqlite", "okhttp"]


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
        for source in module.glob("src/*Main/**/*.kt"):
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
                rules = aar.read("proguard.txt").decode()
                assert "ProcessMemoryApi" not in rules, f"{archive}: JVM-only JNA rules leaked to Android"
    jars = list((ROOT / "mothlight-memory/build/libs").glob("*-jvm-*.jar"))
    jars = [p for p in jars if "sources" not in p.name]
    assert jars, "No memory JVM JAR to inspect"
    for jar in jars:
        with zipfile.ZipFile(jar) as archive:
            assert "META-INF/proguard/mothlight-memory.pro" not in archive.namelist(), "Obsolete JNA rules packaged"
            assert not any("ProcessMemoryApi" in name or "ProcessMemoryCounters" in name for name in archive.namelist()), "JNA binding is still packaged"
    output = ROOT / "sample-android/build/outputs/mapping/release"
    configuration = (output / "configuration.txt").read_text()
    assert not re.search(r"(?m)^-dont(?:obfuscate|shrink|optimize)\b", configuration), "R8 protection was disabled"
    mapping = (output / "mapping.txt").read_text()
    renamed = [(a, b) for a, b in re.findall(r"(?m)^(io\.github\.mothlight\.[^ ]+) -> ([^:]+):$", mapping) if a != b]
    assert renamed, "No Mothlight classes were actually obfuscated"
    assert "java.lang.Object heldObject -> heldObject" in mapping, "Demo root was removed or renamed"
    print(f"Packaged consumer rules OK; R8 renamed {len(renamed)} Mothlight classes and preserved the deliberate demo root")


if __name__ == "__main__":
    check_sources()
    check_no_reflection()
    if "--artifacts" in sys.argv:
        check_artifacts()
