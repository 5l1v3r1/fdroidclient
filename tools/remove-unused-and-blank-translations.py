#!/usr/bin/env python3

# This script removes strings from the translated files that are not useful:
#  * translations for strings that are no longer used
#  * empty translated strings, English is better than no text at all

import glob
import os
import re
from xml.etree import ElementTree

resdir = os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main', 'res')
sourcepath = os.path.join(resdir, 'values', 'strings.xml')

strings = set()
for e in ElementTree.parse(sourcepath).getroot().findall('.//string'):
    name = e.attrib['name']
    strings.add(name)

for d in sorted(glob.glob(os.path.join(resdir, 'values-*'))):

    str_path = os.path.join(d, 'strings.xml')
    if not os.path.exists(str_path):
        continue

    header = ''
    with open(str_path, 'r') as f:
        header = f.readline()

    # handling XML namespaces in Python is painful, just remove them, they
    # should not be in the translation files anyway
    with open(str_path, 'rb') as fp:
        contents = fp.read()
    contents = contents.replace(b' tools:ignore="UnusedResources"', b'') \
                       .replace(b' xmlns:tools="http://schemas.android.com/tools"', b'')
    root = ElementTree.fromstring(contents)

    for e in root.findall('.//string'):
        name = e.attrib['name']
        if name not in strings:
            root.remove(e)
        if not e.text:
            root.remove(e)

    for e in root.findall('.//plurals'):
        for item in e.findall('item'):
            if not item.text:
                e.remove(item)

    result = re.sub(r' />', r'/>', ElementTree.tostring(root, encoding='utf-8').decode('utf-8'))

    with open(str_path, 'w+') as f:
        f.write(header)
        f.write(result)
        f.write('\n')
