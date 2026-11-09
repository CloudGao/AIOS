import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
class PhysicalFixResources(unittest.TestCase):
 def test_translations_and_placeholders(self):
  for module, name in [("workspace", "workspace_home.xml"), ("agent", "schedule_results.xml")]:
   values=[]
   for locale in ["values", "values-zh-rCN", "values-zh-rTW"]:
    entries=ET.parse(ROOT/"android"/module/"res"/locale/name).getroot()
    data={e.attrib["name"]: "".join(e.itertext()) for e in entries}
    self.assertEqual(len(entries),len(data));self.assertTrue(all(data.values()));values.append(data)
   for translated in values[1:]:
    self.assertEqual(values[0].keys(),translated.keys())
    for key,text in values[0].items():
     self.assertEqual(re.findall(r"%[0-9]+\$[sd]",text),re.findall(r"%[0-9]+\$[sd]",translated[key]),key)
if __name__ == "__main__": unittest.main()
