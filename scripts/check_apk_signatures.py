"""Check that the diagnostic APK can update the already published preview without data removal."""
import glob
import os
import pathlib
import re
import subprocess

tools = sorted(glob.glob(os.environ["ANDROID_HOME"] + "/build-tools/*/apksigner"))
if not tools:
    raise SystemExit("apksigner missing")
reports = pathlib.Path("startup-reports")
reports.mkdir(exist_ok=True)
digests = []
for file in ("previous-apk/RailCard-v1.3.0.apk", "apk/RailCard-v1.3.1.apk"):
    result = subprocess.run([tools[-1], "verify", "--print-certs", file], check=True, capture_output=True, text=True)
    digest = re.search(r"Signer #1 certificate SHA-256 digest: (\S+)", result.stdout)
    if not digest:
        raise SystemExit("Signing certificate missing: " + file)
    digests.append(digest.group(1))
    print(file + ": " + digest.group(1))
(reports / "signing-certificates.txt").write_text("\n".join(digests))
if digests[0] != digests[1]:
    raise SystemExit("Signing keys differ: this APK cannot update the installed preview. Do not ask the user to uninstall.")
