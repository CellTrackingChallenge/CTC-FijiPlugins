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

/*
 * ====================================================================================
 *       THIS CLASS IS NOT PROPERLY IMPLEMENTED! DO NOT USE! (and sorry for that)
 * ====================================================================================
 */

public class SHA extends AbstractDSmeasure
{
	///a constructor requiring connection to Fiji report/log services
	public SHA(final LogService _log)
	{ super(_log); }


	//---------------------------------------------------------------------/
	/// This is the main SHA calculator.
	@Override
	protected double calculateBottomStage()
	{
		//do the bottom stage
		//DEBUG//log.info("Computing the SHA bottom part...");
		double sha = 0.0;
		long videoCnt = 0; //how many videos were processed

		//go over all encountered videos and calc
		//their respective avg. SHAs and average them
		for (ImgQualityDataCache.videoDataContainer data : cache.cachedVideoData)
		{
			//shadows of the/short-cuts to the cache data
			final Vector<HashMap<Integer,Long>> volumeFG = data.volumeFG;

			//go over all FG objects and calc their RESs
			long noFGs = 0;
			double l_sha = 0.0;
			//over all time points
			for (int time=0; time < volumeFG.size(); ++time)
			{
				//over all objects
				for (Long vol : volumeFG.get(time).values())
				{
					l_sha += (double)vol;
					++noFGs;
				}
			}

			//finish the calculation of the average
			if (noFGs > 0)
			{
				l_sha /= (double)noFGs;
				log.info("RES for video "+data.video+": "+l_sha);

				sha += l_sha;
				++videoCnt;
			}
			else
				log.info("RES for video "+data.video+": Couldn't calculate average RES because there are no cells labelled.");
		}



		//NOTES:
		//use imagej-ops to convert RAI to DefaultMesh
		//via: src/main/java/net/imagej/ops/geom/geom3d/DefaultMarchingCubes.java
		// (need to figure out how binarization is achieved -- see .isolevel and interpolatorClass)
		//
		//That seems to be an implementation of http://paulbourke.net/geometry/polygonise/
		//(which is likely a defacto standard approach, 1st hit on Google at least)
		//
		//once image is meshified, go through all facets of the mesh and stretch vertices
		//according to the current resolution, call mesh.getSurfaceArea() afterwards
		//
		//(alternatively, make a copy of the MC class, and change it by
		// incorporating the resolution directly, see L178-L186)
		//
		//some MC is in 3D_Viewer by Ulrik, some MC is done Kyle...
		//see: https://gitter.im/fiji/fiji/archives/2016/01/22


		//summarize over all datasets:
		if (videoCnt > 0)
		{
			sha /= (double)videoCnt;
			log.info("SHA for dataset: "+sha);
		}
		else
			log.info("SHA for dataset: Couldn't calculate average SHA because there are missing labels.");

		return (sha);
	}
}
