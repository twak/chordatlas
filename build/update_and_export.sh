#!/bin/bash

for r in jutils campskeleton siteplan chordatlas;
   do 
       cd /tmp/$r; \
       git pull; \
       mvn install; \
done

cd /tmp/chordatlas
mvn assembly:single

mkdir -p /output
cp /tmp/chordatlas/target/*with-dependencies.jar /output
