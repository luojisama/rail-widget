"""Verify that the diagnostic APK uses an independent package and has a valid signature."""
import glob
import hashlib
import os
import pathlib
import re
import subprocess

root = pathlib.Path("startup-reports")
root.mkdir(exist_ok=True)
java_tools = sorted(glob.glob(os.environ["ANDROID_HOME"] + "/build-tools/*/apksigner"))
aapt_tools = sorted(glob.glob(os.environ["ANDROID_HOME"] + "/build-tools/*/aapt")) or sorted(glob.glob(os.environ["ANDROID_HOME"] + "/build-tools/*/aapt2"))
apk = "apk/RailCard-v1.3.1-diagnostic.apk"
report = subprocess.run([java_tools[-1], "verify", "--print-certs", apk], check=True, capture_output=True, text=True)
output = report.stdout + report.stderr
print(output)
match = re.search(r"certificate SHA-256 digest(?:\s*\([^)]*\))?:\s*([0-9a-fA-F:]{64,})", output, re.IGNORECASE)
if not match:
    raise SystemExit("Signing certificate missing")
actual = match.group(1).replace(":", "").lower()
badging = subprocess.run([aapt_tools[-1], "dump", "badging", apk], check=True, capture_output=True, text=True).stdout
package = re.search(r"package: name='([^']+)'", badging)
if not package or package.group(1) != "team.shiro.railwidget.diagnostic":
    raise SystemExit("Diagnostic APK must use its separate application ID to preserve the installed app")
(root / "signing-certificates.txt").write_text("Diagnostic package: " + package.group(1) + "\nCertificate: " + actual)
print("Independent diagnostic package and APK signature verified")
