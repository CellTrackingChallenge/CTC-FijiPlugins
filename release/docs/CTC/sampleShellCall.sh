# example of calling the Technical measures (TRA & SEG) from a Linux/Mac shell command line
your_path_to_Fiji_binary/ImageJ-macosx --headless --run "Technical measures" "resPath=\"foo1\",gtPath=\"foo2\",calcSEG=true,calcTRA=true"

# example of calling the Technical measures (TRA & SEG) from a Linux/Mac shell command line
your_path_to_Fiji_binary/ImageJ-macosx --headless --run "Biological measures" "resPath=\"foo1\",gtPath=\"foo2\",calcCT=true,calcTF=true,calcBCi=true,iForBCi=2,calcCCA=true"

# example of calling the Dataset measures from a Linux/Mac shell command line
your_path_to_Fiji_binary/ImageJ-linux64 --headless --run "Dataset measures" "imgPath=\"foo1\",annPath=\"foo2\",xRes=1.0,yRes=1.0,zRes=1.0,calcSNR=true,calcCR=true,calcHeti=true,calcHetb=true,calcRes=true,calcSha=true,calcDen=true,calcCha=true,calcOve=true,calcMit=true" 2>&1 | grep INFO > `mktemp -u OUTPUT_XXXX.txt`
