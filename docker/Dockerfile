FROM jfemiani/segnet-facade:cuda8-cudnn3

RUN apt update && apt install -y software-properties-common 
RUN add-apt-repository ppa:webupd8team/java && apt update

RUN echo oracle-java7-installer shared/accepted-oracle-license-v1-1 select true | /usr/bin/debconf-set-selections

RUN apt install -y oracle-java8-installer # your soul and your grandmoth now belongs to Oracle
RUN apt install -y git curl maven openssh-client

RUN for r in jutils campskeleton siteplan chordatlas \
    do git clone --branch bigsur "https://github.com/twak/$r.git"; \
    done

#RUN git clone https://github.com/twak/chordatlas.git
#RUN git clone https://github.com/twak/jutils.git
#RUN git clone https://github.com/twak/campskeleton.git
#RUN git clone https://github.com/twak/.git

