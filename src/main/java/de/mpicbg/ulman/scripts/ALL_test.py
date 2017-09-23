#@String gtPath
#@String resPath

from ij import IJ

IJ.run("All Measures", "gtpath="+gtPath+" respath="+resPath
       + " calctra=true calcseg=true calcct=true"
       + " calctf=true calcbci=true iforbci=2 calccca=true");
