"""Host paths shared by the supported build entrypoints (no writes on import)."""
import os
from pathlib import Path
import shutil
import time

SOURCE = Path(__file__).resolve().parents[2]
LINEAGE = Path(os.environ.get("AIOS_LINEAGE_ROOT", str(SOURCE.parent / "lineage"))).expanduser().resolve()
INPUTS = Path(os.environ.get("AIOS_INPUTS", str(SOURCE / ".local/inputs"))).expanduser().resolve()
# All worktrees using one Lineage checkout must share its build lock.
STATE = Path(os.environ.get("AIOS_BUILD_STATE", str(LINEAGE / ".aios-state"))).expanduser().resolve()

def snapshot():
    usage = shutil.disk_usage(LINEAGE)
    host = os.environ.get("AIOS_HOST_VOLUME")
    vhd = os.environ.get("AIOS_VHD_PATH")
    return dict(time=time.time(), linux_used_bytes=usage.used, linux_free_bytes=usage.free,
                f_free_bytes=shutil.disk_usage(host).free if host else None,
                vhd_file_bytes=Path(vhd).stat().st_size if vhd else None)

def enough_space(value, reserve):
    free = [value["linux_free_bytes"]]
    if value.get("f_free_bytes") is not None:
        free.append(value["f_free_bytes"])
    return min(free) >= reserve
