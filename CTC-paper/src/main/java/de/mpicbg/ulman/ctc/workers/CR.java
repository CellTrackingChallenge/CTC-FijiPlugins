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

public class CR extends AbstractDSmeasure
{
	///a constructor requiring connection to Fiji report/log services
	public CR(final LogService _log)
	{ super(_log); }


	//---------------------------------------------------------------------/
	/// This is the main CR calculator.
	@Override
	protected double calculateBottomStage()
	{
		//do the bottom stage
		//DEBUG//log.info("Computing the CR bottom part...");
		double cr = 0.0;
		long videoCnt = 0; //how many videos were processed

		//go over all encountered videos and calc
		//their respective avg. CRs and average them
		for (ImgQualityDataCache.videoDataContainer data : cache.cachedVideoData)
		{
			//shadows of the/short-cuts to the cache data
			final Vector<HashMap<Integer,Double>> avgFG = data.avgFG;
			final Vector<Double> avgBG = data.avgBG;

			//go over all FG objects and calc their CRs
			long noFGs = 0;
			double l_cr = 0.0;
			//over all time points
			for (int time=0; time < avgFG.size(); ++time)
			{
				//skip this frame if we cannot compute anything on it
				if (avgBG.get(time) == 0.0) continue;

				//over all objects, in fact use their avg intensities
				for (Double fg : avgFG.get(time).values())
				{
					l_cr += fg / avgBG.get(time);
					++noFGs;
				}
			}

			//finish the calculation of the average
			if (noFGs > 0)
			{
				l_cr /= (double)noFGs;
				log.info("CR for video "+data.video+": "+l_cr);

				cr += l_cr;
				++videoCnt;
			}
			else
				log.info("CR for video "+data.video+": Couldn't calculate average CR because there are missing labels.");
		}

		//summarize over all datasets:
		if (videoCnt > 0)
		{
			cr /= (double)videoCnt;
			log.info("CR for dataset: "+cr);
		}
		else
			log.info("CR for dataset: Couldn't calculate average CR because there are missing labels.");

		return (cr);
	}
}
