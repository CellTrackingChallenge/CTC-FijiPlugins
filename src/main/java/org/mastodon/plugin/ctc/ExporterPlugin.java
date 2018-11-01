package org.mastodon.plugin.ctc;

import java.io.File;

import org.scijava.command.Command;
import org.scijava.command.ContextCommand;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.Parameter;

import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.FinalRealInterval;
import net.imglib2.RealInterval;
import net.imglib2.RealPoint;

import bdv.viewer.Source;
import io.scif.img.ImgSaver;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.planar.PlanarImgFactory;
import net.imglib2.img.Img;
import net.imglib2.Cursor;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Util;
import net.imglib2.view.Views;

import org.mastodon.revised.model.mamut.Spot;
import org.mastodon.revised.model.mamut.Link;
import org.mastodon.revised.model.mamut.Model;
import org.mastodon.spatial.SpatioTemporalIndex;
import org.mastodon.collection.RefIntMap;
import org.mastodon.collection.RefMaps;

@Plugin( type = Command.class )
public class ExporterPlugin <T extends NativeType<T> & RealType<T>>
extends ContextCommand
{
	// ----------------- where to store products -----------------
	@Parameter
	String outputPath;

	@Parameter
	String filePrefix = "man_track";

	@Parameter
	String filePostfix = ".tif";

	@Parameter
	int fileNoDigits = 3;

	// ----------------- how to store products -----------------
	@Parameter
	Source<?> imgSource;

	private final int viewNo = 0;
	private final int viewMipLevel = 0;
	private final T outImgVoxelType;

	// ----------------- what to store in the products -----------------
	@Parameter
	Model model;

	@Parameter
	int timeFrom;

	@Parameter
	int timeTill;

	public ExporterPlugin(final T outImgVoxelType)
	{
		this.outImgVoxelType = outImgVoxelType.createVariable();
	}

	@Override
	public void run()
	{
		//debug report
		System.out.println("Time points span is: "+String.valueOf(timeFrom)+"-"+String.valueOf(timeTill));
		System.out.println("Output folder is   : "+outputPath);

		//aux stuff to create and name the output files
		final PlanarImgFactory<T> outImgFactory = new PlanarImgFactory<T>(outImgVoxelType);
		final String outImgFilenameFormat = String.format("%s%s%s%%0%dd%s", outputPath,File.separator,filePrefix,fileNoDigits,filePostfix);

		//some more shortcuts to template image params
		final RandomAccessibleInterval<?> outImgTemplate = imgSource.getSource(viewNo,viewMipLevel);
		if (outImgDims != outImgTemplate.numDimensions())
		{
			//reset dimensionality-based attributes to become compatible again
			outImgDims = outImgTemplate.numDimensions();
			spotMin = new long[outImgDims];
			spotMax = new long[outImgDims];
			radii = new double[2*outImgDims];
			coord = new RealPoint(outImgDims);
		}

		//debug report
		outImgTemplate.dimensions(spotMin);
		System.out.println("Output image size  : "+Util.printCoordinates(spotMin));

		//some more shortcuts to template voxel params
		//transformation used
		final AffineTransform3D coordTransImg2World = new AffineTransform3D();
		imgSource.getSourceTransform(viewNo,viewMipLevel, coordTransImg2World);
		final AffineTransform3D coordTransWorld2Img = coordTransImg2World.inverse();

		//aux conversion data
		final TrackRecords tracks = new TrackRecords();
		final lastTwoFramesRecords frames = new lastTwoFramesRecords();

		//aux Mastodon data: shortcuts and caches/proxies
		final SpatioTemporalIndex< Spot > spots = model.getSpatioTemporalIndex();
		final Link lRef = model.getGraph().edgeRef();
		final Spot sRef = model.getGraph().vertices().createRef();

		//over all time points
		for ( int time = timeFrom; time <= timeTill; ++time )
		{
			//init the "list" of spots seen in the current frame
			frames.currentlySeenSpots.clear();

			//init the counter of references on the spots seen in the previous frame
			//NB: only deletes here, the entries will be created (and initialized) on demand
			frames.lSScounter.clear();

			//over all spots in the current time point
			for ( final Spot spot : spots.getSpatialIndex( time ) )
			{
				//CTC trackID is not decided yet
				int trackID = -1;

				//scan all neighbors of this spot to see if some is part of some existing CTC track
				for (int n=0; n < spot.incomingEdges().size() && trackID == -1; ++n)
				{
					spot.incomingEdges().get(n, lRef).getSource( sRef );
					trackID = frames.lastlySeenSpots.get( sRef );
				}
				for (int n=0; n < spot.outgoingEdges().size() && trackID == -1; ++n)
				{
					spot.outgoingEdges().get(n, lRef).getTarget( sRef );
					trackID = frames.lastlySeenSpots.get( sRef );
				}

				if (trackID == -1)
				{
					//no neighbor of the current spot was seen in the previous frame,
					//we are thus starting a new track
					trackID = tracks.getNextAvailTrackID();
					tracks.startNewTrack(trackID, time);
				}
				else
				{
					//the neighbor was seen in the previous frame, (conditionally) "prolong" its track,
					//it holds trackID == frames.lastlySeenSpots.get( sRef );
					frames.lSScounter.adjustOrPutValue(sRef, 1, 1);
					//NB: if neig's counter ends up > 1, we have found a division,
					//    and trackID hints who are the daughters
				}
				frames.currentlySeenSpots.put(spot, trackID);
			}

			//now check the lastlySeenSpots.counter, that is how many times a spot in the previous
			//frame was referenced (via link) from the current frame
			for (final Spot mSpot : frames.lSScounter.keySet())
			{
				if (frames.lSScounter.get(mSpot) > 1)
				{
					//mSpot is a mother
					final int mTrackID = frames.lastlySeenSpots.get(mSpot);

					//let's find her daughters
					for (final Spot dID : frames.currentlySeenSpots.keySet())
					if (frames.currentlySeenSpots.get(dID) == mTrackID)
					{
						//daughter has been found, start a new track for her
						final int dTrackID = tracks.getNextAvailTrackID();
						tracks.insertDivision(mTrackID, dTrackID, time);
						frames.currentlySeenSpots.put(dID, dTrackID); //NB: rewrites existing record
					}
				}
				else if (frames.lSScounter.get(mSpot) == 0)
				{
					//mSpot is abandoned
					tracks.finishTrack(frames.lastlySeenSpots.get(mSpot), time);
				}
			}

			//finally, render currently seen spots into the current image with their CTC's trackIDs
			final String outImgFilename = String.format(outImgFilenameFormat, time);
			System.out.println(outImgFilename);
			Img<T> outImg = outImgFactory.create(outImgTemplate);
			for (final Spot spot : frames.currentlySeenSpots.keySet())
			{
				renderSpot(outImg, coordTransWorld2Img, spot, frames.currentlySeenSpots.get(spot));
			}

			//save the image
			//net.imglib2.img.display.imagej.ImageJFunctions.showUnsignedShort(outImg, outImgFilename);
			ImgSaver imgSaver = new ImgSaver(this.context());
			imgSaver.saveImg(outImgFilename, outImg);

			//lastSeen will be what is currentlySeen
			frames.swapSeenMaps();
		}

		//finish the tracks from the last processed frame (NB: lists are already swapped)
		for (final Integer trackID : frames.lastlySeenSpots.values())
			tracks.finishTrack(trackID, timeTill);
		tracks.exportToFile( String.format("%s%s%s.txt", outputPath,File.separator,filePrefix) );

		//release the aux "binder" objects
		model.getGraph().vertices().releaseRef(sRef);
		model.getGraph().releaseRef(lRef);
	}

	//management of the last two frames only
	class lastTwoFramesRecords
	{
		//maps Mastodon's spotID to CTC's trackID
		RefIntMap< Spot > currentlySeenSpots = RefMaps.createRefIntMap( model.getGraph().vertices(), -1 );
		RefIntMap< Spot > lastlySeenSpots    = RefMaps.createRefIntMap( model.getGraph().vertices(), -1 );
		final RefIntMap< Spot > lSScounter   = RefMaps.createRefIntMap( model.getGraph().vertices(), -1 );

		public void swapSeenMaps()
		{
			final RefIntMap< Spot > tmp = currentlySeenSpots;
			currentlySeenSpots = lastlySeenSpots;
			lastlySeenSpots = tmp;
		}
	}

	//some shortcut variables worth remembering
	private int outImgDims = -1;
	private long[] spotMin,spotMax; //image coordinates (in voxel units)
	private double[] radii;         //BBox corners relative to spot's center
	private RealPoint coord;        //aux tmp coordinate

	private
	void renderSpot(final Img<T> img,final AffineTransform3D transform,
	                final Spot spot, final int label)
	{
		//the spot size
		final double radius = Math.sqrt(spot.getBoundingSphereRadiusSquared());

		//create spot's bounding box in the world coordinates
		for (int d=0; d < outImgDims; ++d)
		{
			radii[d           ] = spot.getDoublePosition(d) - radius;
			radii[d+outImgDims] = spot.getDoublePosition(d) + radius;
		}

		//create spot's bounding box in the image coordinates
		final FinalRealInterval spotBBox    = FinalRealInterval.createMinMax(radii);
		final FinalRealInterval spotImgBBox = transform.estimateBounds(spotBBox);

		System.out.println("rendering "+label
		  +" ("+spot.getLabel()+") at "+Util.printCoordinates(spot)
		  +" with radius="+radius);

		System.out.println("world sweeping box: "+printRealInterval(spotBBox));
		System.out.println("image sweeping box: "+printRealInterval(spotImgBBox));

		//now, spotImgBBox has to be in pixel (integer) units and intersect with img,
		//also check if there is some intersection with the image at all
		for (int d=0; d < outImgDims; ++d)
		{
			spotMin[d] = Math.max( (long)Math.floor(spotImgBBox.realMin(d)), img.min(d) );
			spotMax[d] = Math.min( (long)Math.ceil( spotImgBBox.realMax(d)), img.max(d) );

			if (spotMin[d] > spotMax[d])
			{
				//no intersection along this axis
				System.out.println("px sweeping box: no intersection");
				return ;
			}
		}

		System.out.println("px sweeping box: "+Util.printCoordinates(spotMin)+" <-> "+Util.printCoordinates(spotMax));

		//NB: the tests above assure that spotMin and spotMax make sense and live inside the img
		final Cursor<T> p = Views.interval(img, spotMin, spotMax).localizingCursor();
		T voxelAtP;
		while (p.hasNext())
		{
			//get next voxel
			voxelAtP = p.next();

			//get it's (real) image coordinate
			for (int d=0; d < outImgDims; ++d)
				coord.setPosition( p.getDoublePosition(d) + 0.5, d );
			//get it's real world coordinate
			transform.applyInverse(coord, coord);

			//if close to the spot's center, draw into this voxel
			if (Util.distance(coord,spot) <= radius) voxelAtP.setReal(label);
		}
	}

	public
	String printRealInterval(final RealInterval ri)
	{
		return "["+ri.realMin(0)+","+ri.realMin(1)+","+ri.realMin(2)+"] <-> ["+ri.realMax(0)+","+ri.realMax(1)+","+ri.realMax(2)+"]";
	}
}
