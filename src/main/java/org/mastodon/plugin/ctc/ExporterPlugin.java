package org.mastodon.plugin.ctc;

import java.io.File;

import org.scijava.command.Command;
import org.scijava.command.ContextCommand;
import org.scijava.plugin.Plugin;

import bdv.viewer.Source;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Util;

import org.scijava.plugin.Parameter;

import org.mastodon.revised.model.mamut.Spot;
import org.mastodon.revised.model.mamut.Link;
import org.mastodon.revised.model.mamut.Model;
import org.mastodon.spatial.SpatioTemporalIndex;
import org.mastodon.collection.RefIntMap;
import org.mastodon.collection.RefMaps;

@Plugin( type = Command.class )
public class ExporterPlugin extends ContextCommand
{
	@Parameter
	String outputPath;

	@Parameter
	String filePrefix = "man_track";

	@Parameter
	String filePostfix = ".tif";

	@Parameter
	int fileNoDigits = 3;

	@Parameter
	int timeFrom;

	@Parameter
	int timeTill;

	@Parameter
	Model model;

	@Parameter
	Source<?> imgSource;

	@Override
	public void run()
	{
		//debug
		System.out.println("Output folder is   : "+outputPath);
		System.out.println("Time points span is: "+String.valueOf(timeFrom)+"-"+String.valueOf(timeTill));

		//enumerate output files
		final String outFilenameFormat = String.format("%s%s%s%%0%dd%s", outputPath,File.separator,filePrefix,fileNoDigits,filePostfix);

		//params of output files
		//transformation used with the 1st setup
		final AffineTransform3D coordTransImg2World = new AffineTransform3D();
		imgSource.getSourceTransform(0,0, coordTransImg2World);

		//voxel size = 1/resolution
		if (imgSource.getVoxelDimensions().unit().startsWith("um") == false)
			throw new IllegalArgumentException("Incompatible resolution units used in this project: "+imgSource.getVoxelDimensions().unit());
		final double[] voxelSize = new double[3];
		imgSource.getVoxelDimensions().dimensions(voxelSize);

		//RAI corresponding to the output image
		final RandomAccessibleInterval<?> imgTemplate = imgSource.getSource(0,0);

		final long[] voxelCounts = new long[3]; //REMOVE ME
		imgTemplate.dimensions(voxelCounts);
		final double[] tt = new double[16];
		coordTransImg2World.toArray(tt);
		System.out.println("Output image size  : "+Util.printCoordinates(voxelCounts));
		System.out.println("Output voxel size  : "+Util.printCoordinates(voxelSize));
		System.out.println("Coord transform    : "+Util.printCoordinates(tt));

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
			System.out.println(String.format(outFilenameFormat, time));
			final float[] coord = new float[3];
			for (final Spot spot : frames.currentlySeenSpots.keySet())
			{
				spot.localize(coord);
				coordTransImg2World.applyInverse(coord, coord);

				System.out.println( String.valueOf(time)+": rendering "+String.valueOf(frames.currentlySeenSpots.get(spot))
				  +" ("+spot.getLabel()+") at "+Util.printCoordinates(coord)
				  +" with radius^2="+spot.getBoundingSphereRadiusSquared());
			}

			//lastSeen will be what is currentlySeen
			frames.swapSeenMaps();
		}

		//finish the tracks from the last processed frame
		for (final Integer trackID : frames.currentlySeenSpots.values())
			tracks.finishTrack(trackID, timeTill);
		tracks.exportToConsole();

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
}
