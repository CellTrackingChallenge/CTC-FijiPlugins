#!/bin/bash

# collect results from the evaluation, i.e. collect the summary line from all
# outputs (over all results available) of the 'Tracking performance measures' plugin
grep "Tracking performance" ../step1/ISBI201?/OUTPUT* > results.txt

# create CSV-like (comma separated) text files (to be imported into Excel, for instance)
TRA=TRA_forExcel.txt
SEG=SEG_forExcel.txt
 CT=CT_forExcel.txt
 TF=TF_forExcel.txt
BCi=BCi_forExcel.txt
CCA=CCA_forExcel.txt

# init all result files
cat /dev/null > "$TRA";
cat /dev/null > "$SEG";
cat /dev/null > "$CT";
cat /dev/null > "$TF";
cat /dev/null > "$BCi";
cat /dev/null > "$CCA";

for L in COM-US CUL-UK CUNI-CZ FR-Be-GE FR-Ro-GE HD-Har-GE HD-Hau-GE "IMCB-SG (1)" "IMCB-SG (2)" KIT-GE "KTH-SE (1)" "KTH-SE (2)" "KTH-SE (3)" "KTH-SE (4)" LEID-NL MU-CZ NOTT-UK PAST-FR UP-PT UPM-ES UZH-CH; do
	lgin=`grep "$L" nicknames.txt | cut -d' ' -f1`

	# init all results (start a new line)
	echo -n "$L," >> "$TRA";
	echo -n "$L," >> "$SEG";
	echo -n "$L," >> "$CT";
	echo -n "$L," >> "$TF";
	echo -n "$L," >> "$BCi";
	echo -n "$L," >> "$CCA";

	# enumerate all datasets
	for D in DIC-C2DH-HeLa Fluo-C2DL-MSC Fluo-C3DH-H157 Fluo-C3DL-MDA231 Fluo-N2DH-GOWT1 Fluo-N2DL-HeLa Fluo-N3DH-CE Fluo-N3DH-CHO Fluo-N3DL-DRO PhC-C2DH-U373 PhC-C2DL-PSC Fluo-N2DH-SIM+ Fluo-N3DH-SIM+; do
	 for d in 01 02; do
		dataset="$D/$d"
		tra=`. FIJI_queryResult.cmd $lgin $dataset 1`
		seg=`. FIJI_queryResult.cmd $lgin $dataset 2`
		 ct=`. FIJI_queryResult.cmd $lgin $dataset 3`
		 tf=`. FIJI_queryResult.cmd $lgin $dataset 4`
		bci=`. FIJI_queryResult.cmd $lgin $dataset 5`
		cca=`. FIJI_queryResult.cmd $lgin $dataset 6`

		# some pretty printing of failures...
		if [ "Xtra$X" == "XX" ]; then tra="-"; fi
		if [ "Xseg$X" == "XX" ]; then seg="-"; fi
		if [  "Xct$X" == "XX" ]; then  ct="-"; fi
		if [  "Xtf$X" == "XX" ]; then  tf="-"; fi
		if [ "Xbci$X" == "XX" ]; then bci="-"; fi
		if [ "Xcca$X" == "XX" ]; then cca="-"; fi

		# measure-wise, organize output "into columns" for Excel sheets
		# (there is multiple columns used for one dataset in the sheets)
		echo -n "$tra," >> "$TRA";
		echo -n "$seg," >> "$SEG";
		if [ $d == "01" ]; then
			echo -n "$ct,," >> "$CT";
			echo -n "$tf," >> "$TF";
			if [ $D == "Fluo-N2DL-HeLa" -o $D == "Fluo-N3DH-CE" -o $D == "PhC-C2DL-PSC" -o $D == "Fluo-N2DH-SIM+" -o $D == "Fluo-N3DH-SIM+" ]; then
				echo -n "$bci,," >> "$BCi";
				echo -n "$cca," >> "$CCA";
			fi
		else
			echo -n "$ct,,,," >> "$CT";
			echo -n "$tf,,," >> "$TF";
			if [ $D == "Fluo-N2DL-HeLa" -o $D == "Fluo-N3DH-CE" -o $D == "PhC-C2DL-PSC" -o $D == "Fluo-N2DH-SIM+" -o $D == "Fluo-N3DH-SIM+" ]; then
				echo -n "$bci,,,," >> "$BCi";
				echo -n "$cca," >> "$CCA";
			fi
		fi
	 done
	done

	# close the line after the result
	echo >> "$TRA";
	echo >> "$SEG";
	echo >> "$CT";
	echo >> "$TF";
	echo >> "$BCi";
	echo >> "$CCA";
done
