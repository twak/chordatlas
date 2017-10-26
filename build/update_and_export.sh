cd /tmp
for r in jutils campskeleton siteplan chordatlas;
   do 
       git clone --branch bigsur_docker "https://github.com/twak/$r.git"; \
       cd $r; \
       mvn install; \
       cd ..; \
done

cd /tmp/chordatlas
mvn assembly:single

cp /tmp/chordatlas/target/*.jar /output
