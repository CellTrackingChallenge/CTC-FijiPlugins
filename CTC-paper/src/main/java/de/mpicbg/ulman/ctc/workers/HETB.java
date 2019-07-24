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

public class HETB extends AbstractDSmeasure
{
	///a constructor requiring connection to Fiji report/log services
	public HETB(final LogService _log)
	{ super(_log); }


	//---------------------------------------------------------------------/
	/// This is the main HETB calculator.
	@Override
	protected double calculateBottomStage()
	{
		//do the bottom stage
		//DEBUG//log.info("Computing the HETB bottom part...");
		double hetb = 0.0;
		long videoCnt = 0; //how many videos were processed

		//go over all encountered videos and calc
		//their respective avg. HETBs and average them
		for (ImgQualityDataCache.videoDataContainer data : cache.cachedVideoData)
		{
			double intSum = 0.; //for mean and variance
			double int2Sum = 0.;
			//see ImgQualityDataCache.extractFGObjectStats() for explanation of this variable
			double valShift=-1.;

			//shadows of the/short-cuts to the cache data
			final Vector<HashMap<Integer,Double>> avgFG = data.avgFG;
			final Vector<Double> avgBG = data.avgBG;

			//go over all FG objects and calc their HETBs
			long noFGs = 0;
			double l_hetb = 0.0;
			//over all time points
			for (int time=0; time < avgFG.size(); ++time)
			{
				//skip this frame if it is empty
				if (avgFG.get(time).size() == 0) continue;

				//get average signal height from all objects in the given frame
				//NB: the denominator of the HETb_i,t expression
				double frameAvgFGSignal = 0.0;
				for (Double fg : avgFG.get(time).values())
					frameAvgFGSignal += Math.abs(fg - avgBG.get(time));
				frameAvgFGSignal /= (double)avgFG.get(time).size();

				//over all objects, in fact use their avg intensities
				for (Double fg : avgFG.get(time).values())
				{
					//object signal height "normalized" with respect to the
					//usual signal height in this frame, we have to calculate
					//std.dev. from these values
					l_hetb = (fg - avgBG.get(time)) / frameAvgFGSignal;

					if (valShift == -1) valShift = l_hetb;

					intSum  += (l_hetb-valShift);
					int2Sum += (l_hetb-valShift) * (l_hetb-valShift);
					++noFGs;
				}
			}

			//finish the calculation of the average
			if (noFGs > 0)
			{
				//finish calculation of the variance...
				int2Sum -= (intSum*intSum/(double)noFGs);
				int2Sum /= (double)noFGs;

				//...to get the final standard deviation
				l_hetb = Math.sqrt(int2Sum);

				log.info("HETB for video "+data.video+": "+l_hetb);

				hetb += l_hetb;
				++videoCnt;
			}
			else
				log.info("HETB for video "+data.video+": Couldn't calculate average HETB because there are missing labels.");
		}

		//summarize over all datasets:
		if (videoCnt > 0)
		{
			hetb /= (double)videoCnt;
			log.info("HETB for dataset: "+hetb);
		}
		else
			log.info("HETB for dataset: Couldn't calculate average HETB because there are missing labels.");

		return (hetb);
	}
}
