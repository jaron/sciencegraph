#!/bin/sh

# Combines and extracts the sciencegraph database from a multi-part archive

zip -s 0 sciencegraph.db.zip --out whole.zip
unzip whole.zip
rm whole.zip