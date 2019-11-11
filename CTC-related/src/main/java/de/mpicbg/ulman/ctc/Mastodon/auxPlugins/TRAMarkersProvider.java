package de.mpicbg.ulman.ctc.Mastodon.auxPlugins;

import java.util.concurrent.ExecutionException;
import org.scijava.command.CommandService;
import mpicbg.spim.data.sequence.VoxelDimensions;
import de.mpicbg.ulman.ctc.Mastodon.auxPlugins.TRAMarkers.*;

public class TRAMarkersProvider
{
	public
	interface intersectionDecidable
	{
		default void init() {}

		void setHalfBBoxInterval(final double[] halfBBoxSize, final double radius);
		boolean isInside(final double[] distVec, final double radius);

		default String printInfo() { return toString(); }
	}

	public static final
	String[] availableChoices = {
		"Spheres of spot-driven radii",
		"Spheres of fixed radius",
		"Boxes of fixed shape" };

	public static
	intersectionDecidable TRAMarkerFactory(final String choice, final VoxelDimensions pxSize, final CommandService cs)
	{
		intersectionDecidable markerShape;

		//sanity branch...
		if (choice == null || cs == null)
		{
			markerShape = new SpheresWithFloatingRadius();
			markerShape.init();
			return markerShape;
		}

		final String resHint =
			pxSize == null? "Unknown image resolution"
			: ( "Image resolution is: " +pxSize.dimension(0)
			                      +" x "+pxSize.dimension(1)
			                      +" x "+pxSize.dimension(2)
			                      +" "+pxSize.unit()+"/px" );

		//the main branch where 'choice' param is taken seriously...
		try
		{
			if (choice.startsWith("Boxes"))
				markerShape = (intersectionDecidable)cs.run(BoxesWithFixedShape.class,true,"resolutionHint",resHint).get().getCommand();
			else if (choice.contains("fixed"))
				markerShape = (intersectionDecidable)cs.run(SpheresWithFixedRadius.class,true,"resolutionHint",resHint).get().getCommand();
			else
				markerShape = new SpheresWithFloatingRadius();

			markerShape.init();
		}
		catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
			return null;
		}

		return markerShape;
	}
}
