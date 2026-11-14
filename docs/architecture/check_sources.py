"""Check local artifact links, source coverage, source hashes and SVG XML."""
from pathlib import Path
from urllib.parse import unquote, urlparse
import hashlib
import json
import re
import subprocess
import xml.etree.ElementTree as ET

OUT = Path(__file__).resolve().parent
ROOT = OUT.parent.parent
manifest = json.loads((OUT / 'source-manifest.json').read_text('utf-8'))
tracked = subprocess.check_output(['git', 'ls-files'], cwd=ROOT, text=True).splitlines()
main_files = {p for p in tracked if re.search(r'/src/(commonMain|desktopMain)/.*\.kt$', p)}
indexed = {f['path'] for f in manifest['files']}
assert main_files == indexed, (main_files - indexed, indexed - main_files)
test_files = {p for p in tracked if re.search(r'/src/(commonTest|desktopTest)/.*\.kt$', p)}
assert test_files == set(manifest['tests'])
for source in manifest['files']:
    assert hashlib.sha256((ROOT/source['path']).read_bytes()).hexdigest() == source['sha256'], source['path']
links = 0
for file in sorted(OUT.iterdir()):
    if file.suffix not in {'.svg','.md','.html','.py','.cjs','.json'}:
        continue
    data=file.read_bytes()
    assert not data.startswith(b'\xef\xbb\xbf'), file.name
    text=data.decode('utf-8')
    assert '\ufffd' not in text and '\r' not in text, file.name
    assert all(not line.endswith((' ', '\t')) for line in text.splitlines()), file.name
    targets=[]
    if file.suffix=='.svg':
        tree=ET.fromstring(text)
        ids=[node.attrib['id'] for node in tree.iter() if 'id' in node.attrib]
        assert len(ids)==len(set(ids)), file.name
        targets=[node.attrib['href'] for node in tree.iter() if 'href' in node.attrib]
        assert not any(node.tag.endswith('script') or node.tag.endswith('image') for node in tree.iter()), file.name
    if file.suffix=='.md':
        targets=re.findall(r'\]\(([^\s)]+)\)',text)+re.findall(r'^\[[^\]]+\]: (\S+)',text,re.M)
    if file.suffix=='.html':
        targets=re.findall(r'\bhref="([^"]+)"',text)
    for target in targets:
        if urlparse(target).scheme or target.startswith('#'):
            continue
        resolved=(file.parent/unquote(target.split('#')[0])).resolve()
        assert resolved.exists(), (file.name,target)
        links+=1

# Compare the explicit production graph to the build declarations.
build=(ROOT/'build.gradle.kts').read_text('utf-8')
expected={}
for mod, rhs in re.findall(r'"(:[^\"]+)" to (emptySet(?:<String>)?\(\)|setOf\([^)]*\))',build):
    expected[mod]=set(re.findall(r'"(:[^\"]+)"',rhs))
for mod,deps in expected.items():
    src=(ROOT/mod[1:].replace(':','/')/'build.gradle.kts').read_text('utf-8').split('desktopTest')[0]
    actual=set(re.findall(r'(?:api|implementation)\(project\("([^"]+)"\)\)',src))
    assert actual==deps, (mod,actual,deps)
assert len(expected)==11
print(json.dumps({'svgFiles':len(list(OUT.glob('*.svg'))),'mainFiles':len(indexed),'testFiles':len(test_files),'validLinks':links,'moduleGraphs':len(expected),'sourceCommit':manifest['sourceCommit']},ensure_ascii=False))
