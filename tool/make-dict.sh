#!/bin/bash

DIR=`cd \`dirname "${BASH_SOURCE[0]}"\` && pwd`

awk "BEGIN { prefix = \"('\"; print \"INSERT INTO wubi_words (code, word) VALUES\" }
{ for (i = 2; i <= NF; i++) { print prefix \$1 \"','\" \$i \"')\"; prefix = \",('\" } }" \
    $DIR/wubi.txt > $DIR/../app/src/main/res/raw/wubi.sql

