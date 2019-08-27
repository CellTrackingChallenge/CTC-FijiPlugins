package de.mpicbg.ulman.ctc.Mastodon.auxPlugins;

import org.scijava.plugin.Plugin;
import org.scijava.plugin.Parameter;
import org.scijava.command.Command;
import org.scijava.widget.FileWidget;

import java.io.File;

@Plugin( type = Command.class, name = "Provide images location and filename template" )
public class FileTemplateProvider implements Command
{
	@Parameter(label = "Folder with images:", style = FileWidget.DIRECTORY_STYLE)
	File containingFolder;

	@Parameter(label = "Template for file names:",
	           description = "Use %d or %04d in the template to denote where numbers or 4-digits-zero-padded numbers will appear.")
	String filenameTemplate = "img%03d.tif";

	@Parameter(label = "Lineage TXT file name:")
	String filenameTXT = "res_track.txt";

	@Override
	public void run() { /* intentionally empty */ }
}
