##
# i18n.py
# Created on 10 May 2013 
# Copyright 2013 Michele Bonazza <emmepuntobi@gmail.com>
# 
# This file is part of WhatsHare.
# 
# WhatsHare is free software: you can redistribute it and/or modify it under
# the terms of the GNU General Public License as published by the Free Software
# Foundation, either version 3 of the License, or (at your option) any later
# version.
# 
# Foobar is distributed in the hope that it will be useful, but WITHOUT ANY
# WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
# A PARTICULAR PURPOSE. See the GNU General Public License for more details.
# 
# You should have received a copy of the GNU General Public License along with
# WhatsHare. If not, see <http://www.gnu.org/licenses/>.

import glob
import xml.etree.ElementTree as ET
import os
import sys

def parse_strings(xml_file):
	tree = ET.parse(xml_file)
	root = tree.getroot()
	all_strings = {}

	for child in root:
		name = child.get('name')
		if name in all_strings:
			print 'WARNING! duplicate key %s in %s' % (name, xml_file)
		all_strings[name] = child.text

	return all_strings

def print_missing(missing_set, default_strings):
	for key in missing_set:
		print '<string name="%s">%s</string>' % (key, default_strings[key])

path_to_default = '../res/values/strings.xml'
default_strings = parse_strings(path_to_default)
all_keys = set(default_strings)

values_folders = glob.glob('../res/values-[A-Za-z][A-Za-z]')
locales_to_check = ['values-it']

for values_folder in values_folders:
	if os.path.basename(os.path.normpath(values_folder)) in locales_to_check:
		try:
			with open(os.path.join(values_folder, 'strings.xml')) as strings_file:
				translated = parse_strings(strings_file)
				shared = set(translated).intersection(all_keys)
				missing = all_keys - shared
				if missing:
					print >> sys.stderr, 'Missing in %s:' % os.path.abspath(strings_file.name)
					print_missing(missing, default_strings)
					print

		except IOError:
			print "Ignoring %s as it's not an i18n folder" % values_folder
