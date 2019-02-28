package de.mpicbg.ulman.Mastodon;


import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import org.scijava.command.Command;
import org.scijava.command.ContextCommand;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.Parameter;

@Plugin( type = Command.class )
public class TestingPlugin <T extends NativeType<T> & RealType<T>>
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

	@Parameter
	T voxelType;

	@Override
	public void run()
	{
		System.out.println("TestingPlugin(): ");
		System.out.println("outputPath = "+outputPath);
		System.out.println("filePrefix = "+filePrefix);
		System.out.println("filePostfix = "+filePostfix);
		System.out.println("fileNoDigits = "+fileNoDigits);
		if (voxelType != null)
			System.out.println("voxelType = "+voxelType.getClass().getSimpleName());
		else
			System.out.println("voxelType = null");
	}
}
