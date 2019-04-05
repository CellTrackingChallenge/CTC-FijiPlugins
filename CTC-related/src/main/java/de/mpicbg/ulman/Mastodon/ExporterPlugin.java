package de.mpicbg.ulman.Mastodon;

import java.awt.*;
import javax.swing.JFrame;
import javax.swing.BoxLayout;
import org.jhotdraw.samples.svg.gui.ProgressIndicator;

import java.io.File;

import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.log.LogLevel;
import org.scijava.log.LogService;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.Parameter;
import org.scijava.widget.FileWidget;

import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.FinalRealInterval;
import net.imglib2.RealInterval;
import net.imglib2.RealPoint;

import bdv.viewer.Source;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.planar.PlanarImgFactory;
import net.imglib2.img.Img;
import net.imglib2.Cursor;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Util;
import net.imglib2.view.Views;

import org.mastodon.revised.mamut.MamutAppModel;
import org.mastodon.revised.model.mamut.Spot;
import org.mastodon.revised.model.mamut.Link;
import org.mastodon.revised.model.mamut.Model;
import org.mastodon.revised.model.mamut.ModelGraph;
import org.mastodon.spatial.SpatioTemporalIndex;
import org.mastodon.collection.RefIntMap;
import org.mastodon.collection.RefMaps;

import de.mpicbg.ulman.workers.TrackRecords;

@Plugin( type = Command.class, name = "CTC format exporter @ Mastodon" )
public class ExporterPlugin <T extends NativeType<T> & RealType<T>>
extends DynamicCommand
{
	// ----------------- necessary internal references -----------------
	@Parameter
	private LogService logService;

	@Parameter(persist = false)
	private MamutAppModel appModel;

	// ----------------- where to store products -----------------
	@Parameter(label = "Choose GT folder with TRA folder inside:", style = FileWidget.DIRECTORY_STYLE)
	File outputFolder = new File("");

	@Parameter(label = "Template for file names:",
	           description = "Use %d or %04d in the template to denote where numbers or 4-digits-zero-padded numbers will appear.")
	String filenameTemplate = "man_track%03d.tif";

	// ----------------- how to store products -----------------
	@Parameter
	Source<?> imgSource;

	//use always the highest resolution possible
	private final int viewMipLevel = 0;

	//constructor-created voxel type
	private final T outImgVoxelType;

	@Parameter(label = "Splash markers into one slice along z-axis:")
	boolean doOneZslicePerMarker = false;

	@Parameter(label = "Export only .txt file and produce no images:")
	boolean doOutputOnlyTXTfile = false;

	@Parameter(label = "Set parent to old track in a new track after a gap:",
	           description = "A gap creates a new track. Enable this to have a parent link between old and new tracks.")
	boolean setParentAfterGap = false;

	@Parameter(label = "Renumber output to start with time point zero:",
	           description = "The first exported time point will be saved as time point 0.")
	boolean resetTimePointNumbers = true;

	@Parameter(label = "How many images to write in parallel:",
	           description = "Increase if during the saving the hardware is not saturated.")
	int writerThreads = 1;

	// ----------------- what to store in the products -----------------
	@Parameter(label = "Export from this time point:", min="0")
	int timeFrom;

	@Parameter(label = "Export till this time point:", min="0")
	int timeTill;

	public ExporterPlugin(final T outImgVoxelType)
	{
		this.outImgVoxelType = outImgVoxelType.createVariable();
	}


	@Override
	public void run()
	{
		//define some shortcut variables
		final Model model = appModel.getModel();
		final ModelGraph modelGraph = model.getGraph();

		//debug report
		logService.info("Time points span is: "+timeFrom+"-"+timeTill);
		logService.info("Output folder is   : "+outputFolder.getAbsolutePath());

		//aux stuff to create and name the output files
		final PlanarImgFactory<T> outImgFactory = new PlanarImgFactory<T>(outImgVoxelType);
		final String outImgFilenameFormat = outputFolder.getAbsolutePath()
		                                  + File.separator
		                                  + filenameTemplate;

		//some more shortcuts to template image params
		final RandomAccessibleInterval<?> outImgTemplate = imgSource.getSource(timeFrom,viewMipLevel);
		if (outImgDims != outImgTemplate.numDimensions())
		{
			//reset dimensionality-based attributes to become compatible again
			outImgDims = outImgTemplate.numDimensions();
			spotMin = new long[outImgDims];
			spotMax = new long[outImgDims];
			radii = new double[2*outImgDims];
			coord = new RealPoint(outImgDims);
		}

		final ParallelImgSaver saver = new ParallelImgSaver(writerThreads);
		final int outputTimeCorrection = resetTimePointNumbers? timeFrom : 0;

		//debug report
		outImgTemplate.dimensions(spotMin);
		logService.info("Output image size  : "+Util.printCoordinates(spotMin));

		//PROGRESS BAR stuff
		final ButtonHandler pbtnHandler = new ButtonHandler();

		final ProgressIndicator pbar = new ProgressIndicator("Time points processed: ", "", 0, timeTill-timeFrom+1, false);
		final Button pbtn = new Button("Stop exporting");
		pbtn.setMaximumSize(new Dimension(150, 40));
		pbtn.addActionListener(pbtnHandler);

		//populate the bar and show it
		final JFrame pbframe = new JFrame("CTC Exporter Progress Bar @ Mastodon");
		pbframe.setLayout(new BoxLayout(pbframe.getContentPane(), BoxLayout.Y_AXIS));
		pbframe.add(pbar);
		pbframe.add(pbtn);
		pbframe.setMinimumSize(new Dimension(300, 100));
		pbframe.setLocationByPlatform(true);
		pbframe.setVisible(true);
		//PROGRESS BAR stuff

		//some more shortcuts to template voxel params
		//transformation used
		final AffineTransform3D coordTransImg2World = new AffineTransform3D();
		imgSource.getSourceTransform(timeFrom,viewMipLevel, coordTransImg2World);
		final AffineTransform3D coordTransWorld2Img = coordTransImg2World.inverse();

		//aux conversion data
		final TrackRecords tracks = new TrackRecords();

		//map: Mastodon's spotID to CTC's trackID
		RefIntMap< Spot > knownTracks = RefMaps.createRefIntMap( modelGraph.vertices(), -1, 500 );

		//aux Mastodon data: shortcuts and caches/proxies
		final SpatioTemporalIndex< Spot > spots = model.getSpatioTemporalIndex();
		final Link lRef = modelGraph.edgeRef();              //link reference
		final Spot sRef = modelGraph.vertices().createRef(); //spot reference
		final Spot fRef = modelGraph.vertices().createRef(); //some spot's future buddy

		try
		{

		//over all time points
		for (int time = timeFrom; time <= timeTill && isCanceled() == false && !pbtnHandler.buttonPressed(); ++time)
		{
			final String outImgFilename = String.format(outImgFilenameFormat, time-outputTimeCorrection);
			if (doOutputOnlyTXTfile)
				logService.info("Processing time point: "+time);
			else
				logService.info("Populating image: "+outImgFilename);

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
					logService.error("spot "+spot.getLabel()
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

					//a new track from this spot must be existing because some from the backward
					//links must have created it, and creating it means either it is a single-follower
					//in which case we must remove this track (just abandon it), or it is a
					//one-from-many-follower (division) in which case the track has just been started
					//(which is OK) and has parent info set (which is not desired now); in the latter
					//case and since we cannot modify existing track, we just delete it
					//
					//and by re-setting backward links, new track will start just in the code below
					countBackwardLinks = 0;

					if (tracks.getStartTimeOfTrack( knownTracks.get(spot) ) == time)
					{
						//the track 'ID' would have been just starting here,
						//re-starting really means to remove it first
						tracks.removeTrack( knownTracks.get(spot) );
						logService.trace(spot.getLabel()+": will supersede track ID "+knownTracks.get(spot));
					}
					else
					{
						logService.trace(spot.getLabel()+": will just leave the track ID "+knownTracks.get(spot));
					}
				}

				//spot with no backward links?
				if (countBackwardLinks == 0)
				{
					//start a new track
					knownTracks.put( spot, tracks.startNewTrack(time) );
					logService.trace(spot.getLabel()+": started track ID "+knownTracks.get(spot)+" at time "+spot.getTimepoint());
				}
				else //countBackwardLinks == 1
				{
					//prolong the existing track
					tracks.updateTrack( knownTracks.get(spot), time );
					logService.trace(spot.getLabel()+": updated track ID "+knownTracks.get(spot)+" at time "+spot.getTimepoint());
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
						{
							knownTracks.put(sRef, tracks.startNewTrack( sRef.getTimepoint(), knownTracks.get(spot) ) );
							logService.trace(sRef.getLabel()+": started track ID "+knownTracks.get(sRef)+" at time "+sRef.getTimepoint());
						}
					}
					for (int n=0; n < spot.outgoingEdges().size(); ++n)
					{
						spot.outgoingEdges().get(n, lRef).getTarget( sRef );
						if (sRef.getTimepoint() > time && sRef.getTimepoint() <= timeTill)
						if (knownTracks.get(sRef) == -1)
						{
							knownTracks.put(sRef, tracks.startNewTrack( sRef.getTimepoint(), knownTracks.get(spot) ) );
							logService.trace(sRef.getLabel()+": started track ID "+knownTracks.get(sRef)+" at time "+sRef.getTimepoint());
						}
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
						{
							knownTracks.put( fRef, tracks.startNewTrack( fRef.getTimepoint(), (setParentAfterGap ? knownTracks.get(spot) : 0) ) );
							logService.trace(fRef.getLabel()+": started track ID "+knownTracks.get(fRef)+" at time "+fRef.getTimepoint());
						}
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
				//add, or wait until the list of images to be saved is small
				try { saver.addImgSaveRequestOrBlockUntilLessThan(2, outImg,outImgFilename); }
				catch (InterruptedException e) {
					this.cancel("cancel requested");
				}
			}

			pbar.setProgress(time+1-timeFrom);
		}

		if (!doOutputOnlyTXTfile)
		{
			logService.info("Finishing, but saving first already prepared images...");
			saver.closeAllWorkers_FinishFirstAllUnsavedImages();
		}

		//finish the export by creating the supplementary .txt file
		tracks.exportToFile(
		    String.format("%s%sman_track.txt", outputFolder.getAbsolutePath(),File.separator),
		    -outputTimeCorrection );

		}
		catch (InterruptedException e) {
			e.printStackTrace();
		}
		finally
		{
			pbtn.removeActionListener(pbtnHandler);
			pbframe.dispose();

			//release the aux "binder" objects
			modelGraph.vertices().releaseRef(fRef);
			modelGraph.vertices().releaseRef(sRef);
			modelGraph.releaseRef(lRef);
		}

		logService.info("Done.");
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
		//if, however, only one zSlice is requested, squash the BBox to a plane in 2nd (z) axis
		if (doOneZslicePerMarker && outImgDims > 2)
		{
			radii[2           ] = spot.getDoublePosition(2);
			radii[2+outImgDims] = spot.getDoublePosition(2);
		}

		//create spot's bounding box in the image coordinates
		final FinalRealInterval spotBBox    = FinalRealInterval.createMinMax(radii);
		final FinalRealInterval spotImgBBox = transform.estimateBounds(spotBBox);

		logService.info("rendering spot "+spot.getLabel()
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
				coord.setPosition( p.getDoublePosition(d), d );
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
