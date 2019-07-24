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

public class OVE extends AbstractDSmeasure
{
	///a constructor requiring connection to Fiji report/log services
	public OVE(final LogService _log)
	{ super(_log); }


	//---------------------------------------------------------------------/
	/// This is the main OVE calculator.
	@Override
	protected double calculateBottomStage()
	{
		//do the bottom stage
		//DEBUG//log.info("Computing the OVE bottom part...");
		double ove = 0.0;
		long videoCnt = 0; //how many videos were processed

		//go over all encountered videos and calc
		//their respective avg. OVEs and average them
		for (ImgQualityDataCache.videoDataContainer data : cache.cachedVideoData)
		{
			//shadows of the/short-cuts to the cache data
			final Vector<HashMap<Integer,Long>> volumeFG = data.volumeFG;
			final Vector<HashMap<Integer,Long>> overlapFG = data.overlapFG;

			//go over all FG objects and calc their OVEs
			long noFGs = 0;
			double l_ove = 0.0;

			//over all time points (NB: no overlap possible for time==0)
			for (int time=1; time < overlapFG.size(); ++time)
			{
				//over all objects
				for (Integer fgID : overlapFG.get(time).keySet())
				{
					l_ove += (double)overlapFG.get(time).get(fgID) / (double)volumeFG.get(time).get(fgID);
					++noFGs;
				}
			}

			//finish the calculation of the average
			if (noFGs > 0)
			{
				l_ove /= (double)noFGs;
				log.info("OVE for video "+data.video+": "+l_ove);

				ove += l_ove;
				++videoCnt;
			}
			else
				log.info("OVE for video "+data.video+": Couldn't calculate average OVE because there are no cells labelled.");
		}

		//summarize over all datasets:
		if (videoCnt > 0)
		{
			ove /= (double)videoCnt;
			log.info("OVE for dataset: "+ove);
		}
		else
			log.info("OVE for dataset: Couldn't calculate average OVE because there are missing labels.");

		return (ove);
	}
}
