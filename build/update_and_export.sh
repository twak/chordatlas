#!/bin/bash

#for r in jutils campskeleton siteplan chordatlas;
#   do
#       cd /tmp/$r; \
#       git pull; \
#       mvn install; \
#done

cd /tmp/chordatlas
mvn package -T 64

mkdir -p /output
cp /tmp/chordatlas/target/chordatlas-0.0.1.jar /output
