package de.mpicbg.ulman.ctc.Mastodon;

import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.RandomAccess;
import net.imglib2.type.numeric.RealType;

import org.scijava.log.LogService;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.Parameter;

import net.imglib2.realtransform.AffineTransform3D;

import org.mastodon.revised.mamut.MamutAppModel;
import org.mastodon.revised.model.mamut.Spot;

import de.mpicbg.ulman.ctc.Mastodon.util.ImgProviders;

@Plugin( type = Command.class, name = "CTC TRA marker positions auto adjuster @ Mastodon" )
public class TRAadjustPlugin
extends DynamicCommand
{
	// ----------------- necessary internal references -----------------
	@Parameter
	private LogService logService;

	@Parameter(persist = false)
	private MamutAppModel appModel;

	// ----------------- where to read data in -----------------
	@Parameter(label = "Search box size in microns:", min="0")
	double boxSizeUM = 10.0;

	@Parameter(label = "Repeat until no adjustment (per spot):")
	boolean repeatUntilNoChange = false;

	@Parameter(label = "Maximum allowed no. of iterations:", min="0")
	int safetyMaxIters = 10;

	@Parameter(label = "With every search iteration, multiply the search box size by:", stepSize = "0.1")
	double repeatBoxSizeFact = 1.0;

	@Parameter(label = "Report statistics about the adjustments made:",
	           description = "This is useful mainly only when many spots are adjusted in one go.")
	boolean reportStats = false;

	@Override
	public void run()
	{
		if (appModel.getSelectionModel().getSelectedVertices().size() == 0) return;

		//TODO: provide the view choosing dialog
		final ImgProviders.ImgProvider imgSource
			= new ImgProviders.ImgProviderFromMastodon(appModel.getSharedBdvData().getSources().get(0).getSpimSource(),0);
			//NB: we rely on the fact the time point 0 has metadata representative of the rest of the time lapse sequence

		//some more dimensionality-based attributes
		final int inImgDims = imgSource.numDimensions();
		final int[]    posPx = new int[inImgDims];
		final double[] posUm = new double[inImgDims];

		final long[] imgBounds = new long[inImgDims];
		final int[] radiusPx = new int[inImgDims];
		getPxHalfBoxSize(radiusPx,boxSizeUM,imgSource.getVoxelDimensions());

		/*
		logService.info("Considering resolution: "+imgSource.getVoxelDimensions().dimension(0)
		               +" x "+imgSource.getVoxelDimensions().dimension(1)
		               +" x "+imgSource.getVoxelDimensions().dimension(2)
		               +" "+imgSource.getVoxelDimensions().unit()+"/px");
		logService.info("Considering px radius: "+radiusPx[0]
		               +" x "+radiusPx[1]
		               +" x "+radiusPx[2]);
		*/

		//transformation
		final AffineTransform3D coordTransImg2World = new AffineTransform3D();

		//stats:
		final int[] cntsPerDist = new int[20];
		final int[] cntsPerIter = new int[safetyMaxIters+1];

		//"progress bar"
		final long pbSize = appModel.getSelectionModel().getSelectedVertices().size();
		final long pbReportChunk = Math.max( pbSize / 10, 1 );
		long pbDone = 0;

		//scan over all selected spots
		for (Spot spot : appModel.getSelectionModel().getSelectedVertices())
		{
			//get current image data
			final RandomAccess<? extends RealType<?>> ra = (RandomAccess)imgSource.getImage(spot.getTimepoint()).randomAccess();
			imgSource.getImage(spot.getTimepoint()).dimensions( imgBounds );

			//get current image->world coords transformation
			imgSource.getSourceTransform(spot.getTimepoint(), coordTransImg2World);

			//get current image coordinate
			spot.localize(posUm);                           //real world coord
			coordTransImg2World.applyInverse(posUm,posUm);  //real img coord
			for (int d = 0; d < inImgDims; ++d)
				posPx[d] = (int)Math.round( posUm[d] );      //int img coord

			//start scanning around the posPx
			ra.setPosition(posPx);

			//find the local max intensity
			double lastBestInt = -1;
			double tmpInt;
			//NB: posUm will hold the px pos realizing the current local max intensity

			boolean sawDrift = false;
			double currBoxSize = boxSizeUM;
			int iters = 0;
			do {
				if (sawDrift)
				{
					//if we got here, this is not the first round then
					currBoxSize *= repeatBoxSizeFact;
					getPxHalfBoxSize(radiusPx,currBoxSize,imgSource.getVoxelDimensions());

					for (int d = 0; d < inImgDims; ++d)
						ra.setPosition( (int)Math.round( posUm[d] ), d );
				}

				sawDrift = false;
				ra.move(-radiusPx[2],2);
				for (int dzPx = -radiusPx[2]; dzPx <= +radiusPx[2]; ++dzPx)
				{
				ra.move(-radiusPx[1],1);
				for (int dyPx = -radiusPx[1]; dyPx <= +radiusPx[1]; ++dyPx)
				{
				ra.move(-radiusPx[0],0);
				for (int dxPx = -radiusPx[0]; dxPx <= +radiusPx[0]; ++dxPx)
				{
					boolean isOutsideImg = false;
					if (posUm[0]+dxPx < 0 || posUm[0]+dxPx >= imgBounds[0]) isOutsideImg = true;
					if (posUm[1]+dyPx < 0 || posUm[1]+dyPx >= imgBounds[1]) isOutsideImg = true;
					if (posUm[2]+dzPx < 0 || posUm[2]+dzPx >= imgBounds[2]) isOutsideImg = true;

					tmpInt = !isOutsideImg ? ra.get().getRealDouble() : -1;
					if (tmpInt > lastBestInt)
					{
						lastBestInt = tmpInt;
						ra.localize(posUm);
						sawDrift = dxPx != 0 || dyPx != 0 || dzPx != 0 ? true : false;
					}
					ra.move(1,0);
				}
				ra.move(-radiusPx[0]-1,0);
				ra.move(+1            ,1);
				}
				ra.move(-radiusPx[1]-1,1);
				ra.move(+1            ,2);
				}

				++iters;
			} while (repeatUntilNoChange && sawDrift && iters < safetyMaxIters);

			//if repeatUntilNoChange is true, iters will show one round more (the one that had to confirm no change -> sawDrift == false)
			if (repeatUntilNoChange && !sawDrift) --iters;

			//first update the spot's label (while the coords are still in px units)
			double dist=0;
			for (int d = 0; d < inImgDims; ++d)
			{
				posPx[d] = (int)posUm[d] - posPx[d];
				dist += posPx[d]*posPx[d];
			}
			dist = Math.min( Math.sqrt(dist), cntsPerDist.length-1 );

			++cntsPerDist[(int)dist];
			++cntsPerIter[iters];

			spot.setLabel( spot.getLabel()+"+("+posPx[0]+","+posPx[1]+","+posPx[2]+")@"+iters );
			//NB: where did inImgDims go?

			//convert the best obtained px image coord into real world coord
			coordTransImg2World.apply(posUm,posUm);
			spot.setPosition(posUm);

			++pbDone;
			if ((pbDone % pbReportChunk) == 0)
				logService.info((100*pbDone/pbSize)+" % adjusted");
		}

		if (reportStats)
		{
			logService.info("Histogram with bins of 1px distance:");
			for (int i=0; i < cntsPerDist.length; ++i)
				logService.info(i+" px:\t"+cntsPerDist[i]);

			logService.info("Histogram with bins of 1 iteration:");
			for (int i=0; i < cntsPerIter.length; ++i)
				logService.info(i+" iters:\t"+cntsPerIter[i]);
		}
	}

	//determine effective pixel ranges - half box size in px along every image axis
	void getPxHalfBoxSize(final int[] radiusPx, final double boxSize, final VoxelDimensions pixelSize)
	{
		for (int d = 0; d < radiusPx.length; ++d)
			radiusPx[d] = (int)Math.ceil( 0.5 * boxSize / pixelSize.dimension(d) );
			//NB: we actually ignore image units here....
	}
}
