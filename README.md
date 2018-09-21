# chordatlas: data-driven urban procedural modeling

chordatlas is an urban procedural modeling and data fusion research platform. [video](https://youtu.be/Jz8q09r-RFg). It contains implementations of 3 projects that have/will be presented at various Siggraphs: [frankengan](http://geometry.cs.ucl.ac.uk/projects/2018/frankengan/), [bigSUR](http://geometry.cs.ucl.ac.uk/projects/2017/bigsur/), and [procEx](http://www.twak.co.uk/2011/04/interactive-architectural-modeling-with.html).

![interface pic which melts your eyes](https://raw.githubusercontent.com/twak/chordatlas/22b4513bb2e1ac8c9bc1034c4b187025346f5d1a/wiki/pic.jpg)

## run

if you have problems using this system, I would love to [know](https://github.com/twak/chordatlas/issues) so I can fix them!

1. install [java 1.8+](http://www.oracle.com/technetwork/java/javase/downloads/index.html)
1. download the [chordatlas binary](https://drive.google.com/open?id=1FC5K2kKP12jQLlE97YlwhzceTrLgxuDn)
1. run with `java -jar -Xmx10g chordatlas-0.0.1-SNAPSHOT.jar`  (code is academic grade: use at your own risk. the 10g says to use a 10Gb heap)
1. read the [user interface instructions](https://github.com/twak/chordatlas/wiki/interface).

there are additional requirements and instructions for [bigSUR](https://github.com/twak/chordatlas/wiki/bigSUR-details) (creates clean meshes from noisy meshes, streetview info, and maps) and [frankenGAN](https://github.com/twak/chordatlas/wiki/frankenGAN-details) (textures the meshes, generates feature locations).

more adventurous hackers may be interested in compiling their [own datasets](https://github.com/twak/chordatlas/wiki/datasets) or [building the system](https://github.com/twak/chordatlas/wiki/build-instructions).

## cite

if you use this project in your own publications, please cite the appropriate paper(s). this helps us get more funding to improve the systems:

```
@article{frankengan,
  title = {FrankenGAN: Guided Detail Synthesis for Building Mass-Models Using Style-Synchonized GANs},
  author = {Tom Kelly and Paul Guerrero and Anthony Steed and Peter Wonka and Niloy J. Mitra},
  year = {2018},
  journal = {{ACM} Transactions on Graphics},
  volume = {37},
  number = {6},
  doi = {10.1145/3272127.3275065},
}
```

```
@article{bigsur,
  title   = {BigSUR: Large-scale Structured Urban Reconstruction},
  author  = {Tom Kelly and John Femiani and Peter Wonka and Niloy J. Mitra},
  year    = {2017},
  journal = {{ACM} Transactions on Graphics},
  volume = {36},
  number = {6},
  doi = {10.1145/3130800.3130823}
}
```

```
@article{procex,
 title = {Interactive Architectural Modeling with Procedural Extrusions},
 author = {Tom Kelly and Peter Wonka},
 year = {2011},
 journal = {{ACM} Transactions on Graphics},
 volume = {30},
 number = {2},
 doi = {10.1145/1944846.1944854},
}
```
