import argparse
import ast
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import List, Optional, Dict

PY_TO_KOTLIN_TYPES = {
    "int": "Int",
    "float": "Double",
    "str": "String",
    "bool": "Boolean",
    "Any": "Any",
    "None": "Unit",
    "Dict": "Map",
    "EmailStr": "String",
}

FILE_PREAMBLE = """@file:Suppress("unused")

package ai.divyam.client.data.models

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import com.formkiq.graalvm.annotations.Reflectable
import java.util.Locale
"""

CLASS_PREAMBLE = """@Reflectable
"""

ENUM_PREAMBLE = CLASS_PREAMBLE

ENUM_JSON_CREATOR_TEMPLATE = """
    @JsonCreator
    fun fromString(value: String?): {ename}? {{
        if (value == null) {{
            return null
        }}
        return {ename}.valueOf(value.replace("-", "_").uppercase(
            Locale.getDefault()))
    }}
"""


@dataclass
class GenerationResult:
    generated_types: List[str]
    code: str


class GenerateKotlinModels:
    """Generate kotlin data models from python code."""

    def __init__(self, source_dir: str, dest_dir: str):
        self.source_dir = source_dir
        self.dest_dir = dest_dir

    @staticmethod
    def python_type_to_kotlin(node) -> str:
        """Recursively convert Python type annotations to Kotlin."""
        if isinstance(node, ast.Name):  # simple type
            return PY_TO_KOTLIN_TYPES.get(node.id, node.id)

        if isinstance(node, ast.Subscript):  # generics: list[X], dict[K, V]
            base = getattr(node.value, "id", None)
            if base == "list" or base == "tuple" or base == "List":
                return (f"List<"
                        f"{GenerateKotlinModels.python_type_to_kotlin(node.slice)}>")
            if (base == "dict" or base == "Dict") and isinstance(node.slice,
                                                                 ast.Tuple):
                key_type = GenerateKotlinModels.python_type_to_kotlin(
                    node.slice.elts[0])
                val_type = GenerateKotlinModels.python_type_to_kotlin(
                    node.slice.elts[1])
                return f"Map<{key_type}, {val_type}>"
            if base == "Optional":
                return f"{GenerateKotlinModels.python_type_to_kotlin(node.slice)}? = null"
            # fallback for unknown generics
            return f"{base}<{GenerateKotlinModels.python_type_to_kotlin(node.slice)}>"

        if isinstance(node, ast.Tuple):  # multiple types (used in dict slice)
            return ", ".join(
                GenerateKotlinModels.python_type_to_kotlin(elt) for elt in
                node.elts)

        return "Any"  # default fallback

    @staticmethod
    def snake_to_camel(s: str) -> str:
        parts = s.split('_')
        return parts[0] + ''.join(word.capitalize() for word in parts[1:])

    @staticmethod
    def convert_field(fname: str, ftype: str) -> str:
        return ("\n"
                f"    @param:JsonProperty(\"{fname}\")\n"
                f"    val {GenerateKotlinModels.snake_to_camel(fname)}: {ftype}")

    @staticmethod
    def create_enum_creator(ename, values):
        return ENUM_JSON_CREATOR_TEMPLATE.format(ename=ename)

    @staticmethod
    def create_enum_value_fn(value_map: Dict[str, str]) -> str:
        value_entries = [f"        \"{key}\" to \"{value}\"" for key, value in
                         value_map.items()]

        return ("\n    private val valueMap = mapOf(\n"
                f"{",\n".join(value_entries)}\n"
                "    )\n"
                "\n"
                "    @JsonValue\n"
                "    fun toValue(): String {\n"
                "         return valueMap[this.name]!!\n"
                "    }"
                )

    @staticmethod
    def convert_python_to_kotlin(python_code: str, enum_only=False,
                                 generated_types: Optional[
                                     List[str]] = None) -> GenerationResult:
        if generated_types is None:
            generated_types = []
        tree = ast.parse(python_code)
        kotlin_classes = []

        for node in tree.body:
            if isinstance(node, ast.ClassDef):
                is_enum = any(
                    isinstance(base, ast.Name) and base.id == "Enum" for base in
                    node.bases)

                if is_enum:
                    # Enum class
                    constants = []
                    value_map = {}
                    for stmt in node.body:
                        if isinstance(stmt, ast.Assign):
                            constant = stmt.targets[0].id
                            constants.append(constant)
                            value_map[constant] = stmt.value.value
                    kotlin = [ENUM_PREAMBLE]
                    kotlin.append(f"enum class {node.name} {{\n")
                    kotlin.append("    ")
                    kotlin.append(", ".join(constants))
                    kotlin.append(";\n")
                    kotlin.append(GenerateKotlinModels.create_enum_creator(
                        ename=node.name, values=constants))
                    kotlin.append(GenerateKotlinModels.create_enum_value_fn(
                        value_map=value_map))
                    kotlin.append("\n}")
                else:
                    if enum_only:
                        continue

                    # Dataclass → Kotlin data class
                    fields = []
                    for stmt in node.body:
                        if isinstance(stmt, ast.AnnAssign):
                            field_name = stmt.target.id
                            field_type = GenerateKotlinModels.python_type_to_kotlin(
                                stmt.annotation)
                            fields.append((field_name, field_type))

                    kotlin = [CLASS_PREAMBLE]
                    kotlin.append(f"data class {node.name}(\n")
                    kotlin.append(",\n".join(
                        GenerateKotlinModels.convert_field(fname, ftype) for
                        fname,
                        ftype in fields))
                    kotlin.append("\n)\n")

                if node.name in generated_types:
                    # Name already generated.
                    print(f"Skipping duplicate generated type {node.name}",
                          file=sys.stderr)
                    continue

                generated_types.append(node.name)
                kotlin_classes.append("".join(kotlin))

        return GenerationResult(code="\n\n".join(kotlin_classes),
                                generated_types=generated_types)

    @staticmethod
    def list_files_matching(directory: str, pattern: str) -> List[str]:
        path = Path(directory)
        return [str(file) for file in path.glob(pattern)]

    def generate(self):
        with open(f"{self.dest_dir}/DataModels.kt", "w") as f:
            print(FILE_PREAMBLE, file=f)

            generated_types = []

            # Generate models from the external DAO
            external_models_dir = str(Path(self.source_dir) / "divyam-api" /
                                      "divyam_router" / "external_do")
            inputs = self.list_files_matching(external_models_dir, "*.py")
            assert inputs
            for input_file in inputs:
                converted = GenerateKotlinModels.convert_python_to_kotlin(Path(
                    input_file).read_text(), generated_types=generated_types)
                print(converted.code, file=f)
                generated_types.append(generated_types)

            # Generate some models from libs router models
            db_models_dir = str(Path(self.source_dir) /
                                "divyam-libs" / "src" / "divyamlibs" / "router" / "models")
            inputs = self.list_files_matching(db_models_dir, "router_models.py")
            assert inputs
            for input_file in inputs:
                converted = GenerateKotlinModels.convert_python_to_kotlin(Path(
                    input_file).read_text(), generated_types=generated_types)
                print(converted.code, file=f)
                generated_types.append(generated_types)

            # Generate reused enums from libs
            db_models_dir = str(Path(self.source_dir) /
                                "divyam-libs" / "src" / "divyamlibs" / "router" / "models")
            inputs = self.list_files_matching(db_models_dir,
                                              "router_db_models.py")
            assert inputs
            for input_file in inputs:
                converted = GenerateKotlinModels.convert_python_to_kotlin(Path(
                    input_file).read_text(), enum_only=True,
                                                                          generated_types=generated_types)
                print(converted.code, file=f)
                generated_types.append(generated_types)

            # Imports from other places
            other_enums_dir = str(Path(self.source_dir) /
                                  "divyam-libs" / "src" / "divyamlibs" / "router" / "security")
            inputs = self.list_files_matching(other_enums_dir, "*.py")
            assert inputs
            for input_file in inputs:
                converted = GenerateKotlinModels.convert_python_to_kotlin(Path(
                    input_file).read_text(), enum_only=True,
                                                                          generated_types=generated_types)
                print(converted.code, file=f)
                generated_types.append(generated_types)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="Generate Kotlin data models from Divyam python API data model")
    parser.add_argument('--source',
                        help="Optional: The root directory where api and libs are located. Default is this project root")
    parser.add_argument('--dest',
                        help="Optional: The directory where generated models "
                             "will be saved. "
                             "Default "
                             "is ../src/main/kotlin/ai/divyam/client/data/models")

    args = parser.parse_args()

    source_dir = args.source if args.source else Path(
        __file__).resolve().parent.parent

    dest_dir = args.dest if args.dest else str(Path(
        __file__).resolve().parent.parent.parent / "src" / "main" / "kotlin" / "ai" / "divyam" / "client" / "data" / "models")

    generator = GenerateKotlinModels(str(source_dir.absolute()), dest_dir)
    generator.generate()
