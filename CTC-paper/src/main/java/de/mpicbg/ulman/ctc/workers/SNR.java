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

public class SNR extends AbstractDSmeasure
{
	///a constructor requiring connection to Fiji report/log services
	public SNR(final LogService _log)
	{ super(_log); }


	//---------------------------------------------------------------------/
	/// This is the main SNR calculator.
	@Override
	protected double calculateBottomStage()
	{
		//do the bottom stage
		//DEBUG//log.info("Computing the SNR bottom part...");
		double snr = 0.0;
		long videoCnt = 0; //how many videos were processed

		//go over all encountered videos and calc
		//their respective avg. SNRs and average them
		for (ImgQualityDataCache.videoDataContainer data : cache.cachedVideoData)
		{
			//shadows of the/short-cuts to the cache data
			final Vector<HashMap<Integer,Double>> avgFG = data.avgFG;
			final Vector<Double> avgBG = data.avgBG;
			final Vector<Double> stdBG = data.stdBG;

			//go over all FG objects and calc their SNRs
			long noFGs = 0;
			double l_snr = 0.; //local snr

			//over all time points
			for (int time=0; time < avgFG.size(); ++time)
			{
				//skip this frame if we cannot compute anything on it
				if (stdBG.get(time) == 0.0) continue;

				//over all objects, in fact use their avg intensities
				for (Double fg : avgFG.get(time).values())
				{
					l_snr += Math.abs(fg - avgBG.get(time)) / stdBG.get(time);
					++noFGs;
				}
			}

			//finish the calculation of the local average SNR
			if (noFGs > 0)
			{
				l_snr /= (double)noFGs;
				log.info("SNR for video "+data.video+": "+l_snr);

				snr += l_snr;
				++videoCnt;
			}
			else
				log.info("SNR for video "+data.video+": Couldn't calculate average SNR because there are missing labels.");
		}

		//summarize over all datasets:
		if (videoCnt > 0)
		{
			snr /= (double)videoCnt;
			log.info("SNR for dataset: "+snr);
		}
		else
			log.info("SNR for dataset: Couldn't calculate average SNR because there are missing labels.");

		return (snr);
	}
}
