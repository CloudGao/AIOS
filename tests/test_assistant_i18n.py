from pathlib import Path
import re, unittest, xml.etree.ElementTree as ET
R=Path(__file__).resolve().parents[1]
A=R/'android/agent'
class AssistantI18n(unittest.TestCase):
 def test_complete_catalogs(self):
  catalogs=[]
  for folder in ['values','values-zh','values-b+zh+Hant']:
   elements=ET.parse(A/'res'/folder/'assistant_strings.xml').getroot()
   data={e.attrib['name']:e.text for e in elements}
   self.assertEqual(len(elements),len(data));catalogs.append(data)
  for translated in catalogs[1:]:
   self.assertEqual(catalogs[0].keys(),translated.keys())
   for key,text in translated.items():
    self.assertEqual(sorted(re.findall(r'%[0-9]+\$[sd]',catalogs[0][key])), sorted(re.findall(r'%[0-9]+\$[sd]',text)),key)
 def test_no_missing_resources(self):
  names={e.attrib['name'] for resource in (A/'res/values').glob('*.xml')
         for e in ET.parse(resource).getroot() if e.tag=='string' and 'name' in e.attrib}
  for p in A.glob('*.java'):
   for name in re.findall(r'R\.string\.(assistant_\w+)',p.read_text()):self.assertIn(name,names,str(p))
 def test_ui_has_no_embedded_chinese(self):
  for name in ['AssistantOrbService','AssistantActivity','AssistantInputActivity','AssistantAttachmentActivity','AssistantCameraActivity','AssistantAttachments','AssistantConversation','AssistantVoiceFeedback','InterventionCards','UiSessionsActivity','UiViewerActivity']:
   text=(A/(name+'.java')).read_text()
   for literal in re.findall(r'"(?:[^"\\]|\\.)*"',text):self.assertIsNone(re.search('[\u4e00-\u9fff]',literal),name)
 def test_runtime_refreshes_current_locale(self):
  process=(R/'android/codex-runtime/CodexProcess.java').read_text()
  runtime=(R/'android/codex-runtime/RuntimeService.java').read_text()
  self.assertIn('getSystemService(android.app.LocaleManager.class).getSystemLocales()',process)
  self.assertEqual(process.count('.put("baseInstructions",phoneInstructions()'),2)
  self.assertIn('.put("baseInstructions",p.phoneInstructions())',runtime)
  self.assertIn('filtered.put("additionalContext"',runtime)
  self.assertIn('.put("kind","application")',runtime)
 def test_voice_control_is_not_translated_text(self):
  text=(A/'AssistantOrbService.java').read_text()
  self.assertNotIn('notice.startsWith',text)
  self.assertIn('s.optString("voice_state").equals("listening")',text)
if __name__=='__main__':unittest.main()
