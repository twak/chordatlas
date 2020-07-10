# deps to build a ChordAtlas jar - we have a bunch of deps that have a bunch of deps... No-cache build:
# docker image build --no-cache=true --tag chordatlas --file Dockerfile .


FROM ubuntu:16.04
MAINTAINER twakelly@gmail.com

RUN apt update && apt install -y software-properties-common git curl maven openssh-client wget

# sun java
#RUN add-apt-repository ppa:webupd8team/java && apt update
#RUN echo oracle-java7-installer shared/accepted-oracle-license-v1-1 select true | /usr/bin/debconf-set-selections
#RUN apt install -y oracle-java8-installer # your soul and the good 3/4 of your grandmother now belong to Oracle

# openjdk java
RUN apt update && apt-get install -y openjdk-8-jdk

#gurobi

RUN mkdir /opt/gurobi && cd /opt/gurobi && wget http://packages.gurobi.com/9.0/gurobi9.0.2_linux64.tar.gz
RUN cd /opt/gurobi && tar -xvzf gurobi9.0.2_linux64.tar.gz

ENV PATH /opt/gurobi/gurobi902/linux64/bin:$PATH
ENV GUROBI_HOME /opt/gurobi/gurobi902/linux64/bin:$PATH

RUN mvn install:install-file -Dfile=/opt/gurobi/gurobi902/linux64/lib/gurobi.jar -DgroupId=local_gurobi -DartifactId=local_gurobi -Dversion=local_gurobi -Dpackaging=jar

WORKDIR /tmp

# clone, build and install each of the projects. if we are using stable, shouldn't be required?
#RUN for r in jutils campskeleton  siteplan; \
#    do \
#		git clone --branch stable "https://github.com/twak/$r.git"; \
#    	cd $r; \
#		mvn install; \
#		cd ..; \
#    done

WORKDIR /tmp

#https://stackoverflow.com/questions/36996046/how-to-prevent-dockerfile-caching-git-clone
#ADD https://api.github.com/repos/twak/chordatlas/git/refs/heads/stable /tmp/version.json
RUN git clone --branch stable "https://github.com/twak/chordatlas.git"
WORKDIR /tmp/chordatlas
RUN mvn -Dmaven.artifact.threads=64 package -T 64
WORKDIR /tmp

ENV PATH /tmp/chordatlas/build/:$PATH
RUN chmod +x /tmp/chordatlas/build/*.sh