# chordatlas: an urban data fusion platform

chordatlas is an urban procedural modeling and data fusion research platform, in particular it contains implementations of 3 papers that have/will be presented at various Siggraphs: [frankenGAN](http://geometry.cs.ucl.ac.uk/projects/2018/frankengan/), [bigSUR](http://geometry.cs.ucl.ac.uk/projects/2017/bigsur/), and [procEx](http://www.twak.co.uk/2011/04/interactive-architectural-modeling-with.html).

![interface pic which melts your eyes](https://raw.githubusercontent.com/twak/chordatlas/22b4513bb2e1ac8c9bc1034c4b187025346f5d1a/wiki/pic.jpg)

## run

if you have problems using this system, I would love to [know](https://github.com/twak/chordatlas/issues) so I can fix them!

1. install [java 1.8+](http://www.oracle.com/technetwork/java/javase/downloads/index.html)
1. download the [chordatlas binary](https://drive.google.com/open?id=1FC5K2kKP12jQLlE97YlwhzceTrLgxuDn)
1. run with `java -jar -Xmx10g chordatlas-0.0.1-SNAPSHOT.jar`  (the 10g says to use a 10Gb heap)

code is alpha / academic-grade: use at your own risk. other hints:

1. it will write a file `.tweed_config` into your home directory; this is the only state it creates outside of the data folders.
1. look at the command line for feedback (some operations like finding profiles or features are slow, and don't have progress bars)
1. use left mouse drag + WASD keys to navigate the 3D view
2. arrow keys change brightness and camera speed, page up and page down control fov
1. right mouse button selects things in the 3D view
1. intermediate results (meshes, rendered images, and detected features) are written to the project's data diretory. some of these (in the scratch folder) are deleted when you quit.
1. you can edit the visiblity of different layers with the layer-list check boxes

things get a bit more complicated for the bigsur optimisation and window detection:
1. install and license [gurobi optimiser 7.5](http://www.gurobi.com/downloads/gurobi-optimizer). ensure gurobi is on your library path before you start chordatlas.
1. if you want to detect features (doors, windows...), install [nvidia-docker](https://github.com/NVIDIA/nvidia-docker). tested on an 8gb nvidia card.

similarly for the frankenGAN texturing pipeline:
1. install [nvidia-docker](https://github.com/NVIDIA/nvidia-docker). tested on an 8gb nvidia card
1. run the following to start [bikegan](https://github.com/twak/bikegan) in the current directoy:
`nvidia-docker run -v $(pwd)/input:/home/user/bikegan/input -v $(pwd)/output:/home/user/bikegan/output -it --rm twak/bikegan`
1. in chordatlas' `file -> settings` menu, set the bikegan root to the current directory

## data

several datasets are available [here](http://geometry.cs.ucl.ac.uk/projects/2017/bigsur/data/). we don't have a license to distrubte the original data used in the paper, so the GIS data has been replaced by openstreetmap, and streetview photos have been udpated.
1. unzip the data
1. start chordatlas
1. select: `file`, `open...`, then select the tweed.xml in the root of the unzipped data
1. select the layer `panos`, then click `download` to fetch the panoramas from google. watch the command line for progress. this will take up a bunch of disk space (5mb per image).
1. (the mesh and gis data may cover different areas. you can select the `minimesh` layer and select `load all` to show all the mesh data; and "hide all" to hide it.)
1. find a block surrounded by panorama with mesh data; use the select tool, and right click on it to create a block mesh layer.
1. select `block` in the layer-list, and click `render panoramas` to create the 2d street-side images
1. select `block` in the layer-list, and click `find image features` to detect windows etc... with a CNN. this is slow, with limited feedback on the commandline.
1. select `block` in the layer-list, and click `find profiles`. wait for the profiles to become visible in 3d.
1. select the `profiles` layer and click `optimize` to run the optimization, and create the resulting mesh. cancelling the optimization in the pop-up will create the mesh using the current best-solution (if any).
1. the `file -> export obj` menu option will export all visible polygons

the adventurous hacker might try to compile their own datasets: 

1. [OpenStreetMap](wiki.openstreetmap.org) is a great source of building footprints, you'll need them in the GML format. (we also used [OS's Mastermap](https://www.ordnancesurvey.co.uk/business-and-government/products/mastermap-products.html))
1. [qgis](http://www.qgis.org) is an easy way to process filter different GIS data sources to create GML building footprints.
    * [instructions for osm import to qgis](http://learnosm.org/en/osm-data/osm-in-qgis/)
    * right click on the layer in qgis and use the filter option to remove non-building objects
1. [a tool to that might help you download panoramas](https://github.com/twak/panoscraper) from google streetview.
    * if you save the results as "panos/todo.list" in a project directory and create a panorama layer in the same place, then an option to download panoramas appears in the block layer options in chordatlas
1. [Segnet-Facade](https://github.com/jfemiani/facade-segmentation) is our CNN for finding features in street view images.
1. [here's some hacky code](https://github.com/twak/chordatlas/blob/master/src/org/twak/readTrace/ReadTrace.java) that might help you download meshes from online 3D services.

## build

to build:
- [java 1.8](http://openjdk.java.net/install/)
- [maven](https://maven.apache.org/)
- run `mvn install` for [jutils](https://github.com/twak/jutils), [campskeleton](https://github.com/twak/campskeleton), [siteplan](https://github.com/twak/siteplan)
- [gurobi 7.5](http://www.gurobi.com/downloads/gurobi-optimizer), installed into maven (` mvn install:install-file -Dfile=/opt/gurobi/gurobi751/linux64/lib/gurobi.jar -DgroupId=local_gurobi -DartifactId=gurobi -Dversion=751 -Dpackaging=jar`)
- run `mvn package` for [chordatlas](https://github.com/twak/chordatlas)

## cite

If you use this project, please cite the appropriate paper(s). Citations help us get more funding to continue these projects:

```
@misc{frankenGAN,
  Author = {Tom Kelly and Paul Guerrero and Anthony Steed and Peter Wonka and Niloy J. Mitra},
  Title = {FrankenGAN: Guided Detail Synthesis for Building Mass-Models Using Style-Synchonized GANs},
  Year = {2018},
  Eprint = {arXiv:1806.07179},
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
  month = November,
  year = {2017},
  articleno = {204},
  numpages = {16},
  url = {https://doi.org/10.1145/3130800.3130823},
  doi = {10.1145/3130800.3130823}
}
```

```
@article{procex,
 author = {Kelly, Tom and Wonka, Peter},
 title = {Interactive Architectural Modeling with Procedural Extrusions},
 journal = {ACM Trans. Graph.},
 issue_date = {April 2011},
 volume = {30},
 number = {2},
 month = apr,
 year = {2011},
 issn = {0730-0301},
 pages = {14:1--14:15},
 articleno = {14},
 numpages = {15},
 url = {http://doi.acm.org/10.1145/1944846.1944854},
 doi = {10.1145/1944846.1944854},
 acmid = {1944854},
 publisher = {ACM},
 address = {New York, NY, USA},
 keywords = {Procedural modeling, roof modeling, urban modeling},
}
```
