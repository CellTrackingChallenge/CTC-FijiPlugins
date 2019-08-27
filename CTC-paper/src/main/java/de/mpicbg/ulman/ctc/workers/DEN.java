/*
 * CC BY-SA 4.0
 *
 * The code is licensed with "Attribution-ShareAlike 4.0 International license".
 * See the license details:
 *     https://creativecommons.org/licenses/by-sa/4.0/
 *
 * Copyright (C) 2017 Vladimír Ulman
 */
package de.mpicbg.ulman.ctc.workers;

import org.scijava.log.LogService;

import java.util.Vector;
import java.util.HashMap;

public class DEN extends AbstractDSmeasure
{
	///a constructor requiring connection to Fiji report/log services
	public DEN(final LogService _log)
	{ super(_log); }


	//---------------------------------------------------------------------/
	/// This is the main DEN calculator.
	@Override
	protected double calculateBottomStage()
	{
		//do the bottom stage
		//DEBUG//log.info("Computing the DEN bottom part...");
		double den = 0.0;
		long fgCnt = 0; //how many videos were processed

		//go over all encountered videos and calc
		//their respective avg. DENs and average them
		for (ImgQualityDataCache.videoDataContainer data : cache.cachedVideoData)
		{
			//shadows of the/short-cuts to the cache data
			final Vector<HashMap<Integer,Float>> nearDistFG = data.nearDistFG;

			//number of objects whose neighbors were not found (within the distance)
			long noIsolatedFGs = 0;

			//go over all FG objects and calc their DENs
			long noFGs = 0;
			double l_den = 0.;

			//over all time points
			for (int time=0; time < nearDistFG.size(); ++time)
			{
				//over all objects, in fact use their avg intensities
				for (Float dist : nearDistFG.get(time).values())
				{
					l_den += (double)dist;
					++noFGs;
					if (dist == 50.0) ++noIsolatedFGs;
				}
			}

			//finish the calculation of the average
			if (noFGs > 0)
			{
				log.info("DEN for video "+data.video+": There is "+noIsolatedFGs+" ( "+100.0*noIsolatedFGs/(double)noFGs
					+" %) cells with no neighbor in the range of 50 voxels.");
				log.info("DEN for video "+data.video+": "+l_den/(double)noFGs);

				den += l_den;
				fgCnt += noFGs;
			}
			else
				log.info("DEN for video "+data.video+": Couldn't calculate average DEN because there are no cells labelled.");
		}

		//summarize over all datasets:
		if (fgCnt > 0)
		{
			den /= (double)fgCnt;
			log.info("DEN for dataset: "+den);
		}
		else
			log.info("DEN for dataset: Couldn't calculate average DEN because there are missing labels.");

		return (den);
	}
}
