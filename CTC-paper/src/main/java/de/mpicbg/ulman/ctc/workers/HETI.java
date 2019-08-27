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

public class HETI extends AbstractDSmeasure
{
	///a constructor requiring connection to Fiji report/log services
	public HETI(final LogService _log)
	{ super(_log); }


	//---------------------------------------------------------------------/
	/// This is the main HETI calculator.
	@Override
	protected double calculateBottomStage()
	{
		//do the bottom stage
		//DEBUG//log.info("Computing the HETI bottom part...");
		double heti = 0.0;
		long videoCnt = 0; //how many videos were processed

		//go over all encountered videos and calc
		//their respective avg. HETIs and average them
		for (ImgQualityDataCache.videoDataContainer data : cache.cachedVideoData)
		{
			//shadows of the/short-cuts to the cache data
			final Vector<HashMap<Integer,Double>> avgFG = data.avgFG;
			final Vector<HashMap<Integer,Double>> stdFG = data.stdFG;
			final Vector<Double> avgBG = data.avgBG;

			//go over all FG objects and calc their CRs
			long noFGs = 0;
			double l_heti = 0.0;
			//over all time points
			for (int time=0; time < avgFG.size(); ++time)
			{
				//over all objects
				for (Integer fgID : avgFG.get(time).keySet())
				{
					double denom = Math.abs(avgFG.get(time).get(fgID) - avgBG.get(time));
					//exclude close-to-zero denominators (that otherwise escalate/outlay the average)
					if (denom > 0.01)
					{
						l_heti += stdFG.get(time).get(fgID) / denom;
						++noFGs;
					}
				}
			}

			//finish the calculation of the average
			if (noFGs > 0)
			{
				l_heti /= (double)noFGs;
				log.info("HETI for video "+data.video+": "+l_heti);

				heti += l_heti;
				++videoCnt;
			}
			else
				log.info("HETI for video "+data.video+": Couldn't calculate average HETI because there are no cells labelled.");
		}

		//summarize over all datasets:
		if (videoCnt > 0)
		{
			heti /= (double)videoCnt;
			log.info("HETI for dataset: "+heti);
		}
		else
			log.info("HETI for dataset: Couldn't calculate average HETI because there are missing labels.");

		return (heti);
	}
}
