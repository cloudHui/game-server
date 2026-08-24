#!/usr/bin/env python3
"""从已解压的本地资源目录生成开放学习库索引。"""
import argparse, csv, hashlib, json, re, shutil, subprocess
from pathlib import Path

SKIP_POETRY = {"rank", "strains", "loader", "images", "御定全唐詩"}


def json_lines(path):
    """逐行读取 JSONL，坏行跳过。"""
    with path.open(encoding="utf-8") as f:
        for line in f:
            try:
                yield json.loads(line)
            except (ValueError, TypeError):
                pass


def simplify_file(path):
    """整文件繁转简；转换器不可用时明确失败，禁止发布混合字形。"""
    tmp = path.with_suffix(path.suffix + ".simp")
    try:
        subprocess.run(
            ["opencc", "-c", "t2s.json", "-i", str(path), "-o", str(tmp)],
            check=True, capture_output=True, text=True,
        )
        tmp.replace(path)
        return
    except FileNotFoundError:
        try:
            from opencc import OpenCC
        except ImportError as exc:
            raise RuntimeError("缺少 OpenCC：请安装 opencc 命令或 Python opencc 包") from exc
        converter = OpenCC("t2s")
        with path.open(encoding="utf-8") as src, tmp.open("w", encoding="utf-8") as out:
            for line in src:
                out.write(converter.convert(line))
        tmp.replace(path)
    except subprocess.CalledProcessError as exc:
        if tmp.exists():
            tmp.unlink()
        raise RuntimeError(f"OpenCC 转换失败：{exc.stderr.strip()}") from exc


def dedupe_jsonl(path):
    """按整行 SHA-1 去重，保持首次出现顺序。"""
    seen = set()
    tmp = path.with_suffix(path.suffix + ".dedup")
    with path.open(encoding="utf-8") as src, tmp.open("w", encoding="utf-8") as out:
        for line in src:
            digest = hashlib.sha1(line.encode()).digest()
            if digest in seen:
                continue
            seen.add(digest)
            out.write(line)
    tmp.replace(path)


def hanzi(source, target):
    """生成汉字笔顺单字 JSON。"""
    target.mkdir(parents=True, exist_ok=True)
    for old in target.glob("*.json"):
        old.unlink()
    dictionary = {x.get("character"): x for x in json_lines(source / "dictionary.txt")}
    for graphic in json_lines(source / "graphics.txt"):
        char = graphic.get("character")
        if not char:
            continue
        item = dictionary.get(char, {})
        item.update(graphic)
        (target / f"{ord(char):x}.json").write_text(
            json.dumps(item, ensure_ascii=False, separators=(",", ":")), encoding="utf-8"
        )


def ecdict(source, target):
    """按词头两字母分片写入英汉词典。"""
    csv_path = source if source.is_file() else source / "ecdict.csv"
    target.mkdir(parents=True, exist_ok=True)
    for old in target.glob("*.jsonl"):
        old.unlink()
    handles = {}
    try:
        with csv_path.open(encoding="utf-8", newline="") as f:
            for item in csv.DictReader(f):
                word = (item.get("word") or "").lower()
                key = "".join(c if c.isalpha() and c.isascii() else "_" for c in word[:2])
                if not key:
                    continue
                if key not in handles:
                    handles[key] = (target / f"{key}.jsonl").open("w", encoding="utf-8")
                row = {k: item.get(k, "") for k in ("word", "phonetic", "definition", "translation", "tag", "exchange")}
                handles[key].write(json.dumps(row, ensure_ascii=False, separators=(",", ":")) + "\n")
    finally:
        for out in handles.values():
            out.close()
    for shard in sorted(target.glob("*.jsonl")):
        simplify_file(shard)


def iter_poetry_items(path):
    """兼容列表 JSON 与蒙学嵌套结构。"""
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (ValueError, OSError):
        return
    if isinstance(data, list):
        for item in data:
            if isinstance(item, dict):
                yield item
        return
    if not isinstance(data, dict):
        return
    for block in data.get("content") or []:
        if not isinstance(block, dict):
            continue
        nested = block.get("content")
        if isinstance(nested, list):
            for item in nested:
                if isinstance(item, dict):
                    yield item
        elif block.get("title") or block.get("chapter"):
            yield block


def infer_dynasty(path, item):
    """优先使用原始字段，否则按 chinese-poetry 数据目录/文件名推断年代。"""
    aliases = {"tang": "唐", "song": "宋", "yuan": "元", "qing": "清", "唐代": "唐", "宋代": "宋", "元代": "元", "清代": "清"}
    explicit = str(item.get("dynasty") or item.get("period") or "").strip()
    if explicit:
        return aliases.get(explicit.lower(), aliases.get(explicit, explicit))
    author = str(item.get("author") or "")
    prefix = re.match(r"^(先秦|两汉|兩漢|魏晋|魏晉|南北朝|隋|唐|宋|元|明|清)(?:代)?[：:]", author)
    if prefix:
        return {"兩漢": "两汉", "魏晉": "魏晋"}.get(prefix.group(1), prefix.group(1))
    value = "/".join(path.parts).lower()
    if "poet.song." in value or "宋词" in value:
        return "宋"
    if "poet.tang." in value or "全唐诗" in value or "唐诗" in value or "水墨唐诗" in value or "御定全唐詩" in value:
        return "唐"
    mappings = (
        ("五代诗词", "五代"), ("元曲", "元"), ("诗经", "先秦"),
        ("楚辞", "先秦"), ("论语", "先秦"), ("四书五经", "先秦"),
        ("曹操诗集", "魏晋"), ("纳兰性德", "清"),
    )
    for marker, dynasty in mappings:
        if marker in value:
            return dynasty
    return "其他"


def normalize_poem(item, path=Path()):
    """统一成 title/author/dynasty/paragraphs。"""
    title = item.get("title") or item.get("chapter") or item.get("rhythmic")
    if not title:
        return None
    paragraphs = item.get("paragraphs") or item.get("content") or item.get("para")
    if not paragraphs:
        return None
    if isinstance(paragraphs, str):
        paragraphs = [paragraphs]
    if not isinstance(paragraphs, list):
        return None
    author = item.get("author") or ""
    if isinstance(author, str):
        author = author.replace("（唐）", "").replace("(唐)", "").strip()
        author = re.sub(r"^(?:先秦|两汉|兩漢|魏晋|魏晉|南北朝|隋|唐|宋|元|明|清)(?:代)?[：:]", "", author).strip()
    keep = {"title": title, "author": author, "dynasty": infer_dynasty(path, item), "paragraphs": paragraphs}
    if item.get("rhythmic"):
        keep["rhythmic"] = item["rhythmic"]
    return keep


def poetry_paths(source):
    """蒙学与唐诗三百首优先，保证常见篇目排在前面。"""
    seen_paths = set()
    ordered = []
    for path in [
        source / "蒙学",
        source / "全唐诗" / "唐诗三百首.json",
        source / "宋词" / "ci.song.300.json",
    ]:
        if path.is_file():
            ordered.append(path)
            seen_paths.add(path.resolve())
        elif path.is_dir():
            for child in sorted(path.rglob("*.json")):
                ordered.append(child)
                seen_paths.add(child.resolve())
    for path in sorted(source.rglob("*.json")):
        if path.resolve() in seen_paths:
            continue
        if any(part in SKIP_POETRY for part in path.parts):
            continue
        ordered.append(path)
    return ordered


def _shard_name(text):
    """取首字 unicode 十六进制作为分片名；空则 other。"""
    if not text:
        return "other"
    return format(ord(text[0]), "x")


def build_poetry_index(poetry_path):
    """
    生成分片索引目录 poetry-idx/：
    - t/<首字>.tsv 按标题首字
    - a/<首字>.tsv 按作者首字
    每行：offset\\tlength\\ttitle\\tauthor\\tsnippet
    """
    base = poetry_path.parent / "poetry-idx"
    if base.exists():
        shutil.rmtree(base)
    title_dir = base / "t"
    author_dir = base / "a"
    title_dir.mkdir(parents=True)
    author_dir.mkdir(parents=True)
    handles = {}
    dynasty_counts = {}
    author_counts = {}
    offset = 0
    try:
        catalog = (base / "all.tsv").open("w", encoding="utf-8")
        with poetry_path.open("rb") as src:
            while True:
                raw = src.readline()
                if not raw:
                    break
                length = len(raw)
                body = raw[:-1] if raw.endswith(b"\n") else raw
                try:
                    item = json.loads(body.decode("utf-8"))
                except (ValueError, UnicodeDecodeError):
                    offset += length
                    continue
                title = str(item.get("title") or "").replace("\t", " ").replace("\n", " ")
                author = str(item.get("author") or "").replace("\t", " ").replace("\n", " ")
                dynasty = str(item.get("dynasty") or "其他").replace("\t", " ").replace("\n", " ")
                paras = item.get("paragraphs") or []
                if isinstance(paras, list):
                    bits = [p if isinstance(p, str) else json.dumps(p, ensure_ascii=False) for p in paras[:2]]
                    snippet = " ".join(bits)
                else:
                    snippet = str(paras)
                snippet = snippet.replace("\t", " ").replace("\n", " ")[:80]
                row = f"{offset}\t{len(body)}\t{title}\t{author}\t{snippet}\n"
                catalog.write(f"{offset}\t{len(body)}\t{dynasty}\t{author}\t{title}\n")
                dynasty_counts[dynasty] = dynasty_counts.get(dynasty, 0) + 1
                dynasty_authors = author_counts.setdefault(dynasty, {})
                if author:
                    dynasty_authors[author] = dynasty_authors.get(author, 0) + 1
                for kind, text, folder in (("t", title, title_dir), ("a", author, author_dir)):
                    name = _shard_name(text)
                    key = (kind, name)
                    if key not in handles:
                        handles[key] = (folder / f"{name}.tsv").open("w", encoding="utf-8")
                    handles[key].write(row)
                offset += length
    finally:
        if "catalog" in locals():
            catalog.close()
        for out in handles.values():
            out.close()
    (base / "taxonomy.json").write_text(json.dumps(
        {"total": sum(dynasty_counts.values()), "dynasties": dynasty_counts, "authors": author_counts},
        ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    return base


def poetry(source, target):
    """导入诗词正文并生成检索索引。"""
    seen = set()
    target.parent.mkdir(parents=True, exist_ok=True)
    with target.open("w", encoding="utf-8") as out:
        for path in poetry_paths(source):
            for item in iter_poetry_items(path):
                keep = normalize_poem(item, path)
                if not keep:
                    continue
                packed = json.dumps(keep, ensure_ascii=False, separators=(",", ":"))
                digest = hashlib.sha1(packed.encode()).digest()
                if digest in seen:
                    continue
                seen.add(digest)
                out.write(packed + "\n")
    simplify_file(target)
    dedupe_jsonl(target)
    build_poetry_index(target)


def english(source, target):
    """只复制图片和音频到发布目录。"""
    assets = source / "assets" if (source / "assets").is_dir() else source
    if target.exists():
        shutil.rmtree(target)
    target.mkdir(parents=True, exist_ok=True)
    for path in assets.rglob("*"):
        if not path.is_file():
            continue
        if path.suffix.lower() not in {".mp3", ".wav", ".ogg", ".png", ".jpg", ".jpeg", ".gif", ".webp", ".svg"}:
            continue
        dest = target / path.relative_to(assets)
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(path, dest)


if __name__ == "__main__":
    p = argparse.ArgumentParser(description="生成开放学习库本地数据")
    p.add_argument("kind", choices=("hanzi", "ecdict", "poetry", "english", "poetry-index"))
    p.add_argument("source", type=Path)
    p.add_argument("target", type=Path, nargs="?", default=None)
    a = p.parse_args()
    if a.kind == "poetry-index":
        # source 即为 poetry.jsonl
        build_poetry_index(a.source)
    else:
        if a.target is None:
            raise SystemExit("需要 target 目录或文件")
        {"hanzi": hanzi, "ecdict": ecdict, "poetry": poetry, "english": english}[a.kind](a.source, a.target)
