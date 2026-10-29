"""Gera um jar com a API pública do java.base de uma versão do Java, a partir do ct.sym do JDK.

O ECJ no navegador (CheerpJ) não consegue ler as classes do sistema pelo jrt:/, então recebe este jar
via -bootclasspath. As entradas do ct.sym são class files sem corpo de método, agrupadas em pastas cujo
nome lista as versões em que valem (8, 9, A=10, B=11, ... H=17).

Uso: python3 scripts/make_jdk_stub.py <ct.sym> <saida.jar> [versao=17]
"""
import sys
import zipfile


def release_letter(version: int) -> str:
    return str(version) if version < 10 else chr(ord("A") + version - 10)


def main() -> None:
    ct_sym, output = sys.argv[1], sys.argv[2]
    letter = release_letter(int(sys.argv[3]) if len(sys.argv) > 3 else 17)
    count = 0
    with zipfile.ZipFile(ct_sym) as src, zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as out:
        for name in src.namelist():
            parts = name.split("/", 2)
            if len(parts) < 3 or letter not in parts[0] or parts[1] != "java.base" or not name.endswith(".sig"):
                continue
            path = parts[2][: -len(".sig")] + ".class"
            if path == "module-info.class":
                continue
            out.writestr(path, src.read(name))
            count += 1
    if count == 0:
        sys.exit(f"nenhuma classe da versão {letter} encontrada em {ct_sym}")
    print(f"{count} classes de java.base gravadas em {output}")


if __name__ == "__main__":
    main()
