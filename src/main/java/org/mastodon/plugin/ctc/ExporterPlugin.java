package org.mastodon.plugin.ctc;

import java.io.File;

import org.scijava.command.Command;
import org.scijava.command.ContextCommand;
import org.scijava.log.LogLevel;
import org.scijava.log.LogService;
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

	@Parameter
	boolean doOneZslicePerMarker = false;

	@Parameter
	boolean doOutputOnlyTXTfile = false;

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
		//info or error report
		logServiceRef = this.getContext().getService(LogService.class).log();

		//debug report
		logServiceRef.info("Time points span is: "+String.valueOf(timeFrom)+"-"+String.valueOf(timeTill));
		logServiceRef.info("Output folder is   : "+outputPath);

		//aux stuff to create and name the output files
		final PlanarImgFactory<T> outImgFactory = new PlanarImgFactory<T>(outImgVoxelType);
		final String outImgFilenameFormat = String.format("%s%s%s%%0%dd%s",
			outputPath,File.separator,filePrefix,fileNoDigits,filePostfix);

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
		logServiceRef.info("Output image size  : "+Util.printCoordinates(spotMin));

		//some more shortcuts to template voxel params
		//transformation used
		final AffineTransform3D coordTransImg2World = new AffineTransform3D();
		imgSource.getSourceTransform(viewNo,viewMipLevel, coordTransImg2World);
		final AffineTransform3D coordTransWorld2Img = coordTransImg2World.inverse();

		//aux conversion data
		final TrackRecords tracks = new TrackRecords();

		//map: Mastodon's spotID to CTC's trackID
		RefIntMap< Spot > knownTracks = RefMaps.createRefIntMap( model.getGraph().vertices(), -1 );

		//aux Mastodon data: shortcuts and caches/proxies
		final SpatioTemporalIndex< Spot > spots = model.getSpatioTemporalIndex();
		final Link lRef = model.getGraph().edgeRef();              //link reference
		final Spot sRef = model.getGraph().vertices().createRef(); //spot reference
		final Spot fRef = model.getGraph().vertices().createRef(); //some spot's future buddy

		//over all time points
		for ( int time = timeFrom; time <= timeTill; ++time )
		{
			final String outImgFilename = String.format(outImgFilenameFormat, time);
			if (doOutputOnlyTXTfile)
				logServiceRef.info("Processing time point: "+time);
			else
				logServiceRef.info("Populating image: "+outImgFilename);

			final Img<T> outImg
				= doOutputOnlyTXTfile? null : outImgFactory.create(outImgTemplate);

			//over all spots in the current time point
			for ( final Spot spot : spots.getSpatialIndex( time ) )
			{
				//find how many back- and forward-references (time-wise) this spot has
				int countBackwardLinks = 0;
				int countForwardLinks = 0;

				for (int n=0; n < spot.incomingEdges().size(); ++n)
				{
					spot.incomingEdges().get(n, lRef).getSource( sRef );
					if (sRef.getTimepoint() < time && sRef.getTimepoint() >= timeFrom) ++countBackwardLinks;
					if (sRef.getTimepoint() > time && sRef.getTimepoint() <= timeTill)
					{
						++countForwardLinks;
						fRef.refTo( sRef );
					}
				}
				for (int n=0; n < spot.outgoingEdges().size(); ++n)
				{
					spot.outgoingEdges().get(n, lRef).getTarget( sRef );
					if (sRef.getTimepoint() < time && sRef.getTimepoint() >= timeFrom) ++countBackwardLinks;
					if (sRef.getTimepoint() > time && sRef.getTimepoint() <= timeTill)
					{
						++countForwardLinks;
						fRef.refTo( sRef );
					}
				}

				//process events:
				//
				//feasibility test: too many joining paths? (aka merging event)
				if (countBackwardLinks > 1)
				{
					logServiceRef.log(LogLevel.ERROR,
					                  "spot "+spot.getLabel()
					                  +" has multiple ("+countBackwardLinks
					                  +") older-time-point links!");

					//ideally should stop here, but we opted to finish all tracks
					//that join this one, and start the new (parentID = 0) track here

					//list backward links and just forget them (aka delete them from knownTracks)
					for (int n=0; n < spot.incomingEdges().size(); ++n)
					{
						spot.incomingEdges().get(n, lRef).getSource( sRef );
						if (sRef.getTimepoint() < time && sRef.getTimepoint() >= timeFrom)
							knownTracks.remove( sRef );
					}
					for (int n=0; n < spot.outgoingEdges().size(); ++n)
					{
						spot.outgoingEdges().get(n, lRef).getTarget( sRef );
						if (sRef.getTimepoint() < time && sRef.getTimepoint() >= timeFrom)
							knownTracks.remove( sRef );
					}

					//a new track from this spot must be existing (as some from the backward
					//links must have created it), just assure it has no parental information
					//... by removing it completely
					//(or we would need to be allowed to modify internal structures of TrackRecords)
					tracks.removeTrack( knownTracks.get(spot) );

					//pretend there are no backward links (to make it start a new zero-parent track)
					countBackwardLinks = 0;
				}

				//spot with no backward links?
				if (countBackwardLinks == 0)
				{
					//start a new track
					knownTracks.put( spot, tracks.startNewTrack(time) );
				}
				else //countBackwardLinks == 1
				{
					//prolong the existing track
					tracks.updateTrack( knownTracks.get(spot), time );
				}

				//multiple "followers"? feels like a division...
				if (countForwardLinks > 1)
				{
					//list forward links and create them at their respective times,
					//mark spot as their parent
					for (int n=0; n < spot.incomingEdges().size(); ++n)
					{
						spot.incomingEdges().get(n, lRef).getSource( sRef );
						if (sRef.getTimepoint() > time && sRef.getTimepoint() <= timeTill)
						if (knownTracks.get(sRef) == -1)
							knownTracks.put(sRef, tracks.startNewTrack( sRef.getTimepoint(), knownTracks.get(spot) ) );
					}
					for (int n=0; n < spot.outgoingEdges().size(); ++n)
					{
						spot.outgoingEdges().get(n, lRef).getTarget( sRef );
						if (sRef.getTimepoint() > time && sRef.getTimepoint() <= timeTill)
						if (knownTracks.get(sRef) == -1)
							knownTracks.put(sRef, tracks.startNewTrack( sRef.getTimepoint(), knownTracks.get(spot) ) );
					}
				}
				else if (countForwardLinks == 1)
				{
					//just one follower, is he right in the next frame?
					if (fRef.getTimepoint() == time+1)
					{
						//yes, just replace myself in the map
						if (knownTracks.get(fRef) == -1)
							knownTracks.put( fRef, knownTracks.get(spot) );
					}
					else
					{
						//no, start a new track for the follower
						if (knownTracks.get(fRef) == -1)
							knownTracks.put( fRef, tracks.startNewTrack( fRef.getTimepoint(), knownTracks.get(spot) ) );
					}
				}

				//finally, render the spot into the current image with its CTC's trackID
				if (!doOutputOnlyTXTfile)
					renderSpot( outImg, coordTransWorld2Img, spot, knownTracks.get(spot) );

				//forget the currently closed track
				knownTracks.remove( spot );

				//debug: report currently knownTracks
				/*
				for (final Spot s : knownTracks.keySet())
					System.out.println(s.getLabel()+" -> "+knownTracks.get(s));
				*/
			}

			//save the image
			if (!doOutputOnlyTXTfile)
			{
				//net.imglib2.img.display.imagej.ImageJFunctions.showUnsignedShort(outImg, outImgFilename);
				ImgSaver imgSaver = new ImgSaver(this.context());
				imgSaver.saveImg(outImgFilename, outImg);
			}
		}

		//finish the export by creating the supplementary .txt file
		tracks.exportToFile( String.format("%s%s%s.txt", outputPath,File.separator,filePrefix) );

		//release the aux "binder" objects
		model.getGraph().vertices().releaseRef(fRef);
		model.getGraph().vertices().releaseRef(sRef);
		model.getGraph().releaseRef(lRef);

		logServiceRef.info("Done.");
	}


	//some shortcut variables worth remembering
	private int outImgDims = -1;
	private long[] spotMin,spotMax; //image coordinates (in voxel units)
	private double[] radii;         //BBox corners relative to spot's center
	private RealPoint coord;        //aux tmp coordinate
	private LogService logServiceRef;

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
		//if, however, only one zSlice is requested, squash the BBox to a plane in 2nd (z) axis
		if (doOneZslicePerMarker && outImgDims > 2)
		{
			radii[2           ] = spot.getDoublePosition(2);
			radii[2+outImgDims] = spot.getDoublePosition(2);
		}

		//create spot's bounding box in the image coordinates
		final FinalRealInterval spotBBox    = FinalRealInterval.createMinMax(radii);
		final FinalRealInterval spotImgBBox = transform.estimateBounds(spotBBox);

		logServiceRef.info("rendering spot "+spot.getLabel()
		  +" with label "+label+", at "+Util.printCoordinates(spot)
		  +" with radius="+radius);

		//System.out.println("world sweeping box: "+printRealInterval(spotBBox));
		//System.out.println("image sweeping box: "+printRealInterval(spotImgBBox));

		//now, spotImgBBox has to be in pixel (integer) units and intersect with img,
		//also check if there is some intersection with the image at all
		for (int d=0; d < outImgDims; ++d)
		{
			spotMin[d] = Math.max( (long)Math.floor(spotImgBBox.realMin(d)), img.min(d) );
			spotMax[d] = Math.min( (long)Math.ceil( spotImgBBox.realMax(d)), img.max(d) );

			if (spotMin[d] > spotMax[d])
			{
				//no intersection along this axis
				//System.out.println("px sweeping box: no intersection");
				return ;
			}
		}
		//if, however, only one zSlice is requested, make sure the spotImgBBox is indeed single plane thick
		if (doOneZslicePerMarker && outImgDims > 2) spotMax[2] = spotMin[2];

		//System.out.println("px sweeping box: "+Util.printCoordinates(spotMin)+" <-> "+Util.printCoordinates(spotMax));

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
