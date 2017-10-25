#johns marvellous vision framework
FROM ubuntu:16.04
#jfemiani/segnet-facade:cuda8-cudnn3

# OpenGL on host pls (from https://github.com/plumbee/nvidia-virtualgl/blob/master/Dockerfile)
ENV VIRTUALGL_VERSION 2.5.2

RUN apt-get update && apt-get install -y --no-install-recommends \
    libglu1-mesa-dev mesa-utils curl ca-certificates xterm && \
    curl -sSL https://downloads.sourceforge.net/project/virtualgl/"${VIRTUALGL_VERSION}"/virtualgl_"${VIRTUALGL_VERSION}"_amd64.deb -o virtualgl_"${VIRTUALGL_VERSION}"_amd64.deb && \
    dpkg -i virtualgl_*_amd64.deb && \
    /opt/VirtualGL/bin/vglserver_config -config +s +f -t && \
    rm virtualgl_*_amd64.deb && \
    apt-get clean && \
    apt-get remove -y curl ca-certificates && \
    rm -rf /var/lib/apt/lists/*

# nvidia-docker links
LABEL com.nvidia.volumes.needed="nvidia_driver"
ENV PATH /usr/local/nvidia/bin:/opt/VirtualGL/bin:${PATH}
ENV LD_LIBRARY_PATH /usr/local/nvidia/lib:/usr/local/nvidia/lib64:${LD_LIBRARY_PATH}

# java

RUN apt update && apt install -y software-properties-common 
RUN add-apt-repository ppa:webupd8team/java && apt update

RUN echo oracle-java7-installer shared/accepted-oracle-license-v1-1 select true | /usr/bin/debconf-set-selections

RUN apt install -y oracle-java8-installer # your soul and your grandmother now belongs to Oracle
RUN apt install -y git curl maven openssh-client

# toms java ui/optimisation rubbish 

#gurobi

RUN mkdir ~/gurobi && cd ~/gurobi && wget http://packages.gurobi.com/7.5/gurobi7.5.1_linux64.tar.gz
RUN  cd ~/gurobi && tar -xvzf gurobi7.5.1_linux64.tar.gz

ENV PATH ~/gurobi/gurobi751/linux64/bin:$PATH
ENV GUROBI_HOME ~/gurobi/gurobi751/linux64/bin:$PATH

#WORKDIR ~

#RUN for r in jutils campskeleton siteplan; \
#    do \
#	git clone --branch bigsur_docker "https://github.com/twak/$r.git"; \
#    	cd $r; \
#	mvn compile install; \
#	cd ..; \
#    done 

