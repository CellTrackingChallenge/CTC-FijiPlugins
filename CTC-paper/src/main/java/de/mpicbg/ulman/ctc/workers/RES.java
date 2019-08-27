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

public class RES extends AbstractDSmeasure
{
	///a constructor requiring connection to Fiji report/log services
	public RES(final LogService _log)
	{ super(_log); }


	//---------------------------------------------------------------------/
	/// This is the main RES calculator.
	@Override
	protected double calculateBottomStage()
	{
		//do the bottom stage
		//DEBUG//log.info("Computing the RES bottom part...");
		double res = 0.0;
		long fgCnt = 0; //how many videos were processed

		//go over all encountered videos and calc
		//their respective avg. RESes and average them
		for (ImgQualityDataCache.videoDataContainer data : cache.cachedVideoData)
		{
			//shadows of the/short-cuts to the cache data
			final Vector<HashMap<Integer,Long>> volumeFG = data.volumeFG;

			//go over all FG objects and calc their RESs
			long noFGs = 0;
			double l_res = 0.0;
			//over all time points
			for (int time=0; time < volumeFG.size(); ++time)
			{
				//over all objects
				for (Long vol : volumeFG.get(time).values())
				{
					l_res += (double)vol;
					++noFGs;
				}
			}

			//finish the calculation of the average
			if (noFGs > 0)
			{
				log.info("RES for video "+data.video+": "+l_res/(double)noFGs);

				res += l_res;
				fgCnt += noFGs;
			}
			else
				log.info("RES for video "+data.video+": Couldn't calculate average RES because there are no cells labelled.");
		}

		//summarize over all datasets:
		if (fgCnt > 0)
		{
			res /= (double)fgCnt;
			log.info("RES for dataset: "+res);
		}
		else
			log.info("RES for dataset: Couldn't calculate average RES because there are missing labels.");

		return (res);
	}
}
