#!/bin/bash

echo "Cleaning Java Classes..."

rm -r ./bin/*

SOURCEPATH=$(ls -d ./src/*/*/)
CLASSPATH="./bin"
OUTDIR="./bin"
SOURCEFILES=$(ls ./src/*/*/*.java)

javac -d $OUTDIR -classpath $CLASSPATH -sourcepath $SOURCEPATH $SOURCEFILES
