package de.mpicbg.ulman.ctc.Mastodon;

import java.awt.*;
import javax.swing.*;

import org.jhotdraw.samples.svg.gui.ProgressIndicator;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

import org.mastodon.collection.RefCollections;
import org.mastodon.collection.RefIntMap;
import org.mastodon.collection.RefList;
import org.mastodon.collection.RefMaps;
import org.mastodon.model.FocusListener;
import org.mastodon.spatial.SpatioTemporalIndex;
import org.mastodon.grouping.GroupHandle;
import org.mastodon.app.ui.GroupLocksPanel;
import org.scijava.log.LogService;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.Parameter;

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

	// ----------------- what the issues look like -----------------
	@Parameter(label = "Review all roots:")
	boolean reviewRoots = false;

	@Parameter(label = "Review all daughters (after division):")
	boolean reviewDaughters = true;

	@Parameter(label = "Review trajectory relative bending above this angle (deg):",
	           description = "Relative = direction predicted - direction observed. Set to 180 to (effectively) disable.",
	           min="0", max="180")
	float maxToleratedRelativeAngle = 180;

	@Parameter(label = "Review trajectory absolute bending above this angle (deg):",
	           description = "Absolute = direction observed now - direction observed previously. Set to 180 to (effectively) disable.",
	           min="0", max="180")
	float maxToleratedAbsoluteAngle = 180;

	@Parameter(label = "NN: How many nearest neighbors to consider maximally:",
	           description = "Set to 0 to formally disable this suite of tests.",
	           min="0")
	int neighbrMaxCnt = 0;

	@Parameter(label = "NN: Maximum distance to neighbors to consider them (um):",
	           description = "Set to 0 to effectively disable this suite of tests.",
	           min="0")
	float neighbrMaxDist = 12.0f;

	@Parameter(label = "NN: Magnitude of distance change to trigger alarm (um):", min="0")
	float neighbrDistDelta = 5.0f;

	@Parameter(label = "NN: Minimum count of alarms for a spot to review it:", min="1",
	           description = "Set between 1 and number of considered neighbors, set above to effectively disable this test.")
	int neighbrDistAlarmsCnt = 5;

	@Parameter(label = "NN: Minimum count of neighboring track changes around a spot to review it:", min="1",
	           description = "Set between 1 and number of considered neighbors, set above to effectively disable this test.")
	int neighbrLabelAlarmsCnt = 5;

	@Parameter(label = "NN: Minimum distance to nearest spot to review it (um):", min="0",
	           description = "Set to 0 to disable this test.")
	float neighbrAlarmMinDist = 6.0f;

	@Parameter(label = "Find and (re)navigate to a just selected spot:")
	boolean navigateToClickedSpot = false;

	@Parameter(label = "Save trajectory stats:", description="Leave empty to disable this.")
	String statsFile = "";

	// ----------------- where the issues are and how to navigate to them -----------------
	/** the list of "suspicious" spots */
	private RefList< Spot > problemList;

	/** the list of descriptions of the "suspicious" spots (why they are deemed suspicious) */
	private ArrayList< String > problemDesc;

	/** the currently investigated spot */
	private int currentProblemIdx = -1;

	/** this, essentially, gives access to the current view grouping configuration;
	    and allows to notify the other views (TrackScheme or BDV) to show the `currentProblemIdx` */
	private GroupHandle myGroupHandle = null;

	private void navToProblem()
	{
		if (problemList == null || problemDesc == null) return;
		if (currentProblemIdx < 0) currentProblemIdx = 0;
		if (currentProblemIdx >= problemList.size()) currentProblemIdx = problemList.size()-1;

		problemList.get( currentProblemIdx, oSpot );
		appModel.getHighlightModel().highlightVertex(oSpot);
		if (myGroupHandle != null)
			myGroupHandle.getModel(appModel.NAVIGATION).notifyNavigateToVertex(oSpot);

		if (pbar != null) pbar.setProgress(currentProblemIdx);
		if (pMsg != null) pMsg.setText( problemDesc.get(currentProblemIdx) );
	}

	private FocusListener tryToNavToProblem = new FocusListener()
	{
		@Override
		public void focusChanged()
		{
			appModel.getFocusModel().getFocusedVertex(fSpot);
			//System.out.println("clicked on "+fSpot.getLabel());

			int newIdx = problemList.lastIndexOf(fSpot);
			if (newIdx > -1)
			{
				currentProblemIdx = newIdx;
				navToProblem();
			}
		}
	};

	private ProgressIndicator pbar = null;
	private JLabel pMsg = null;

	//shared "proxy" objects, allocated and released in run()
	private Spot nSpot,oSpot,fSpot;
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
		               +" "+imgSource.getVoxelDimensions().unit()+"/px");

		//define some shortcut variables
		final Model model = appModel.getModel();
		final ModelGraph modelGraph = model.getGraph();

		//debug report
		logService.info("Time points span is   : "+timeFrom+"-"+timeTill);

		//allocate the shared proxy objects
		linkRef = modelGraph.edgeRef();
		nSpot = modelGraph.vertices().createRef();
		oSpot = modelGraph.vertices().createRef();
		if (navigateToClickedSpot)
		{
			fSpot = modelGraph.vertices().createRef();
			appModel.getFocusModel().listeners().add( tryToNavToProblem );
		}
		myGroupHandle = appModel.getGroupManager().createGroupHandle();

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
				appModel.getGroupManager().removeGroupHandle( myGroupHandle );
				if (navigateToClickedSpot)
				{
					appModel.getFocusModel().listeners().remove( tryToNavToProblem );
					modelGraph.vertices().releaseRef(fSpot);
				}
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

		pbframe.add( new GroupLocksPanel( myGroupHandle ) );

		pbar = new ProgressIndicator("Issues reviewed: ", "", 0, 1, false);
		pbframe.add(pbar);

		pMsg = new JLabel("");
		pMsg.setMinimumSize(new Dimension(290,50));
		pMsg.setHorizontalTextPosition(JLabel.LEFT);
		pbframe.add(pMsg);

		final JPanel pbtnPanel = new JPanel();
		pbtnPanel.setLayout(new BoxLayout(pbtnPanel, BoxLayout.X_AXIS));

		Button pbtn = new Button("Select all issues");
		pbtn.addActionListener( (action) ->
			{
				appModel.getSelectionModel().clearSelection();
				for (final Spot s : problemList)
					appModel.getSelectionModel().setSelected(s,true);
			} );
		pbtnPanel.add(pbtn);

		pbtn = new Button("Previous issue");
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
		pbframe.setMinimumSize(new Dimension(300, 180));
		pbframe.pack();
		pbframe.setLocationByPlatform(true);
		pbframe.setVisible(true);
		//PROGRESS BAR stuff


		//create the problem list
		problemList = RefCollections.createRefList( appModel.getModel().getGraph().vertices(),1000);
		problemDesc = new ArrayList<>(1000);

		pbar.setMinimum(timeFrom);
		pbar.setMaximum(timeTill);

		//track labeling business
		spot2track = RefMaps.createRefIntMap( modelGraph.vertices(), -1, 1000 );
		parentalTracks = RefMaps.createRefIntMap( modelGraph.vertices(), -1, 500 );
		int lastTrackId = 0;

		final SpatioTemporalIndex< Spot > spots = model.getSpatioTemporalIndex();
		final RefList< Spot > rootsList = RefCollections.createRefList( appModel.getModel().getGraph().vertices(),1000);

		for (int timePoint = timeFrom; timePoint <= timeTill; ++timePoint)
		for ( final Spot spot : spots.getSpatialIndex( timePoint ) )
		{
			//find how many back-references (time-wise) this spot has
			int countBackwardLinks = 0;
			int countForwardLinks = 0;
			for (int n=0; n < spot.incomingEdges().size(); ++n)
			{
				spot.incomingEdges().get(n, linkRef).getSource( oSpot );
				if (oSpot.getTimepoint() < timePoint && oSpot.getTimepoint() >= timeFrom) ++countBackwardLinks;
				if (oSpot.getTimepoint() > timePoint && oSpot.getTimepoint() <= timeTill) ++countForwardLinks;
			}
			for (int n=0; n < spot.outgoingEdges().size(); ++n)
			{
				spot.outgoingEdges().get(n, linkRef).getTarget( oSpot );
				if (oSpot.getTimepoint() < timePoint && oSpot.getTimepoint() >= timeFrom) ++countBackwardLinks;
				if (oSpot.getTimepoint() > timePoint && oSpot.getTimepoint() <= timeTill) ++countForwardLinks;
			}
			if (countBackwardLinks == 0)
			{
				rootsList.add(spot);
				spot2track.put(spot,++lastTrackId);
				if (reviewRoots) enlistProblemSpot(spot, "root");
			}
			if (countForwardLinks > 1)
			{
				for (int n=0; n < spot.incomingEdges().size(); ++n)
				{
					spot.incomingEdges().get(n, linkRef).getSource( oSpot );
					if (oSpot.getTimepoint() > timePoint && oSpot.getTimepoint() <= timeTill)
					{
						rootsList.add(oSpot);
						spot2track.put(oSpot,++lastTrackId);
						parentalTracks.put(oSpot, spot2track.get(spot));
						if (reviewDaughters)
							enlistProblemSpot(oSpot, "daughter");
					}
				}
				for (int n=0; n < spot.outgoingEdges().size(); ++n)
				{
					spot.outgoingEdges().get(n, linkRef).getTarget( oSpot );
					if (oSpot.getTimepoint() > timePoint && oSpot.getTimepoint() <= timeTill)
					{
						rootsList.add(oSpot);
						spot2track.put(oSpot,++lastTrackId);
						parentalTracks.put(oSpot, spot2track.get(spot));
						if (reviewDaughters)
							enlistProblemSpot(oSpot, "daughter");
					}
				}
			}

			pbar.setProgress(timePoint+1);
		}

		logService.info("Found "+rootsList.size()+" tracks, "+parentalTracks.size()
		                +" out of which are daughter tracks (twice the amount of divisions...)");

		//label all tracks first
		for (int n=0; n < rootsList.size(); ++n)
		{
			//cache the label of the current track
			rootsList.get(n,oSpot);
			lastTrackId = spot2track.get(oSpot);

			while (getLastFollower(oSpot, nSpot) == 1)
			{
				spot2track.put(nSpot, lastTrackId);
				oSpot.refTo( nSpot );
			}
		}

		try {
		final BufferedWriter f
			= statsFile.length() > 0 ? new BufferedWriter( new FileWriter(statsFile) ) : null;

		final double toDeg = 180.0 / 3.14159;
		final double[] axis = new double[3];

		final double[] vec1 = new double[3];
		final double[] vec2 = new double[3];
		final double[] vec3 = new double[3];

		final double[] referenceDistances = new double[neighbrMaxCnt];
		final double[] testDistances = new double[neighbrMaxCnt];
		final double[] referenceLabels = new double[neighbrMaxCnt]; //could be int, but type issues with noOfDifferentArrayElems()
		final double[] testLabels = new double[neighbrMaxCnt];

		//finally, process the tracks and search for anomalies
		for (int n=0; n < rootsList.size(); ++n)
		{
			final Spot spot = rootsList.get(n);
			if (getLastFollower(spot, nSpot) != 1) continue;
			if (getLastFollower(nSpot, oSpot) != 1) continue;

			if (f != null) f.write("# from spot: "+spot.getLabel()+" @ tp "+spot.getTimepoint()+"\n");

			//so, we have a chain here: spot -> nSpot -> oSpot
			 spot.localize(vec1);
			nSpot.localize(vec2);
			oSpot.localize(vec3);

			//deltaPos: nSpot -> oSpot
			vec3[0] -= vec2[0]; vec3[1] -= vec2[1]; vec3[2] -= vec2[2];

			//deltaPos: spot -> nSpot
			vec2[0] -= vec1[0]; vec2[1] -= vec1[1]; vec2[2] -= vec1[2];

			//logService.info("1->2: "+printVector(vec2));
			//logService.info("2->3: "+printVector(vec3));

			double angle = getRotationAngleAndAxis(vec2, vec3, axis);
			if (angle*toDeg > maxToleratedAbsoluteAngle)
				enlistProblemSpot(oSpot, "3rd spot: angle "+(angle*toDeg)+" deg too much");

			//logService.info("rot params: "+(angle*toDeg)+" deg around "+printVector(axis));

			//DEBUG CHECK:
			vec1[0] = vec2[0]; vec1[1] = vec2[1]; vec1[2] = vec2[2];
			rotateVector(vec1, axis,angle);
			if (f != null) f.write("# debug check: test ang = "+getRotationAngle(vec1,vec3)+" (should be close to 0.0)\n");

			if (f != null) f.write("# abs. angle test at spot "+oSpot.getLabel()+": "+(angle*toDeg)+" (seen) vs. "+maxToleratedAbsoluteAngle+" (tolerated)\n");
			if (f != null) f.write("# time, predicted-observed diff angle (deg), observed now-prev diff angle (deg), displacement length, expected dir to this spot, observed dir to this spot[, distances to neighbors], spot label at this time\n");

			//neighbors test: initialization
			if (neighbrMaxCnt > 0)
				findNearestNeighbors(oSpot,spots,imgSource.getDimensionOfOneUnitOfWorldCoordinates(), referenceDistances,referenceLabels);

			while (getLastFollower(oSpot, nSpot) == 1)
			{
				//BTW: oSpot -> nSpot
				//last dir is: vec3
				//last rot params: axis, angle

				//last dir is also vec2
				vec2[0] = vec3[0]; vec2[1] = vec3[1]; vec2[2] = vec3[2];

				//new dir is vec3
				oSpot.localize(vec1);
				nSpot.localize(vec3);
				//deltaPos: oSpot -> nSpot
				vec3[0] -= vec1[0]; vec3[1] -= vec1[1]; vec3[2] -= vec1[2];

				//last dir is also vec1
				vec1[0] = vec2[0]; vec1[1] = vec2[1]; vec1[2] = vec2[2];

				//predict relative direction of the next nSpot, and compare to the actual one
				rotateVector(vec1, axis,angle);
				angle = getRotationAngle(vec1, vec3);
				if (angle*toDeg > maxToleratedRelativeAngle)
					enlistProblemSpot(nSpot, "relative angle "+(angle*toDeg)+" deg too much");

				if ((getRotationAngle(vec2,vec3)*toDeg) > maxToleratedAbsoluteAngle)
					enlistProblemSpot(nSpot, "absolute angle "+(getRotationAngle(vec2,vec3)*toDeg)+" deg too much");

				//neighbors test
				if (neighbrMaxCnt > 0)
				{
					findNearestNeighbors(nSpot,spots,imgSource.getDimensionOfOneUnitOfWorldCoordinates(), testDistances,testLabels);
					int alarms = noOfDifferentArrayElems(testDistances,referenceDistances,neighbrDistDelta);
					if (alarms >= neighbrDistAlarmsCnt)
						enlistProblemSpot(nSpot, alarms+" neighbors have different distance");
					if (neighbrAlarmMinDist > 0 && testDistances[0] < neighbrAlarmMinDist)
						enlistProblemSpot(nSpot, "too close ("+testDistances[0]+" um) to other spot");

					//TODO replace daughters' labels with mother's label
					alarms = noOfDifferentSetElems(testLabels,referenceLabels,0);
					if (alarms >= neighbrLabelAlarmsCnt)
						enlistProblemSpot(nSpot, alarms+" neighboring tracks are now different");
				}

				if (f != null)
				{
					f.write(nSpot.getTimepoint()+"\t"
						+(angle*toDeg)+"\t"
						+(getRotationAngle(vec2,vec3)*toDeg)+"\t"
						+Math.sqrt(vec3[0]*vec3[0] + vec3[1]*vec3[1] + vec3[2]*vec3[2])+"\t"
						+printVector(vec1)+"\t"
						+printVector(vec3)+"\t");
					for (int i=0; i < testDistances.length; ++i) f.write(testDistances[i]+"\t");
					for (int i=0; i < testLabels.length; ++i) f.write(testLabels[i]+"\t");
					f.write(nSpot.getLabel()+"\n");
				}

				//update the rot params, reference distances, and move to the next spot
				angle = getRotationAngleAndAxis(vec2, vec3, axis);
				for (int i=0; i < referenceDistances.length; ++i) referenceDistances[i] = testDistances[i];
				for (int i=0; i < referenceLabels.length; ++i)    referenceLabels[i]    = testLabels[i];
				oSpot.refTo( nSpot );
			}

			if (f != null) f.write("\n\n");
		}

		if (f != null) f.close();
		} catch (IOException e) {
			e.printStackTrace();
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

	private void enlistProblemSpot(final Spot spot, final String reason)
	{
		problemList.add( spot );
		problemDesc.add( spot.getLabel()+": "+reason );
	}

	/** returns the number of detected followers and returns
	    the last visited one (if there was at least one) */
	int getLastFollower(final Spot from, final Spot retFollower)
	{
		int followers = 0;
		for (int n=0; n < from.incomingEdges().size(); ++n)
		{
			from.incomingEdges().get(n, linkRef).getSource( retFollower );
			if (retFollower.getTimepoint() > from.getTimepoint() && retFollower.getTimepoint() <= timeTill) ++followers;
		}
		for (int n=0; n < from.outgoingEdges().size(); ++n)
		{
			from.outgoingEdges().get(n, linkRef).getTarget( retFollower );
			if (retFollower.getTimepoint() > from.getTimepoint() && retFollower.getTimepoint() <= timeTill) ++followers;
		}
		return followers;
	}


	/** returns angle (in radians) and rotAxis that would transform the fromVec to toVec */
	double getRotationAngleAndAxis(final double[] fromVec, final double[] toVec, final double[] rotAxis)
	{
		//rotation axis
		rotAxis[0] = fromVec[1]*toVec[2] - fromVec[2]*toVec[1];
		rotAxis[1] = fromVec[2]*toVec[0] - fromVec[0]*toVec[2];
		rotAxis[2] = fromVec[0]*toVec[1] - fromVec[1]*toVec[0];

		//normalized rotation axis
		final double rotAxisLen = Math.sqrt(rotAxis[0]*rotAxis[0] + rotAxis[1]*rotAxis[1] + rotAxis[2]*rotAxis[2]);
		rotAxis[0] /= rotAxisLen;
		rotAxis[1] /= rotAxisLen;
		rotAxis[2] /= rotAxisLen;

		//rotation angle
		return getRotationAngle(fromVec,toVec);
	}

	double getRotationAngle(final double[] fromVec, final double[] toVec)
	{
		double dotProd = fromVec[0]*toVec[0] + fromVec[1]*toVec[1] + fromVec[2]*toVec[2];
		dotProd /= Math.sqrt(fromVec[0]*fromVec[0] + fromVec[1]*fromVec[1] + fromVec[2]*fromVec[2]);
		dotProd /= Math.sqrt(  toVec[0]*  toVec[0] +   toVec[1]*  toVec[1] +   toVec[2]*  toVec[2]);
		return Math.min( Math.acos(dotProd), 3.14159 ); //limit to PI to fight numerical inaccuracies
	}

	void rotateVector(final double[] vec, final double[] rotAxis, final double rotAng)
	{
		if (Math.abs(rotAng) < 0.05) //smaller than 3 deg
		{
			rotMatrix[0] = 1; rotMatrix[1] = 0; rotMatrix[2] = 0;
			rotMatrix[3] = 0; rotMatrix[4] = 1; rotMatrix[5] = 0;
			rotMatrix[6] = 0; rotMatrix[7] = 0; rotMatrix[8] = 1;
		}
		else
		{
			//quaternion params
			final double rAng = rotAng / 2.0;
			final double q0 = Math.cos(rAng);
			final double q1 = Math.sin(rAng) * rotAxis[0];
			final double q2 = Math.sin(rAng) * rotAxis[1];
			final double q3 = Math.sin(rAng) * rotAxis[2];

			//rotation matrix from the quaternion
			//        row col
			rotMatrix[0*3 +0] = q0*q0 + q1*q1 - q2*q2 - q3*q3;
			rotMatrix[0*3 +1] = 2 * (q1*q2 - q0*q3);
			rotMatrix[0*3 +2] = 2 * (q1*q3 + q0*q2);

			//this is the 2nd row of the matrix...
			rotMatrix[1*3 +0] = 2 * (q2*q1 + q0*q3);
			rotMatrix[1*3 +1] = q0*q0 - q1*q1 + q2*q2 - q3*q3;
			rotMatrix[1*3 +2] = 2 * (q2*q3 - q0*q1);

			rotMatrix[2*3 +0] = 2 * (q3*q1 - q0*q2);
			rotMatrix[2*3 +1] = 2 * (q3*q2 + q0*q1);
			rotMatrix[2*3 +2] = q0*q0 - q1*q1 - q2*q2 + q3*q3;
		}

		//rotate the input vector
		double x = rotMatrix[0]*vec[0] + rotMatrix[1]*vec[1] + rotMatrix[2]*vec[2];
		double y = rotMatrix[3]*vec[0] + rotMatrix[4]*vec[1] + rotMatrix[5]*vec[2];
		  vec[2] = rotMatrix[6]*vec[0] + rotMatrix[7]*vec[1] + rotMatrix[8]*vec[2];
		  vec[0] = x;
		  vec[1] = y;
	}
	private double[] rotMatrix = new double[9];

	public static String printVector(final double[] vec)
	{
		return ("("+vec[0]+","+vec[1]+","+vec[2]+")");
	}

	/** fills the full arrays 'nearestDistances' and 'nearestLabels' */
	private void findNearestNeighbors(final Spot aroundThisSpot,
	                                  final SpatioTemporalIndex< Spot > allSpots,
	                                  final double pxSize,
	                                  final double[] nearestDistances,
	                                  final double[] nearestLabels)
	{
		//fixed position of the investigated spot
		aroundThisSpot.localize(neigPosA);

		//compute all distances to all other spots,
		//and remember its track label to every distance
		allNeighDistances.clear();
		for ( final Spot spot : allSpots.getSpatialIndex( aroundThisSpot.getTimepoint() ) )
		{
			//skip over the same spot
			if (spot.getInternalPoolIndex() == aroundThisSpot.getInternalPoolIndex()) continue;

			//enlist the squared distance of the current spot to the reference one
			spot.localize(neigPosB);
			neigPosB[0] -= neigPosA[0]; //isotropic px distance
			neigPosB[1] -= neigPosA[1];
			neigPosB[2] -= neigPosA[2];
			neigPosB[0] *= pxSize;      //um distance
			neigPosB[1] *= pxSize;
			neigPosB[2] *= pxSize;
			neigPosB[0] *= neigPosB[0]; //um squared distance
			neigPosB[1] *= neigPosB[1];
			neigPosB[2] *= neigPosB[2];
			allNeighDistances.add( neigPosB[0]+neigPosB[1]+neigPosB[2] );
			allNeighLabels[allNeighDistances.size()-1] = spot2track.get(spot);
		}

		//NB: designation of invalid index value
		allNeighLabels[999] = -1;

		//fill the output arrays
		double bestCurrDist, bestLastDist = 0, dist;
		int    bestCurrIdx;
		for (int i=0; i < nearestDistances.length; ++i)
		{
			bestCurrDist = inftyDistanceConstant;
			bestCurrIdx  = 999;
			for (int j=0; j < allNeighDistances.size(); ++j)
			{
				dist = allNeighDistances.get(j);
				if (dist > bestLastDist && dist < bestCurrDist)
				{
					bestCurrDist = dist;
					bestCurrIdx  = j;
				}
			}

			nearestDistances[i] = Math.sqrt(bestCurrDist);
			nearestLabels[i]    = allNeighLabels[bestCurrIdx];

			//"erase"/disable any element that is associated with a spot
			//that is beyond the search threshold
			if (nearestDistances[i] > neighbrMaxDist)
			{
				nearestDistances[i] = inftyDistanceConstant;
				nearestLabels[i]    = -1;
			}

			bestLastDist = bestCurrDist;
		}
	}
	final ArrayList<Double> allNeighDistances = new ArrayList<>(1000);
	final double[] neigPosA = new double[3];
	final double[] neigPosB = new double[3];
	final double inftyDistanceConstant = 999999999;

	//track labeling business
	final int[] allNeighLabels = new int[1000]; //hopefully there'll be no more spots per time point
	RefIntMap< Spot > spot2track, parentalTracks;

	private int noOfDifferentArrayElems(final double[] testArray, final double[] referenceArray,
	                                    final double threshold)
	{
		int alarmsCnt = 0;
		for (int i=0; i < referenceArray.length; ++i)
		{
			if (referenceArray[i] == inftyDistanceConstant || testArray[i] == inftyDistanceConstant) continue;
			if (Math.abs(referenceArray[i]-testArray[i]) > threshold) ++alarmsCnt;
		}

		return alarmsCnt;
	}
	/*
	private <N extends Number>
	int noOfDifferentArrayElems(final N[] testArray, final N[] referenceArray,
	                            final double threshold)
	{
		int alarmsCnt = 0;
		for (int i=0; i < referenceArray.length; ++i)
		{
			final double rVal = referenceArray[i].doubleValue();
			final double tVal = testArray[i].doubleValue();
			if (rVal == inftyDistanceConstant || tVal == inftyDistanceConstant) continue;
			if (Math.abs(rVal-tVal) > threshold) ++alarmsCnt;
		}

		return alarmsCnt;
	}
	*/

	private int noOfTestSetElemsNotInRefSet(final double[] testArray, final double[] referenceArray,
	                                        final double threshold)
	{
		int alarmsCnt = 0;

		//for every sensible element from the test set...
		for (int i=0; i < testArray.length; ++i)
		if (testArray[i] != inftyDistanceConstant)
		{
			boolean foundMatch = false;
			//... we check whether it is included anywhere in the reference set
			// (again, anywhere within the subset of sensible content only)
			for (int j=0; j < referenceArray.length && !foundMatch; ++j)
			if (referenceArray[j] != inftyDistanceConstant
			 && Math.abs(referenceArray[j]-testArray[i]) <= threshold) foundMatch = true;

			if (!foundMatch) ++alarmsCnt;
		}

		return alarmsCnt;
	}

	private int noOfDifferentSetElems(final double[] testArray, final double[] referenceArray,
	                                  final double threshold)
	{
		//how many test set elems are not present in the reference set,
		//and the same in the switched-role case
		return ( noOfTestSetElemsNotInRefSet(testArray,referenceArray,threshold)
		       + noOfTestSetElemsNotInRefSet(referenceArray,testArray,threshold) );
	}
}
