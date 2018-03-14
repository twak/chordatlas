# chordatlas: BigSUR implementation

chordatlas is an urban data fusion research platform from UCL, in particular it contains an implementation of [BigSUR](http://geometry.cs.ucl.ac.uk/projects/2017/bigsur/). this [video](https://youtu.be/FMCp-D9rEgI?t=6m28s) shows the system in use.

![interface pic which melts your eyes](https://raw.githubusercontent.com/twak/chordatlas/22b4513bb2e1ac8c9bc1034c4b187025346f5d1a/wiki/pic.jpg)

## run

1. install [java 1.8+](http://www.oracle.com/technetwork/java/javase/downloads/index.html)
1. download the [chordatlas binary](https://drive.google.com/open?id=1FC5K2kKP12jQLlE97YlwhzceTrLgxuDn)
1. run with `java -jar chordatlas-0.0.1-SNAPSHOT.jar`
1. if you want to run the optimisation step, install and license [gurobi optimiser 7.5](http://www.gurobi.com/downloads/gurobi-optimizer). ensure gurobi is on your library path.
1. if you want to detect features (doors, windows...), install [nvidia-docker](https://github.com/NVIDIA/nvidia-docker). you will also need a 8gb nvidia card.

it will write a file `.tweed_config` into your home directory; this is the only state it creates outside of the data folders.
code is alpha / academic-grade: use at your own risk. hints:

1. look at the command line for feedback (some operations like finding profiles or features are slow, and don't have progress bars)
1. use left mouse drag + WASD keys to navigate
2. arrow keys change brightness and camera speed
1. right mouse button selects things in the 3D view
1. right click on a building footprint to import a mesh
1. intermediate results (meshes, rendered images, and detected features) are written to the project's data diretory. some of these (in the scratch folder) are deleted when you quit.
1. you can edit the visiblity of different layers with the layer-list check boxes

## data

we don't have a license to distrubte the complete data used in the paper. as we reprocess the data, the datasets will appear [in this folder](https://drive.google.com/drive/u/0/folders/1Mj4samNAeQIA_l7UieE2O01PO18PnFt-). 
1. unzip the data
1. start chordatlas
1. select: file, open..., then select the tweed.xml in the root of the unzipped data
1. select the layer "panos", then click "download" to fetch the panoramas from google. watch the command line for progress.
1. find the block surrounded by panoramas; right click on it to create a block mesh layer
1. select "block" in the layer-list, and click "render panoramas" to create the 2d street-side images
1. select "block" in the layer-list, and click "find image features" to detect windows etc... with a CNN. this is slow, with limited feedback on the commandline.
1. select "block" in the layer-list, and click "find profiles". wait for the profiles to become visible in 3d.
1. select the profiles and click "optimize" to run the optimization, and create the resulting mesh.
1. the "file -> export obj" option will export all visible polygons

for the adventurous hacker: 
1. [OpenStreetMap](wiki.openstreetmap.org) is a great source of building footprints, you'll need them in the GML format. (we also used [OS's Mastermap](https://www.ordnancesurvey.co.uk/business-and-government/products/mastermap-products.html))
1. [qgis](http://www.qgis.org) is an easy way to process filter different GIS data sources to create GML building footprints.
  * [instructions for osm import to qgis](http://learnosm.org/en/osm-data/osm-in-qgis/)
  * right click on the layer in qgis and use the filter option to remove non-building objects
1. [a tool to that might help you download panoramas](https://github.com/twak/panoscraper) from google streetview.
  * if you save the results as "panos/todo.list" in a project directory, create a panorama layer in the sam eplace an option
to download panoramas appears in the block layer options in chordatlas
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
