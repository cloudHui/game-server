import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
LAYOUT_STYLESHEETS = (
    ROOT / "hub/src/main/resources/static/pages/learning/css/viewport-fit.css",
    ROOT / "web/src/main/resources/static/pages/learning/css/viewport-fit.css",
)


class LearningLayoutOverflowTest(unittest.TestCase):
    def test_learning_page_does_not_force_viewport_clipping(self):
        for stylesheet in LAYOUT_STYLESHEETS:
            with self.subTest(stylesheet=stylesheet):
                css = stylesheet.read_text(encoding="utf-8")

                self.assertNotRegex(
                    css,
                    r"(?m)^\s*overflow:\s*hidden\s*;",
                    "layout policy must not hide overflowing learning content",
                )
                self.assertNotRegex(
                    css,
                    r"(?m)^\s*height:\s*calc\(100dvh\s*-\s*(?:72|62)px\)\s*;",
                    "page content must not be locked to viewport height",
                )
                self.assertRegex(
                    css,
                    r"\.shell\s*\{[^}]*min-height:\s*100dvh",
                    "shell must grow with content",
                )
                self.assertRegex(
                    css,
                    r"\.content\s*\{[^}]*height:\s*auto",
                    "content must use natural height",
                )


if __name__ == "__main__":
    unittest.main()
