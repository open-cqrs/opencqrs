import re

def define_env(env):

    @env.macro
    def esdb_name() -> str:
        return "EventSourcingDB"

    @env.macro
    def esdb_ref() -> str:
        return f"[{esdb_name()}](https://www.eventsourcingdb.io)"

    @env.macro
    def javadoc_class_ref(classname: str, base_url: str = "https://docs.opencqrs.com/javadoc") -> str:
        parts = classname.split(".")
        split_index = 0

        # Suche erstes Segment, das mit einem Großbuchstaben beginnt (Klassenanfang)
        for i, part in enumerate(parts):
            if part and part[0].isupper():
                split_index = i
                break

        # Packages → durch Slashes verbinden
        package_path = "/".join(parts[:split_index])
        # Klassenname + evtl. Inner Classes → bleiben mit Punkt verbunden
        class_path = ".".join(parts[split_index:])

        # Zusammensetzen
        full_path = f"{package_path}/{class_path}" if package_path else class_path
        url = f"{base_url}/{full_path}.html"
        short_classname = parts[-1]

        return f'<a href="{url}" title="{classname}" style="color: inherit;"><code>{short_classname}</code></a>'
