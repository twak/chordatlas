# !! this is a work in progress, it should be easier to build/use by the end of November !!

# chordatlas: BigSUR implementation

chordatlas is an urban data fusion research platform from UCL, in particular it contains an implementation of BigSUR. the webpage is [here](http://geometry.cs.ucl.ac.uk/projects/2017/bigsur/).

## run

1. install and license [Gurobi optimiser 7.5](http://www.gurobi.com/downloads/gurobi-optimizer). ensure gurobi is on your library path.
2. download the [binary]()
3. run with `java -jar chordatlas-0.0.1-SNAPSHOT-jar-with-dependencies.jar`

## data

we don't have a license to distrubte the data used in the paper. [here]() is a small project that you can unzip and run. [a video]() might help with the interface.

## build

A simple way to build the binary (jar) is to use docker, this will dump the output jar into the current directory:
```
docker run -v ${PWD}:/output twak/chordatlas update_and_export.sh
```

A more complex way is to install the deps:
- [gurobi 7.5](http://www.gurobi.com/downloads/gurobi-optimizer)
- [maven](https://maven.apache.org/)
- run `mvn install` for [jutils](https://github.com/twak/jutils), [campskeleton](https://github.com/twak/campskeleton), [siteplan](https://github.com/twak/siteplan)
- run `mvn assembly:single` for [chordatlas]()

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
