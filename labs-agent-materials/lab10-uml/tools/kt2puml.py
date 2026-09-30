#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
kt2puml: генератор диаграмм классов PlantUML по исходному коду Kotlin.

Разбирает реальные .kt-файлы агента (пакеты, классы, интерфейсы, object, enum,
sealed/data, свойства, методы, супертипы), строит связи (наследование,
реализация, ассоциация по полям, зависимость по типам параметров и результатов)
и накладывает на классы пометки шаблонов проектирования из patterns.json.

Запуск:  py kt2puml.py --src <каталог kotlin> --config diagrams.json \
                       --patterns patterns.json --out <каталог .puml>
"""
import argparse
import json
import os
import re
import sys
from collections import OrderedDict

MOD_WORDS = {
    "public", "private", "protected", "internal", "sealed", "data", "enum", "abstract",
    "open", "final", "inline", "value", "annotation", "companion", "override", "const",
    "lateinit", "suspend", "operator", "infix", "tailrec", "external",
}
VISIBILITY = {"private": "-", "protected": "#", "internal": "~", "public": "+"}


def strip_noise(text):
    """Убирает комментарии и содержимое строковых литералов, сохраняя длину и переводы строк."""
    out = []
    i = 0
    n = len(text)
    while i < n:
        c = text[i]
        if text.startswith("//", i):
            while i < n and text[i] != "\n":
                out.append(" ")
                i += 1
        elif text.startswith("/*", i):
            end = text.find("*/", i + 2)
            end = n if end == -1 else end + 2
            for ch in text[i:end]:
                out.append("\n" if ch == "\n" else " ")
            i = end
        elif text.startswith('"""', i):
            end = text.find('"""', i + 3)
            end = n if end == -1 else end + 3
            for ch in text[i:end]:
                out.append("\n" if ch == "\n" else " ")
            i = end
        elif c == '"':
            out.append('"')
            i += 1
            while i < n and text[i] != '"':
                if text[i] == "\\" and i + 1 < n:
                    out.append(" ")
                    i += 1
                out.append("\n" if text[i] == "\n" else " ")
                i += 1
            out.append('"')
            i += 1
        elif c == "'":
            end = i + 1
            while end < n and text[end] != "'":
                if text[end] == "\\":
                    end += 1
                end += 1
            for ch in text[i:end + 1]:
                out.append("'" if ch == "'" else " ")
            i = end + 1
        else:
            out.append(c)
            i += 1
    return "".join(out)


def match_bracket(s, i, open_ch, close_ch):
    depth = 0
    n = len(s)
    while i < n:
        if s[i] == open_ch:
            depth += 1
        elif s[i] == close_ch:
            depth -= 1
            if depth == 0:
                return i
        i += 1
    return n - 1


def split_top(s, sep=","):
    parts, depth, cur = [], 0, []
    for ch in s:
        if ch in "<({[":
            depth += 1
        elif ch in ">)}]":
            depth -= 1
        if ch == sep and depth == 0:
            parts.append("".join(cur))
            cur = []
        else:
            cur.append(ch)
    tail = "".join(cur)
    if tail.strip():
        parts.append(tail)
    return parts


def squash(s):
    return re.sub(r"\s+", " ", s).strip()


class Member:
    def __init__(self, kind, name, type_, visibility, params=None):
        self.kind = kind
        self.name = name
        self.type = type_
        self.visibility = visibility
        self.params = params or []


class Decl:
    def __init__(self, name, kind, package, file, mods):
        self.name = name
        self.kind = kind
        self.package = package
        self.file = file
        self.mods = mods
        self.supers = []
        self.members = []
        self.enum_entries = []
        self.outer = None
        self.ctor_props = []

    @property
    def is_interface(self):
        return self.kind == "interface"


DECL_RE = re.compile(
    r"(?P<mods>(?:\b(?:" + "|".join(sorted(MOD_WORDS)) + r")\s+)*)\b(?P<kw>class|interface|object|fun|val|var)\b"
)


def parse_params(text):
    params = []
    for raw in split_top(text):
        raw = squash(raw)
        if not raw:
            continue
        raw = re.sub(r"^(?:(?:vararg|noinline|crossinline)\s+)+", "", raw)
        vis = "+"
        vm = re.match(r"(private|protected|internal|public)\s+", raw)
        if vm:
            vis = VISIBILITY[vm.group(1)]
            raw = raw[vm.end():]
        m = re.match(r"(?:(val|var)\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*:\s*(.+)$", raw)
        if not m:
            continue
        type_part = m.group(3)
        depth = 0
        cut = len(type_part)
        for idx, ch in enumerate(type_part):
            if ch in "<({":
                depth += 1
            elif ch in ">)}":
                depth -= 1
            elif ch == "=" and depth == 0:
                cut = idx
                break
        params.append((m.group(1), m.group(2), squash(type_part[:cut]), vis))
    return params


def parse_file(path, src_root):
    with open(path, encoding="utf-8-sig") as handle:
        raw = handle.read()
    text = strip_noise(raw)
    pm = re.search(r"^\s*package\s+([\w.]+)", text, re.M)
    package = pm.group(1) if pm else ""
    imports = re.findall(r"^\s*import\s+([\w.]+)", text, re.M)

    depth_at = []
    depth = 0
    for ch in text:
        depth_at.append(depth)
        if ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1

    decls = []
    spans = []
    top_members = []
    n = len(text)
    for m in DECL_RE.finditer(text):
        kw = m.group("kw")
        mods = set(m.group("mods").split())
        start = m.end()
        if kw in ("class", "interface", "object"):
            if "companion" in mods and kw == "object":
                continue
            if "enum" in mods and kw != "class":
                continue
            nm = re.match(r"\s*([A-Za-z_][A-Za-z0-9_]*)", text[start:])
            if not nm:
                continue
            name = nm.group(1)
            pos = start + nm.end()
            after = text[pos:]
            tp = re.match(r"\s*<", after)
            if tp:
                pos = match_bracket(text, pos + tp.end() - 1, "<", ">") + 1
            ctor = ""
            cm = re.match(r"\s*(?:(?:private|internal|protected|public)\s+)?(?:constructor\s*)?\(", text[pos:])
            if cm:
                open_idx = pos + cm.end() - 1
                close_idx = match_bracket(text, open_idx, "(", ")")
                ctor = text[open_idx + 1:close_idx]
                pos = close_idx + 1
            supers_text = ""
            sm = re.match(r"\s*:", text[pos:])
            body_open = -1
            j = pos
            if sm:
                j = pos + sm.end()
                pdepth = 0
                k = j
                while k < n:
                    ch = text[k]
                    if ch in "<(":
                        pdepth += 1
                    elif ch in ">)":
                        pdepth -= 1
                    elif ch == "{" and pdepth == 0:
                        body_open = k
                        break
                    elif ch == "\n" and pdepth == 0:
                        nxt = text[k + 1:].lstrip()
                        prev = text[j:k].rstrip()
                        if not (prev.endswith(",") or nxt.startswith(",")):
                            break
                    k += 1
                supers_text = text[j:k]
                j = k
            else:
                k = pos
                while k < n and text[k] in " \t":
                    k += 1
                if k < n and text[k] == "{":
                    body_open = k
            if body_open == -1 and text[j:j + 200].lstrip().startswith("{"):
                body_open = j + text[j:].index("{")
            kind = "class"
            if kw == "interface":
                kind = "interface"
            elif kw == "object":
                kind = "object"
            elif "enum" in mods:
                kind = "enum"
            decl = Decl(name, kind, package, os.path.relpath(path, src_root).replace("\\", "/"), mods)
            for part in split_top(supers_text):
                ident = re.match(r"\s*([A-Za-z_][\w.]*)", part)
                if ident:
                    decl.supers.append(ident.group(1).split(".")[-1])
            for val_kw, pname, ptype, pvis in parse_params(ctor):
                if val_kw:
                    decl.ctor_props.append(Member("property", pname, ptype, pvis))
            decls.append(decl)
            if body_open != -1:
                body_close = match_bracket(text, body_open, "{", "}")
                spans.append((body_open, body_close, decl, depth_at[body_open] + 1))
            else:
                spans.append((-1, -1, decl, -1))
        else:
            q = m.start()
            owner = None
            owner_depth = 0
            for (bo, bc, d, dep) in spans:
                if bo != -1 and bo < q < bc and (owner is None or bo > owner[0]):
                    owner = (bo, bc, d, dep)
            if owner is None:
                if depth_at[q] != 0:
                    continue
            else:
                if depth_at[q] != owner[3]:
                    continue
            vis = "+"
            for v in ("private", "protected", "internal"):
                if v in mods:
                    vis = VISIBILITY[v]
            if kw == "fun":
                fm = re.match(r"\s*(?:<[^>]*>\s*)?(?:[\w<>?,. ]+\.)?([A-Za-z_][A-Za-z0-9_]*)\s*\(", text[start:])
                if not fm:
                    continue
                open_idx = start + fm.end() - 1
                close_idx = match_bracket(text, open_idx, "(", ")")
                params = parse_params(text[open_idx + 1:close_idx])
                k = close_idx + 1
                rest = text[k:k + 400]
                rt = "Unit"
                rm = re.match(r"\s*:\s*", rest)
                if rm:
                    t_start = k + rm.end()
                    pdepth = 0
                    t = t_start
                    while t < n:
                        ch = text[t]
                        if ch in "<(":
                            pdepth += 1
                        elif ch in ">)":
                            pdepth -= 1
                        elif pdepth == 0 and (ch == "{" or ch == "=" or ch == "\n"):
                            if ch == "\n":
                                nxt = text[t + 1:].lstrip()
                                if text[t_start:t].rstrip().endswith("->"):
                                    t += 1
                                    continue
                            break
                        t += 1
                    rt = squash(text[t_start:t])
                member = Member("fun", fm.group(1), rt, vis, [(p[1], p[2]) for p in params])
            else:
                vm = re.match(r"\s*([A-Za-z_][A-Za-z0-9_]*)\s*(?::\s*([^=\n{]+))?(?:=\s*([^\n]*))?", text[start:])
                if not vm:
                    continue
                vtype = squash(vm.group(2)) if vm.group(2) else None
                if not vtype and vm.group(3):
                    cm2 = re.match(r"\s*([A-Z][A-Za-z0-9_]*)\s*[(<.]", vm.group(3))
                    vtype = cm2.group(1) if cm2 else "?"
                member = Member("property", vm.group(1), vtype or "?", vis)
            if owner is None:
                top_members.append(Member(member.kind, member.name, member.type, member.visibility, member.params))
            else:
                owner[2].members.append(member)

    for idx, (bo, bc, d, dep) in enumerate(spans):
        parent = None
        for (bo2, bc2, d2, dep2) in spans:
            if d2 is not d and bo2 != -1 and bo2 < bo and bc < bc2:
                if parent is None or bo2 > parent[0]:
                    parent = (bo2, d2)
        if parent:
            d.outer = parent[1].name
        if d.kind == "enum" and bo != -1:
            body = text[bo + 1:bc]
            head = body
            for stop in (";", "\n    fun ", "\n    val ", "\n    var "):
                cut = head.find(stop)
                if cut != -1:
                    head = head[:cut]
            for entry in split_top(head):
                ident = re.match(r"\s*([A-Z_][A-Z0-9_]*)", entry)
                if ident:
                    d.enum_entries.append(ident.group(1))
    return package, imports, decls, top_members


def simple_types(type_text):
    return re.findall(r"[A-Z][A-Za-z0-9_]*", type_text or "")


class Model:
    def __init__(self):
        self.decls = OrderedDict()
        self.package_imports = {}
        self.file_imports = {}

    def add_file(self, path, src_root):
        package, imports, decls, _ = parse_file(path, src_root)
        rel = os.path.relpath(path, src_root).replace("\\", "/")
        self.file_imports[rel] = (package, imports)
        for d in decls:
            self.decls[d.name] = d


def load_json(path):
    with open(path, encoding="utf-8") as handle:
        return json.load(handle)


def collect_files(src, excluded_files, excluded_packages):
    result = []
    for root, _, names in os.walk(src):
        for name in sorted(names):
            if not name.endswith(".kt"):
                continue
            full = os.path.join(root, name)
            rel = os.path.relpath(full, src).replace("\\", "/")
            if rel in excluded_files:
                continue
            if any(rel.startswith(p.replace(".", "/") + "/") for p in excluded_packages):
                continue
            result.append(full)
    return sorted(result)


def member_line(m, known):
    if m.kind == "property":
        return f"{m.visibility}{m.name}: {shorten(m.type)}"
    params = ", ".join(f"{n}: {shorten(t)}" for n, t in m.params)
    ret = "" if m.type == "Unit" else f": {shorten(m.type)}"
    return f"{m.visibility}{m.name}({params}){ret}"


def shorten(t):
    t = squash(t or "")
    return t if len(t) <= 46 else t[:43] + "..."


def esc_name(n):
    return n


def render_group(group, model, patterns, stereo_colors, known_in_group_only=True):
    names = group["classes"]
    selected = [model.decls[n] for n in names if n in model.decls]
    missing = [n for n in names if n not in model.decls]
    if missing:
        raise SystemExit(f"Группа '{group['title']}': классы не найдены в коде: {missing}")
    sel_names = {d.name for d in selected}
    all_names = set(model.decls.keys())
    lines = ["@startuml", "!pragma layout smetana", "skinparam dpi 150", "skinparam classAttributeIconSize 0",
             "skinparam shadowing false", "skinparam defaultFontName Arial", "skinparam linetype ortho",
             "skinparam ranksep 40", "skinparam nodesep 30", "hide empty members",
             f'title {group["title"]}']
    if group.get("direction"):
        lines.append(group["direction"])
    max_members = group.get("max_members", 7)
    for d in selected:
        pats = patterns.get(d.name, [])
        stereos = []
        if d.kind == "object":
            stereos.append("object")
        if "data" in d.mods and d.kind == "class":
            stereos.append("data")
        if "sealed" in d.mods:
            stereos.append("sealed")
        stereos += [p["stereotype"] for p in pats]
        color = ""
        if pats:
            color = " " + stereo_colors.get(pats[0]["stereotype"], "#FFF2CC")
        st = "".join(f" <<{s}>>" for s in stereos)
        if d.kind == "interface":
            head = f"interface {d.name}{st}{color}"
        elif d.kind == "enum":
            head = f"enum {d.name}{st}{color}"
        elif "abstract" in d.mods or "sealed" in d.mods:
            head = f"abstract class {d.name}{st}{color}"
        else:
            head = f"class {d.name}{st}{color}"
        body = []
        if d.kind == "enum":
            body += list(d.enum_entries)
        else:
            props = [m for m in d.ctor_props if m.visibility in ("+", "~")]
            for m in props[:max_members]:
                body.append(member_line(m, all_names))
            remaining = max_members - min(len(props), max_members)
            own = [m for m in d.members if m.visibility in ("+", "~") and m.kind == "property"]
            funs = [m for m in d.members if m.visibility in ("+", "~") and m.kind == "fun"]
            for m in own[:max(remaining, 0)]:
                body.append(member_line(m, all_names))
            for m in funs[:max_members]:
                body.append(member_line(m, all_names))
        if body:
            lines.append(head + " {")
            lines += ["  " + b for b in body]
            lines.append("}")
        else:
            lines.append(head)

    drawn = set()
    stubs = set()

    def edge(a, b, arrow, label=""):
        key = (a, b, arrow)
        if key in drawn or a == b:
            return
        drawn.add(key)
        lines.append(f"{a} {arrow} {b}" + (f" : {label}" if label else ""))

    for d in selected:
        for s in d.supers:
            if s in model.decls:
                target = model.decls[s]
                if target.name not in sel_names:
                    stubs.add(target.name)
                arrow = "..|>" if target.is_interface else "--|>"
                edge(d.name, target.name, arrow)
        fields = [(m.name, m.type) for m in d.ctor_props] + [(m.name, m.type) for m in d.members if m.kind == "property"]
        assoc_targets = set()
        for fname, ftype in fields:
            for t in simple_types(ftype):
                if t in all_names and t != d.name and t not in d.supers:
                    if t not in sel_names:
                        if group.get("stubs", True):
                            stubs.add(t)
                        else:
                            continue
                    mult = ' "*" ' if re.search(r"\b(List|Set|Collection|Map)\b", ftype) else " "
                    if (d.name, t) in assoc_targets:
                        continue
                    assoc_targets.add((d.name, t))
                    if mult.strip():
                        lines.append(f'{d.name} -->{mult}{t} : {fname}')
                    else:
                        lines.append(f"{d.name} --> {t} : {fname}")
        for m in d.members:
            if m.kind != "fun" or m.visibility not in ("+", "~"):
                continue
            mentioned = set()
            for _, ptype in m.params:
                mentioned.update(simple_types(ptype))
            mentioned.update(simple_types(m.type))
            for t in sorted(mentioned):
                if t in all_names and t != d.name and t not in d.supers and (d.name, t) not in assoc_targets:
                    if t in sel_names or group.get("stubs", True):
                        if t not in sel_names:
                            stubs.add(t)
                        edge(d.name, t, "..>", "")
        if d.outer and d.outer in sel_names:
            parent_decl = model.decls[d.outer]
            if d.outer not in d.supers:
                edge(d.name, d.outer, "--+", "")
    for s in sorted(stubs):
        sd = model.decls[s]
        kind = "interface" if sd.is_interface else ("enum" if sd.kind == "enum" else "class")
        lines.insert(lines.index(f'title {group["title"]}') + 1, f"{kind} {s} #EEEEEE")
    notes = group.get("notes", [])
    for target, text in notes:
        if target in sel_names:
            lines.append(f'note right of {target}\n{text}\nend note')
    used = OrderedDict()
    for d in selected:
        for p in patterns.get(d.name, []):
            used.setdefault(p["stereotype"], stereo_colors.get(p["stereotype"], "#FFF2CC"))
    if used:
        lines.append("legend right")
        lines.append("  Шаблоны проектирования")
        for st, col in used.items():
            lines.append(f"  <back:{col}>      </back> <<{st}>>")
        lines.append("  Серый цвет: класс без шаблона проектирования")
        lines.append("endlegend")
    lines.append("@enduml")
    return "\n".join(lines) + "\n"


def render_packages(cfg, model, src):
    includes = cfg["packages"]
    edges = OrderedDict()
    for rel, (package, imports) in model.file_imports.items():
        if package not in includes:
            continue
        for imp in imports:
            parts = imp.split(".")
            for candidate in includes:
                if candidate != package and (imp == candidate or imp.startswith(candidate + ".")):
                    edges[(package, candidate)] = edges.get((package, candidate), 0) + 1
    lines = ["@startuml", "!pragma layout smetana", "skinparam dpi 150", "skinparam shadowing false",
             "skinparam defaultFontName Arial", "skinparam linetype ortho", f'title {cfg["title"]}']
    alias = {}
    for p in includes:
        short = p.split(".")[-1]
        alias[p] = short
        lines.append(f'package "{short}" as {short} #DDEBF7 {{\n}}')
    for (a, b), count in edges.items():
        lines.append(f"{alias[a]} ..> {alias[b]} : {count}")
    lines.append("@enduml")
    return "\n".join(lines) + "\n", edges


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", required=True)
    ap.add_argument("--config", required=True)
    ap.add_argument("--patterns", required=True)
    ap.add_argument("--out", required=True)
    args = ap.parse_args()

    cfg = load_json(args.config)
    pat_cfg = load_json(args.patterns)
    patterns = {}
    for item in pat_cfg["patterns"]:
        patterns.setdefault(item["class"], []).append(item)
    stereo_colors = pat_cfg["colors"]

    files = collect_files(args.src, set(cfg.get("exclude_files", [])), cfg.get("exclude_packages", []))
    model = Model()
    for f in files:
        model.add_file(f, args.src)

    errors = [f"Шаблон '{p['stereotype']}' указывает на отсутствующий класс {p['class']}" for plist in patterns.values()
              for p in plist if p["class"] not in model.decls]
    if errors:
        print("\n".join(errors), file=sys.stderr)
        sys.exit(2)

    os.makedirs(args.out, exist_ok=True)
    produced = []
    text, edges = render_packages(cfg["package_diagram"], model, args.src)
    path = os.path.join(args.out, cfg["package_diagram"]["file"])
    with open(path, "w", encoding="utf-8", newline="\n") as handle:
        handle.write(text)
    produced.append(path)
    for group in cfg["groups"]:
        text = render_group(group, model, patterns, stereo_colors)
        path = os.path.join(args.out, group["file"])
        with open(path, "w", encoding="utf-8", newline="\n") as handle:
            handle.write(text)
        produced.append(path)
    summary = {
        "files_parsed": len(files),
        "classes_found": len(model.decls),
        "diagrams": [os.path.basename(p) for p in produced],
        "package_edges": {f"{a}->{b}": c for (a, b), c in edges.items()},
    }
    with open(os.path.join(args.out, "summary.json"), "w", encoding="utf-8") as handle:
        json.dump(summary, handle, ensure_ascii=False, indent=2)
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
