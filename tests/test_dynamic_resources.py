from pathlib import Path
import re,unittest,xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
class ResourceTests(unittest.TestCase):
 def test_localized_contract(self):
  for module,filename in [('agent','sharing_capabilities.xml'),('core-runtime','sharing_capabilities.xml'),('codex-runtime','extensions.xml')]:
   root=ROOT/'android'/module/'res';base={e.attrib['name']:e.text or '' for e in ET.parse(root/'values'/filename).getroot()}
   for locale in ('values-zh','values-b+zh+Hant'):
    values={e.attrib['name']:e.text or '' for e in ET.parse(root/locale/filename).getroot()}
    self.assertEqual(base.keys(),values.keys())
    for key in base:self.assertEqual(sorted(re.findall(r'%[0-9]+\$[sd]',base[key])),sorted(re.findall(r'%[0-9]+\$[sd]',values[key])),key)
if __name__=='__main__':unittest.main()
