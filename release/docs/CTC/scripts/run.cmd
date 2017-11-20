# This is an overview of general ways of calling specific FiJi routines from command line.

# possibly cd to the FiJi binary
cd ~/Apps/Fiji.app/Contents/MacOS

# ======================================

# start as a menu item (directly via the plugin interface):
# (the prefered option for command line)
./ImageJ-macosx --run "Annotations Merging Tool"  "mergeModel=\"Threshold - flat weights\",filePath=\"/Users/ulman/job_spec.txt\",mergeThreshold=2.0,fileIdxFrom=9,fileIdxTo=9,outputPath=\"/Users/ulman/DATA/combinedGT__XXX.tif\""
./ImageJ-macosx --headless --run "Tracking performance measures"  "gtPath=\"foo1\",resPath=\"foo2\",calcTRA=true,calcSEG=true,calcCT=true,calcTF=true,calcBCi=true,iForBCi=2,calcCCA=true"

# ======================================

# start as a script:
# (the command actually only executes the script 'ALL_test.py', which executes the FiJi menu command 'Tracking performance measures', which actually does the job...)
./ImageJ-macosx --headless --run ./ALL_test.py "gtPath=\"$1\",resPath=\"$2\""

[ begin ALL_test.py
--------
#@String gtPath
#@String resPath

from ij import IJ

IJ.run("Tracking performance measures", "gtpath="+gtPath+" respath="+resPath
       + " calctra=true calcseg=true calcct=true"
       + " calctf=true calcbci=true iforbci=2 calccca=true");
--------
end test.py ]

# ======================================

# start as a class (executes the main() function):
# (geeky way that might feature some back-door initiation/start of the plugin
#  as different entry point can be used to start the plugin)
./ImageJ-macosx de/mpicbg/ulman/machineGTViaMarkers.class  foo 4
./ImageJ-macosx --headless de/mpicbg/ulman/plugin_CTCmeasures.class gtPath resPath

[ de -> /Users/ulman/devel/eclipse_workspace/CTC-FijiPlugins/target/classes/de ]
(the class may start-up the scijava ecosystem: final ImageJ ij = new net.imagej.ImageJ();
 but is not working for me now...)

# ======================================

# often it is useful to append: 2>&1 | grep INFO
# params are case-sensitive
# params are separated with commas ','
