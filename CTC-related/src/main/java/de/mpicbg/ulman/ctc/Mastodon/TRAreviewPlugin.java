package de.mpicbg.ulman.ctc.Mastodon;

import java.awt.*;
import javax.swing.*;

import org.jhotdraw.samples.svg.gui.ProgressIndicator;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;

import org.mastodon.collection.RefCollections;
import org.mastodon.collection.RefList;
import org.mastodon.spatial.SpatioTemporalIndex;
import org.scijava.log.LogService;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.Parameter;

import net.imglib2.realtransform.AffineTransform3D;

import org.mastodon.revised.mamut.MamutAppModel;
import org.mastodon.revised.model.mamut.Spot;
import org.mastodon.revised.model.mamut.Link;
import org.mastodon.revised.model.mamut.Model;
import org.mastodon.revised.model.mamut.ModelGraph;

import de.mpicbg.ulman.ctc.Mastodon.util.ImgProviders;

@Plugin( type = Command.class, name = "CTC TRA content reviewer @ Mastodon" )
public class TRAreviewPlugin
extends DynamicCommand
{
	// ----------------- necessary internal references -----------------
	@Parameter
	private LogService logService;

	@Parameter(persist = false)
	private MamutAppModel appModel;

	// ----------------- where to read data in -----------------
	@Parameter(label = "Review from this time point:", min="0")
	Integer timeFrom;

	@Parameter(label = "Review till this time point:", min="0")
	Integer timeTill;

	// ----------------- where the issues are and how to navigate to them -----------------
	/** the list of "suspicious" spots */
	private RefList< Spot > problemList;

	/** the list of descriptions of the "suspicious" spots (why they are deemed suspicious) */
	private ArrayList< String > problemDesc;

	/** the currently investigated spot */
	private int currentProblemIdx = -1;

	private void navToProblem()
	{
		if (problemList == null || problemDesc == null) return;
		if (currentProblemIdx < 0) currentProblemIdx = 0;
		if (currentProblemIdx >= problemList.size()) currentProblemIdx = problemList.size()-1;

		problemList.get( currentProblemIdx, oSpot );
		appModel.getFocusModel().focusVertex(oSpot);
		appModel.getSelectionModel().clearSelection();
		appModel.getSelectionModel().setSelected(oSpot,true);
		appModel.getHighlightModel().highlightVertex(oSpot);

		if (pbar != null) pbar.setProgress(currentProblemIdx);
		if (pMsg != null) pMsg.setText( problemDesc.get(currentProblemIdx) );
	}

	private ProgressIndicator pbar = null;
	private JLabel pMsg = null;

	//shared "proxy" objects, allocated and released in run()
	private Spot nSpot,oSpot;
	private Link linkRef;

	// ----------------- how to read data in -----------------
	@Override
	public void run()
	{
		//TODO: provide the view choosing dialog
		final ImgProviders.ImgProvider imgSource
			= new ImgProviders.ImgProviderFromMastodon(appModel.getSharedBdvData().getSources().get(0).getSpimSource(),timeFrom);

		logService.info("Considering resolution: "+imgSource.getVoxelDimensions().dimension(0)
		               +" x "+imgSource.getVoxelDimensions().dimension(1)
		               +" x "+imgSource.getVoxelDimensions().dimension(2)
		               +" px/"+imgSource.getVoxelDimensions().unit());

		//define some shortcut variables
		final Model model = appModel.getModel();
		final ModelGraph modelGraph = model.getGraph();

		//debug report
		logService.info("Time points span is   : "+timeFrom+"-"+timeTill);

		//transformation used
		final AffineTransform3D coordTransImg2World = new AffineTransform3D();

		//some more dimensionality-based attributes
		final int inImgDims = imgSource.numDimensions();
		final int[] position = new int[inImgDims];

		//volume and squared lengths of one voxel along all axes
		final double[] resSqLen  = new double[inImgDims];
		imgSource.getVoxelDimensions().dimensions(resSqLen);
		double resArea   = resSqLen[0] * resSqLen[1]; //NB: lengths are yet before squaring
		double resVolume = 1;
		for (int i=0; i < inImgDims; ++i)
		{
			resVolume *= resSqLen[i];
			resSqLen[i] *= resSqLen[i];
		}


		//allocate the shared proxy objects
		linkRef = modelGraph.edgeRef();
		nSpot = modelGraph.vertices().createRef();
		oSpot = modelGraph.vertices().createRef();

		//release the shared proxy objects
		class MyWindowAdapter extends WindowAdapter
		{
			@Override
			public void windowClosing(WindowEvent e)
			{
				windowClosing( (JFrame)null );
			}

			public void windowClosing(final JFrame closeThisFrameToo)
			{
				modelGraph.vertices().releaseRef(oSpot);
				modelGraph.vertices().releaseRef(nSpot);
				modelGraph.releaseRef(linkRef);
				if (closeThisFrameToo != null) closeThisFrameToo.dispose();
				logService.info("Reviewing closed.");
			}
		}
		final MyWindowAdapter releaseRoutine = new MyWindowAdapter();


		//PROGRESS BAR stuff
		//  - will use it first to show progress of the detection of problematic cases
		//  - will use it after to show progress of the review process
		//populate the bar and show it
		final JFrame pbframe = new JFrame("CTC TRA Reviewer Progress Bar @ Mastodon");
		pbframe.setLayout(new BoxLayout(pbframe.getContentPane(), BoxLayout.Y_AXIS));

		pbar = new ProgressIndicator("Issues reviewed: ", "", 0, 1, false);
		pbframe.add(pbar);

		pMsg = new JLabel("");
		pbframe.add(pMsg);

		final JPanel pbtnPanel = new JPanel();
		pbtnPanel.setLayout(new BoxLayout(pbtnPanel, BoxLayout.X_AXIS));

		Button pbtn = new Button("Previous issue");
		pbtn.addActionListener( (action) -> { --currentProblemIdx; navToProblem(); } );
		pbtnPanel.add(pbtn);

		pbtn = new Button("Next issue");
		pbtn.addActionListener( (action) -> { ++currentProblemIdx; navToProblem(); } );
		pbtnPanel.add(pbtn);

		pbtn = new Button("Stop reviewing");
		pbtn.addActionListener( (action) -> releaseRoutine.windowClosing( pbframe ) );
		pbtnPanel.add(pbtn);
		pbframe.add(pbtnPanel);

		pbframe.addWindowListener(releaseRoutine);
		pbframe.setMinimumSize(new Dimension(300, 140));
		pbframe.pack();
		pbframe.setLocationByPlatform(true);
		pbframe.setVisible(true);
		//PROGRESS BAR stuff


		//create the problem list
		problemList = RefCollections.createRefList( appModel.getModel().getGraph().vertices(),1000);
		problemDesc = new ArrayList<>(1000);

		int fakeChooser = 0;

		pbar.setMinimum(timeFrom);
		pbar.setMaximum(timeTill);

		final SpatioTemporalIndex< Spot > spots = model.getSpatioTemporalIndex();

		for (int timePoint = timeFrom; timePoint <= timeTill; ++timePoint)
		for ( final Spot spot : spots.getSpatialIndex( timePoint ) )
		{
			++fakeChooser;
			if (fakeChooser %3 == 0)
			{
				problemList.add( spot );
				problemDesc.add( "fake no. "+fakeChooser);
			}

			pbar.setProgress(timePoint+1);
		}

		if (problemList.size() > 0)
		{
			//init the "dialog"
			logService.info("Detected "+problemList.size()+" possible problems.");
			pbar.setMinimum(0);
			pbar.setMaximum(problemList.size()-1);
			currentProblemIdx = 0;
			navToProblem();
		}
		else
		{
			//just clean up and quit
			releaseRoutine.windowClosing( pbframe );
			logService.info("No problems detected, done.");
		}
	}
}
