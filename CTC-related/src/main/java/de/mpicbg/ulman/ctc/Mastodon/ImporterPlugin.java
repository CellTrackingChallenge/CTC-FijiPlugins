package de.mpicbg.ulman.ctc.Mastodon;

import java.awt.*;
import javax.swing.JFrame;
import javax.swing.BoxLayout;

import org.jhotdraw.samples.svg.gui.ProgressIndicator;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException;

import org.scijava.log.LogService;
import org.scijava.command.Command;
import org.scijava.command.CommandModule;
import org.scijava.command.CommandService;
import org.scijava.command.DynamicCommand;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.Parameter;

import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.IterableInterval;
import net.imglib2.RealInterval;

import bdv.viewer.SourceAndConverter;
import net.imglib2.Cursor;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.LinAlgHelpers;

import org.mastodon.revised.ui.util.FileChooser;
import org.mastodon.revised.ui.util.ExtensionFileFilter;
import org.mastodon.revised.mamut.MamutAppModel;
import org.mastodon.revised.model.AbstractModelImporter;
import org.mastodon.revised.model.mamut.Spot;
import org.mastodon.revised.model.mamut.Link;
import org.mastodon.revised.model.mamut.Model;
import org.mastodon.revised.model.mamut.ModelGraph;
import org.mastodon.collection.IntRefMap;
import org.mastodon.collection.RefMaps;

import de.mpicbg.ulman.ctc.Mastodon.util.ButtonHandler;
import de.mpicbg.ulman.ctc.Mastodon.util.ImgProviders;
import de.mpicbg.ulman.ctc.Mastodon.auxPlugins.FileTemplateProvider;
import de.mpicbg.ulman.ctc.workers.TrackRecords;

@Plugin( type = Command.class, name = "CTC format importer @ Mastodon" )
public class ImporterPlugin
extends DynamicCommand
{
	// ----------------- necessary internal references -----------------
	@Parameter
	private LogService logService;

	@Parameter(persist = false)
	private MamutAppModel appModel;

	// ----------------- where to read data in -----------------
	@Parameter(label = "From where to import CTC tracking:",
	           initializer = "encodeImgSourceChoices", choices = {} )
	public String imgSourceChoice = "";

	@Parameter(label = "Import from this time point:", min="0")
	Integer timeFrom;

	@Parameter(label = "Import till this time point:", min="0")
	Integer timeTill;

	final ArrayList<String> choices = new ArrayList<>(20);
	void encodeImgSourceChoices()
	{
		final ArrayList<SourceAndConverter<?> > mSources = appModel.getSharedBdvData().getSources();
		for (int i = 0; i < mSources.size(); ++i)
			choices.add( "View: "+mSources.get(i).getSpimSource().getName() );
		choices.add( "CTC: result data" );
		choices.add( "CTC: GT data" );
		choices.add( "images in own filename format" );
		getInfo().getMutableInput("imgSourceChoice", String.class).setChoices( choices );

		//provide some default presets
		MutableModuleItem<Integer> tItem = getInfo().getMutableInput("timeFrom", Integer.class);
		tItem.setMinimumValue(appModel.getMinTimepoint());
		tItem.setMaximumValue(appModel.getMaxTimepoint());

		tItem = getInfo().getMutableInput("timeTill", Integer.class);
		tItem.setMinimumValue(appModel.getMinTimepoint());
		tItem.setMaximumValue(appModel.getMaxTimepoint());

		timeFrom = appModel.getMinTimepoint();
		timeTill = appModel.getMaxTimepoint();

		//make sure this will always appear in the menu
		this.unresolveInput("timeFrom");
		this.unresolveInput("timeTill");
	}

	private String inputTxtFile = null;
	ImgProviders.ImgProvider decodeImgSourceChoices()
	{
		if (imgSourceChoice.startsWith("images in own"))
		{
			//ask for folder, filename type and lineage file
			Future<CommandModule> files = this.getContext().getService(CommandService.class).run(FileTemplateProvider.class,true);
			try {
				inputTxtFile = ((File)files.get().getInput("containingFolder")).getAbsolutePath()
					+File.separator
					+((String)files.get().getInput("filenameTXT"));

				return new ImgProviders.ImgProviderFromDisk(
					((File)files.get().getInput("containingFolder")).getAbsolutePath(),
					(String)files.get().getInput("filenameTemplate"),timeFrom);
			} catch (InterruptedException | ExecutionException e) {
				e.printStackTrace();
				return null;
			}
		}
		else if (imgSourceChoice.startsWith("CTC"))
		{
			//some CTC format, ask for an images-containing folder
			File selectedFolder = FileChooser.chooseFile(null, null, null,
				"Choose folder with the images in the CTC format:",
				FileChooser.DialogType.LOAD,
				FileChooser.SelectionMode.DIRECTORIES_ONLY);

			//cancel button ?
			if (selectedFolder == null) return null;

			if (imgSourceChoice.startsWith("CTC: GT"))
			{
				inputTxtFile = selectedFolder.getAbsolutePath()+File.separator+"TRA"+File.separator+"man_track.txt";
				return new ImgProviders.ImgProviderFromDisk(selectedFolder.getAbsolutePath()+File.separator+"TRA","man_track%03d.tif",timeFrom);
			}
			else
			{
				inputTxtFile = selectedFolder.getAbsolutePath()+File.separator+"res_track.txt";
				return new ImgProviders.ImgProviderFromDisk(selectedFolder.getAbsolutePath(),"mask%03d.tif",timeFrom);
			}
		}
		else
		{
			File selectedFile = FileChooser.chooseFile(null, null,
				new ExtensionFileFilter("txt"),
				"Choose folder with the images in the CTC format:",
				FileChooser.DialogType.LOAD,
				FileChooser.SelectionMode.FILES_ONLY);

			//cancel button ?
			if (selectedFile == null) return null;

			inputTxtFile = selectedFile.getAbsolutePath();

			//some project's view, have to find the right one
			for (int i = 0; i < choices.size(); ++i)
			if (imgSourceChoice.startsWith(choices.get(i)))
				return new ImgProviders.ImgProviderFromMastodon(appModel.getSharedBdvData().getSources().get(i).getSpimSource(),timeFrom);

			//else not found... strange...
			return null;
		}
	}

	// ----------------- how to read data in -----------------
	@Parameter(label = "Checks if created spots overlap with their markers significantly:")
	boolean doMatchCheck = true;


	@Override
	public void run()
	{
		final ImgProviders.ImgProvider imgSource = decodeImgSourceChoices();
		if (imgSource == null) return;

		logService.info("Considering resolution: "+imgSource.getVoxelDimensions().dimension(0)
		               +" x "+imgSource.getVoxelDimensions().dimension(1)
		               +" x "+imgSource.getVoxelDimensions().dimension(2)
		               +" "+imgSource.getVoxelDimensions().unit()+"/px");

		//define some shortcut variables
		final Model model = appModel.getModel();
		final ModelGraph modelGraph = model.getGraph();

		//debug report
		logService.info("Time points span is   : "+timeFrom+"-"+timeTill);
		logService.info("Supp. lineage file is : "+inputTxtFile);

		//load metadata with the lineages
		final TrackRecords tracks = new TrackRecords();
		try
		{
			tracks.loadTrackFile(inputTxtFile, logService);
		}
		catch (IOException e)
		{
			e.printStackTrace();
			throw new IllegalArgumentException("Error reading the lineage file "+inputTxtFile);
		}

		//PROGRESS BAR stuff
		final ButtonHandler pbtnHandler = new ButtonHandler();

		final ProgressIndicator pbar = new ProgressIndicator("Time points processed: ", "", 0, timeTill-timeFrom+1, false);
		final Button pbtn = new Button("Stop importing");
		pbtn.setMaximumSize(new Dimension(150, 40));
		pbtn.addActionListener(pbtnHandler);

		//populate the bar and show it
		final JFrame pbframe = new JFrame("CTC Importer Progress Bar @ Mastodon");
		pbframe.setLayout(new BoxLayout(pbframe.getContentPane(), BoxLayout.Y_AXIS));
		pbframe.add(pbar);
		pbframe.add(pbtn);
		pbframe.setMinimumSize(new Dimension(300, 100));
		pbframe.setLocationByPlatform(true);
		pbframe.setVisible(true);
		//PROGRESS BAR stuff

		new AbstractModelImporter< Model >( model ){{ startImport(); }};

		//transformation used
		final AffineTransform3D coordTransImg2World = new AffineTransform3D();

		//some more dimensionality-based attributes
		inImgDims = imgSource.numDimensions();
		position = new int[inImgDims];

		//volume and squared lengths of one voxel along all axes
		resSqLen  = new double[inImgDims];
		imgSource.getVoxelDimensions().dimensions(resSqLen);
		resArea   = resSqLen[0] * resSqLen[1]; //NB: lengths are yet before squaring
		resVolume = 1;
		for (int i=0; i < inImgDims; ++i)
		{
			resVolume *= resSqLen[i];
			resSqLen[i] *= resSqLen[i];
		}

		recentlyUsedSpots = RefMaps.createIntRefMap( modelGraph.vertices(), -1, 500 );
		linkRef = modelGraph.edgeRef();
		nSpot = modelGraph.vertices().createRef();
		oSpot = modelGraph.vertices().createRef();

		try
		{

		//iterate through time points and extract spots
		for (int time = timeFrom; time <= timeTill && isCanceled() == false && !pbtnHandler.buttonPressed(); ++time)
		{
			logService.info("Processing time point : "+time);

			imgSource.getSourceTransform(time, coordTransImg2World);
			readSpots( (IterableInterval)imgSource.getImage(time),
			           time, coordTransImg2World, modelGraph, tracks );

			pbar.setProgress(time+1-timeFrom);
		}

		}
		finally
		{
			pbtn.removeActionListener(pbtnHandler);
			pbframe.dispose();

			modelGraph.vertices().releaseRef(oSpot);
			modelGraph.vertices().releaseRef(nSpot);
			modelGraph.releaseRef(linkRef);
		}

		new AbstractModelImporter< Model >( model ){{ finishImport(); }};
		logService.info("Done.");
	}


	//some shortcut variables worth remembering
	private int inImgDims = -1;
	private int[] position;         //aux px coordinate
	private double[] resSqLen;      //aux 1px square lengths
	private double   resVolume;     //aux 1px volume
	private double   resArea;       //aux 1px xy-plane area
	private IntRefMap< Spot > recentlyUsedSpots;
	private Spot nSpot,oSpot;       //spots references
	private Link linkRef;           //link reference
	final private double[][] cov = new double[3][3];
	final private double[][] T   = new double[3][3];
	final private double[][] Tc  = new double[3][3];

	private <T extends NativeType<T> & RealType<T>>
	void readSpots(final IterableInterval<T> img, final int time,
	               final AffineTransform3D transform,
	               final ModelGraph modelGraph, final TrackRecords tracks)
	{
		//description of a marker:
		class Marker
		{
			Marker(final T l)
			{
				label = l.copy();
				accCoords = new double[inImgDims];
			}

			//label (voxel value) of this marker
			final T label;

			//volume/area of the marker
			long size;

			//overlap of this marker with its corresponding spot
			long markerOverlap;

			//accumulated coordinates
			double[] accCoords;

			//z-coordinate span
			int minZ=inImgDims < 3 ? 0 : Integer.MAX_VALUE;
			int maxZ=inImgDims < 3 ? 0 : Integer.MIN_VALUE;
		}

		//markers discovered in this image
		HashMap<T,Marker> currentMarkers = new HashMap<>(100);

		//sweep the image and define the markers
		final Cursor<T> voxelCursor = img.localizingCursor();
		while (voxelCursor.hasNext())
		if (voxelCursor.next().getRealFloat() > 0)
		{
			//get functional reference on a marker description
			Marker m = currentMarkers.get(voxelCursor.get());
			if (m == null)
			{
				m = new Marker( voxelCursor.get() );
				currentMarkers.put(m.label, m);
			}

			//update it...
			m.size++;

			voxelCursor.localize(position);
			for (int i=0; i < inImgDims; ++i)
				m.accCoords[i] += position[i];

			if (inImgDims > 2)
			{
				m.minZ = position[2] < m.minZ ? position[2] : m.minZ;
				m.maxZ = position[2] > m.maxZ ? position[2] : m.maxZ;
			}
		}

		//process markers and create respective spots in Mastodon
		for (final Marker m : currentMarkers.values())
		{
			final int label = (int)m.label.getRealFloat();

			//finalize the geometrical centre coordinate (img coords, in px)
			for (int i=0; i < inImgDims; ++i)
				m.accCoords[i] /= m.size;

			//convert the coordinate into Mastodon's world coordinate
			transform.apply(m.accCoords,m.accCoords);

			//estimate radius...
			if (m.minZ == m.maxZ)
			{
				//...as if marker is 2D
				final double r = Math.sqrt( resArea * (double)m.size / Math.PI );
				cov[0][0] = r*r / resSqLen[0];
				cov[1][1] = r*r / resSqLen[1];
				cov[2][2] = 0.5;
				//NB: 0.7 * 0.7 = 0.5 -> z thickness is 1.4 px around the marker's centre
			}
			else
			{
				//...as if marker is 3D
				final double r = Math.cbrt( 0.75 * resVolume * (double)m.size / Math.PI );
				cov[0][0] = r*r / resSqLen[0];
				cov[1][1] = r*r / resSqLen[1];
				cov[2][2] = r*r / resSqLen[2];
			}
			//reset non-diagonal elements
			cov[0][1] = 0; cov[0][2] = 0;
			cov[1][0] = 0; cov[1][2] = 0;
			cov[2][0] = 0; cov[2][1] = 0;

			//adapt the canonical/img-based covariance to Mastodon's world coordinate system
			for ( int r = 0; r < 3; ++r )
				for ( int c = 0; c < 3; ++c )
					T[r][c] = transform.get( r, c );
			LinAlgHelpers.mult( T, cov, Tc );
			LinAlgHelpers.multABT( Tc, T, cov );

			//System.out.println("adding spot at "+Util.printCoordinates(m.accCoords)+" with label="+label);
			nSpot = modelGraph.addVertex( nSpot ).init( time, m.accCoords, cov );

			if (recentlyUsedSpots.containsKey(label))
			{
				//was detected also in the previous frame
				//System.out.println("linking spot with its previous occurrence");

				recentlyUsedSpots.get(label, oSpot);
				modelGraph.addEdge( oSpot, nSpot, linkRef ).init();
			}
			else
			{
				//is detected for the first time: is it after a division?
				if (recentlyUsedSpots.containsKey(tracks.getParentOfTrack(label)))
				{
					//System.out.println("linking spot with its mother "+t.m_parent);

					recentlyUsedSpots.get(tracks.getParentOfTrack(label), oSpot);
					modelGraph.addEdge( oSpot, nSpot, linkRef ).init();
				}
			}

			//in any case, add-or-replace the association of nSpot to this label
			recentlyUsedSpots.put(label, nSpot);

			//NB: we're not removing finished tracks TODO??
			//NB: we shall not remove finished tracks until we're sure they are no longer parents to some future tracks
		}

		//check markers vs. created spots how well do they overlap
		if (doMatchCheck)
		{
			final double[] positionV = new double[inImgDims];
			final double[] positionS = new double[inImgDims];

			//sweep the image and define the markers
			voxelCursor.reset();
			while (voxelCursor.hasNext())
			if (voxelCursor.next().getRealFloat() > 0)
			{
				//Mastodon's world coordinate of this voxel (of this voxel's centre)
				voxelCursor.localize(positionV);
				transform.apply(positionV, positionV);

				//found some marker voxel, find its spot,
				//and increase overlap counter if voxel falls into the spot
				recentlyUsedSpots.get((int)voxelCursor.get().getRealFloat(), nSpot);
				nSpot.localize(positionS);
				nSpot.getCovariance(cov);

				double sum=0;
				for (int i=0; i < inImgDims && i < 3; ++i)
				{
					positionV[i] -= positionS[i];
					positionV[i] *= positionV[i];
					positionV[i] /= cov[i][i];
					sum += positionV[i];
				}

				//is the voxel "covered" with the spot?
				if (sum <= 1.0)
					currentMarkers.get(voxelCursor.get()).markerOverlap++;
			}

			//now scan over the markers and check the matching criterion
			for (final Marker m : currentMarkers.values())
			{
				//System.out.println((int)m.label.getRealFloat()+": "+m.markerOverlap+" / "+m.size);
				if (2*m.markerOverlap < m.size)
					logService.error("time "+time
					                  +": spot "+recentlyUsedSpots.get((int)m.label.getRealFloat(),nSpot).getLabel()
					                  +" does not cover image marker "+(int)m.label.getRealFloat());
			}
		}
	}


	public
	String printRealInterval(final RealInterval ri)
	{
		return "["+ri.realMin(0)+","+ri.realMin(1)+","+ri.realMin(2)+"] <-> ["+ri.realMax(0)+","+ri.realMax(1)+","+ri.realMax(2)+"]";
	}
}
