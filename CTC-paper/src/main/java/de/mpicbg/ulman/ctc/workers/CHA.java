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

public class CHA extends AbstractDSmeasure
{
	///a constructor requiring connection to Fiji report/log services
	public CHA(final LogService _log)
	{ super(_log); }


	//---------------------------------------------------------------------/
	/**
	 * The function returns average FG intensity over all objects found that
	 * are present at time points (inclusive) \e from till \e to.
	 * Returns -1 if no object has been found at all.
	 */
	private double avgFGfromTimeSpan(final int from, final int to,
		final Vector<HashMap<Integer,Double>> avgFG)
	{
		if (from < 0 || from >= avgFG.size()) return (-1.0);
		if ( to  < 0 ||  to  >= avgFG.size()) return (-1.0);

		double avg = 0.0;
		int cnt = 0;

		for (int time = from; time <= to; ++time)
		{
			for (Double fg : avgFG.get(time).values())
			{
				avg += fg;
				++cnt;
			}
		}

		return (cnt > 0 ? avg/(double)cnt : -1.0);
	}


	//---------------------------------------------------------------------/
	/// This is the main CHA calculator.
	@Override
	protected double calculateBottomStage()
	{
		//do the bottom stage
		//DEBUG//log.info("Computing the CHA bottom part...");
		double cha = 0.0;
		long videoCnt = 0; //how many videos were processed

		//go over all encountered videos and calc
		//their respective avg. CHAs and average them
		for (ImgQualityDataCache.videoDataContainer data : cache.cachedVideoData)
		{
			//shadows of the/short-cuts to the cache data
			final Vector<HashMap<Integer,Double>> avgFG = data.avgFG;

			double a = -1.0, b = -1.0;
			double l_cha = 0.0;

			if (avgFG.size() < 2)
			{
				throw new IllegalArgumentException("Cannot calculate CHA from less than two images.");
			}
			else
			if (avgFG.size() == 2)
			{
				a = avgFGfromTimeSpan(0,0,avgFG);
				b = avgFGfromTimeSpan(1,1,avgFG);
				l_cha = b - a;
			}
			else
			{
				//use largest possible (possibly overlapping, though) window
				//windows size = 2 time points
				final int last = avgFG.size() - 1;
				a = avgFGfromTimeSpan(0,1,avgFG);
				b = avgFGfromTimeSpan(last-1,last,avgFG);
				l_cha = b - a;
				l_cha /= (double)last;
			}

			if (a < 0.0 || b < 0.0)
				throw new IllegalArgumentException("CHA for video "+data.video
					+": Current implementation cannot deal with images with no FG labels.");

			log.info("CHA_debug: avg. int. "+a+" -> "+b+", over "+avgFG.size()+" frames");
			log.info("CHA for video "+data.video+": "+l_cha);

			cha += l_cha;
			++videoCnt;
		}

		//summarize over all datasets:
		if (videoCnt > 0)
		{
			cha /= (double)videoCnt;
			cha = Math.abs(cha);
			log.info("CHA for dataset: "+cha);
		}
		else
			log.info("CHA for dataset: Couldn't calculate average CHA because there are missing labels.");

		return (cha);
	}
}
