FROM jfemiani/segnet-facade:cuda8-cudnn3

RUN apt update && apt install -y software-properties-common 
RUN add-apt-repository ppa:webupd8team/java && apt update

RUN echo oracle-java7-installer shared/accepted-oracle-license-v1-1 select true | /usr/bin/debconf-set-selections

RUN apt install -y oracle-java8-installer

# RUN apt install git curl

