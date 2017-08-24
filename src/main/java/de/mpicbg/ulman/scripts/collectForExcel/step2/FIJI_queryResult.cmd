# example call
# . FIJI_queryResult.cmd isbiABC Fluo-C3DL-MDA231/01 2

# isbi???
login=$1

#e.g., Fluo-N2DL-HeLa/01
dataset=$2

# number of the measure:
# 1 TRA
# 2 SEG
# 3 CT
# 4 TF
# 5 BCi
# 6 CCA
measure=$3

# note the 'tail' command that assures that only one result is used in the end
grep "$login/$dataset" results.txt | tail -n 1 | cut -d',' -f13-18 | tr -d ',]' | cut -d' ' -f$((measure+1))

