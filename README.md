Welcome
-------
This is a repository with Java source codes of the [Fiji](http://fiji.sc) tools related to the [Cell Tracking Challenge](http://www.celltrackingchallenge.net), and to the quantitative evaluation of biomedical tracking in general. In particular, one can find here:

* Technical (developer-oriented) tracking measures: TRA, SEG
* Biological (user-oriented) measures: CT, TF, BC(i), CCA
* Dataset quality measures: SNR, CR, Hetb, Heti, Res, Sha, Den, Cha, Ove, Mit
* Tracking accuracy evaluation with general [Acyclic Oriented Graphs Measure (AOGM)](http://journals.plos.org/plosone/article?id=10.1371/journal.pone.0144959)
* [_A Fiji Tool for Automatic Fusion of Segmentation and Tracking Labels_](http://labels2017.org/wp-content/uploads/2017/07/LABELS2017_14.pdf)

The binaries of the measures can be downloaded from the [official challenge website](http://www.celltrackingchallenge.net). The binary of the fusion tool can be found on this website in the [`release` **folder**](https://github.com/xulman/CTC-FijiPlugins/tree/master/release).

All the tools are in the form of a Fiji GUI plugins. However, owing to the Fiji capabilities, it is possible to call the tools also from command line, in a batch mode. Notes on how to install the binaries just follows.

The ideas, that are implemented in the tools, are product of a collective collaboration between [CIMA](http://www.cima.es), [CBIA](http://cbia.fi.muni.cz), [Erasmus MC](https://www.erasmusmc.nl/oic/?lang=en), [UC3M](https://www.uc3m.es), [CSBD](http://www.csbdresden.de/) and [MPI-CBG](http://mpi-cbg.de) groups.

The tools were developed and the page is maintained by [Vladimír Ulman](http://www.fi.muni.cz/~xulman/). The SEG, TRA/AOGM measure was originally developed in C++ by [Martin Maška](http://cbia.fi.muni.cz/).


Fresh Fiji installation:
------------------------
1. Download recent version of Fiji from [http://fiji.sc/](http://fiji.sc/)
1. Install it, and pay attention where (which Folder) it got installed into
1. Locate the folder where Fiji got installed, say it is folder `FIJIROOT`
1. Download binary of the tool, and place it into folder: `FIJIROOT/plugins`


Upgrading existing Fiji installation:
-------------------------------------
Proceed only with steps 3 and 4.


License
--------
The tools are licensed with the [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/).


Notes
------
Once installed, one can find the tools in the Fiji, in the _Plugins_ menu (and in the _Cell Tracking Challenge_ sub-menu). Contact (ulman při mpi-cbg.de) for help on how to use it, or do batch mode processing, or find hints in the `scripts` sub\[sub*\]folder.
