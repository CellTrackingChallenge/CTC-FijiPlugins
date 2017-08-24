#!/bin/bash

# go over all particpants folders with submitted results
for i in isbi???; do

	# find folders with results (that is, folders with no results were removed)
	find "$i" -name "??_RES" | cut -d'/' -f2- | while read j; do

		# create name of counter-parting GT dataset
		GTdir=`dirname $j`/`basename $j RES`GT;

		echo Working on:  ${i}/${j}

		# have we processed this one already?
		grep -q "${i}/${j}/" OUTPUT*
		if [ $? -eq 1 ]; then
			# no, process it now (results are stored as OUTPUT* files in the current working directory)
			. FIJI_run.cmd /data/ISBI2015/Private/ChallengeGT/${GTdir}/ ${i}/${j}/
		else
			# yes, we have result for this already
			echo skipped
		fi
	done
done
