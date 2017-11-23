# chordatlas: BigSUR implementation

chordatlas is an urban data fusion research platform from UCL, in particular it contains an implementation of [BigSUR](http://geometry.cs.ucl.ac.uk/projects/2017/bigsur/).

## run

1. install [java 1.8+](http://www.oracle.com/technetwork/java/javase/downloads/index.html)
1. install and license [gurobi optimiser 7.5](http://www.gurobi.com/downloads/gurobi-optimizer). ensure gurobi is on your library path.
1. download the [chordatlas binary](https://drive.google.com/open?id=1FC5K2kKP12jQLlE97YlwhzceTrLgxuDn)
1. run with `java -jar chordatlas-0.0.1-SNAPSHOT-jar-with-dependencies.jar`

it will write a file `.tweed_config` into your home directory; this is the only state it creates outside of the data folders.
code is alpha "academic grade": use at your own risk...

## data

we don't have a license to distrubte the data used in the paper. eventually you'll be able to download a synthetic demo project [here](), and view [a video]() that might help with the interface.

for the adventurous hacker: 
1. [OpenStreetMap](wiki.openstreetmap.org) is a great source of building footprints, you'll need them in the GML format. (we also used [OS's Mastermap](https://www.ordnancesurvey.co.uk/business-and-government/products/mastermap-products.html))
1. [QGIS](http://www.qgis.org) is an easy way to process filter different GIS data sources to create GML building footprints.
1. [a tool to that might help you download panoramas](https://github.com/twak/panoscraper) from google streetview.
1. [Segnet-Facade](https://github.com/jfemiani/facade-segmentation) is our CNN for finding features in street view images.
1. [here's some hacky code](https://github.com/twak/chordatlas/blob/master/src/org/twak/readTrace/ReadTrace.java) that might help you download meshes from online 3D services.

## build

A simple way to build the binary (jar) is to use the [docker container](https://hub.docker.com/r/twak/chordatlas/), this will dump the output jar into the current directory:
```
docker run -v ${PWD}:/output twak/chordatlas update_and_export.sh
```

A more complex way is to install the deps:
- [java 1.8](http://openjdk.java.net/install/)
- [maven](https://maven.apache.org/)
- run `mvn install` for [jutils](https://github.com/twak/jutils), [campskeleton](https://github.com/twak/campskeleton), [siteplan](https://github.com/twak/siteplan)
- [gurobi 7.5](http://www.gurobi.com/downloads/gurobi-optimizer), installed into maven (` mvn install:install-file -Dfile=/opt/gurobi/gurobi751/linux64/lib/gurobi.jar -DgroupId=local_gurobi -DartifactId=gurobi -Dversion=751 -Dpackaging=jar`)
- run `mvn package` for [chordatlas]()

## cite

If you use this project, please cite:
```
@article{Kelly:SIGA:2017,
  title   = {BigSUR: Large-scale Structured Urban Reconstruction},
  author  = {Tom Kelly and John Femiani and Peter Wonka and Niloy J. Mitra},
  year    = {2017},
  journal = {{ACM} Transactions on Graphics},
  volume = {36},
  number = {6},
  month = November,
  year = {2017},
  articleno = {204},
  numpages = {16},
  url = {https://doi.org/10.1145/3130800.3130823},
  doi = {10.1145/3130800.3130823}
}
```
