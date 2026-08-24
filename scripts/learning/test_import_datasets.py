import importlib.util
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


MODULE_PATH = Path(__file__).with_name("import-datasets.py")
SPEC = importlib.util.spec_from_file_location("import_datasets", MODULE_PATH)
IMPORTER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(IMPORTER)


class PoetryDynastyTest(unittest.TestCase):
    def test_infers_dynasty_from_dataset_path_and_filename(self):
        cases = {
            "全唐诗/poet.tang.0.json": "唐",
            "全唐诗/poet.song.0.json": "宋",
            "宋词/ci.song.0.json": "宋",
            "元曲/yuanqu.json": "元",
            "五代诗词/huajianji/huajianji-1.json": "五代",
            "诗经/shijing.json": "先秦",
            "曹操诗集/caocao.json": "魏晋",
            "纳兰性德/纳兰性德诗集.json": "清",
        }
        for path, expected in cases.items():
            with self.subTest(path=path):
                self.assertEqual(expected, IMPORTER.infer_dynasty(Path(path), {}))

        self.assertEqual("元", IMPORTER.infer_dynasty(Path("元曲/yuanqu.json"), {"dynasty": "yuan"}))
        self.assertEqual("唐", IMPORTER.infer_dynasty(Path("蒙学/古文观止.json"), {"author": "唐代：李白"}))

    def test_normalizes_prefixed_author_and_para_content(self):
        poem = IMPORTER.normalize_poem(
            {"title": "例诗", "author": "清代：纳兰性德", "para": ["人生若只如初见"]},
            Path("纳兰性德/纳兰性德诗集.json"),
        )
        self.assertEqual("纳兰性德", poem["author"])
        self.assertEqual("清", poem["dynasty"])
        self.assertEqual(["人生若只如初见"], poem["paragraphs"])

    def test_index_contains_full_catalog_and_taxonomy(self):
        with tempfile.TemporaryDirectory() as folder:
            data = Path(folder) / "poetry.jsonl"
            rows = [
                {"title": "静夜思", "author": "李白", "dynasty": "唐", "paragraphs": ["床前明月光"]},
                {"title": "春夜喜雨", "author": "杜甫", "dynasty": "唐", "paragraphs": ["好雨知时节"]},
                {"title": "水调歌头", "author": "苏轼", "dynasty": "宋", "paragraphs": ["明月几时有"]},
            ]
            data.write_text("".join(json.dumps(row, ensure_ascii=False) + "\n" for row in rows), encoding="utf-8")
            index = IMPORTER.build_poetry_index(data)
            self.assertEqual(3, len((index / "all.tsv").read_text(encoding="utf-8").splitlines()))
            taxonomy = json.loads((index / "taxonomy.json").read_text(encoding="utf-8"))
            self.assertEqual(2, taxonomy["dynasties"]["唐"])
            self.assertEqual(1, taxonomy["authors"]["唐"]["李白"])


class DictionarySimplificationTest(unittest.TestCase):
    def test_dictionary_shards_are_simplified_before_publish(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            source = root / "ecdict.csv"
            target = root / "dictionary"
            source.write_text(
                "word,phonetic,definition,translation,tag,exchange\n"
                "rule,,,規則,,\n",
                encoding="utf-8",
            )

            with patch.object(IMPORTER, "simplify_file") as simplify:
                IMPORTER.ecdict(source, target)

            simplify.assert_called_once_with(target / "ru.jsonl")


if __name__ == "__main__":
    unittest.main()
