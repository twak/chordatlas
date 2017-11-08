# !! this is a work in progress, it should be easier to build/use by the end of November !!

# chordatlas: BigSUR implementation

chordatlas is an urban data fusion research platform from UCL, in particular it contains an implementation of [BigSUR](http://geometry.cs.ucl.ac.uk/projects/2017/bigsur/).

## run

1. install and license [gurobi optimiser 7.5](http://www.gurobi.com/downloads/gurobi-optimizer). ensure gurobi is on your library path.
2. download the [chordatlas binary](https://drive.google.com/open?id=0B6r_mUgXfBLdUXhndkR0ZFYxNzA)
3. run with `java -jar chordatlas-0.0.1-SNAPSHOT-jar-with-dependencies.jar`

it will write a file `.tweed_config` into your home directory; this is the only state it creates outside of the data folders.
code is alpha "academic grade": use at your own risk...

## data

we don't have a license to distrubte the data used in the paper. [here]() is a small project that you can unzip and run. [a video]() might help with the interface.

[here is a tool](https://github.com/twak/panoscraper) that might help you download panoramas.

## build

A simple way to build the binary (jar) is to use the [docker container](https://hub.docker.com/r/twak/chordatlas/), this will dump the output jar into the current directory:
```
docker run -v ${PWD}:/output twak/chordatlas update_and_export.sh
```

A more complex way is to install the deps:
- [gurobi 7.5](http://www.gurobi.com/downloads/gurobi-optimizer)
- [java 1.8](http://openjdk.java.net/install/)
- [maven](https://maven.apache.org/)
- run `mvn install` for [jutils](https://github.com/twak/jutils), [campskeleton](https://github.com/twak/campskeleton), [siteplan](https://github.com/twak/siteplan)
- run `mvn package assembly:single` for [chordatlas]()

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
