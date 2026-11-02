import importlib
import os
import re
from threading import RLock


def install(native_directory, asset_directory, native_libraries):
    importer = importlib.import_module("java.android.importer")
    original_extract = importer.AssetZipFile.extract_if_changed
    native_lock = RLock()

    def extract_if_changed(self, member, target_dir):
        if isinstance(member, str):
            member = self.getinfo(member)
        if not re.search(r"\.so(?:\.|$)", member.filename):
            return original_extract(self, member, target_dir)

        destination = os.path.join(target_dir, member.filename)
        relative_path = os.path.relpath(destination, asset_directory)
        library_name = native_libraries.get(relative_path)
        if library_name is None:
            raise ImportError(f"Native library was not packaged: {relative_path}")
        if os.path.basename(library_name) != library_name:
            raise ImportError(f"Invalid installed library name: {library_name}")
        library = os.path.join(native_directory, library_name)
        if not os.path.isfile(library):
            raise ImportError(f"Missing installed native library: {library_name}")

        with native_lock:
            if os.path.islink(destination) and os.path.realpath(destination) == library:
                return destination
            os.makedirs(os.path.dirname(destination), exist_ok=True)
            if os.path.lexists(destination):
                os.unlink(destination)
            os.symlink(library, destination)
        return destination

    importer.AssetZipFile.extract_if_changed = extract_if_changed
