package de.mpicbg.ulman.ctc.Mastodon;

import static org.mastodon.app.ui.ViewMenuBuilder.item;
import static org.mastodon.app.ui.ViewMenuBuilder.menu;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mastodon.app.ui.ViewMenuBuilder;
import org.mastodon.plugin.MastodonPlugin;
import org.mastodon.plugin.MastodonPluginAppModel;
import org.mastodon.revised.mamut.MamutAppModel;

import org.scijava.log.LogService;
import org.scijava.AbstractContextual;
import org.scijava.command.CommandService;
import org.scijava.plugin.Plugin;
import org.scijava.ui.behaviour.util.Actions;
import org.scijava.ui.behaviour.util.AbstractNamedAction;
import org.scijava.ui.behaviour.util.RunnableAction;
import net.imglib2.type.numeric.integer.UnsignedShortType;

@Plugin( type = CTC_Plugins.class )
public class CTC_Plugins extends AbstractContextual implements MastodonPlugin
{
	//"IDs" of all plug-ins wrapped in this class
	private static final String CTC_IMPORT = "CTC-import-all";
	private static final String CTC_EXPORT = "CTC-export-all";
	private static final String CTC_TRA_CHECKER = "CTC-reviewTRA";
	//------------------------------------------------------------------------

	@Override
	public List< ViewMenuBuilder.MenuItem > getMenuItems()
	{
		//this places the plug-in's menu items into the menu,
		//the titles of the items are defined right below
		return Arrays.asList(
				menu( "Plugins",
						menu( "Cell Tracking Challenge",
								item( CTC_IMPORT ), item ( CTC_EXPORT), item ( CTC_TRA_CHECKER) ) ) );
	}

	/** titles of this plug-in's menu items */
	private static Map< String, String > menuTexts = new HashMap<>();
	static
	{
		menuTexts.put( CTC_IMPORT, "Import from CTC format" );
		menuTexts.put( CTC_EXPORT, "Export to CTC format" );
		menuTexts.put( CTC_TRA_CHECKER, "Review TRA annotation" );
	}

	@Override
	public Map< String, String > getMenuTexts()
	{
		return menuTexts;
	}
	//------------------------------------------------------------------------

	private final AbstractNamedAction actionImport;
	private final AbstractNamedAction actionExport;
	private final AbstractNamedAction actionTRAreview;

	/** default c'tor: creates Actions available from this plug-in */
	public CTC_Plugins()
	{
		actionImport = new RunnableAction( CTC_IMPORT, this::importer );
		actionExport = new RunnableAction( CTC_EXPORT, this::exporter );
		actionTRAreview = new RunnableAction( CTC_TRA_CHECKER, this::TRAreviewer );
		updateEnabledActions();
	}

	/** register the actions to the application (with no shortcut keys) */
	@Override
	public void installGlobalActions( final Actions actions )
	{
		final String[] noShortCut = new String[] { "not mapped" };
		actions.namedAction( actionImport, noShortCut );
		actions.namedAction( actionExport, noShortCut );
		actions.namedAction( actionTRAreview, noShortCut );
	}

	/** reference to the currently available project in Mastodon */
	private MastodonPluginAppModel pluginAppModel;

	/** learn about the current project's params */
	@Override
	public void setAppModel( final MastodonPluginAppModel model )
	{
		//the application reports back to us if some project is available
		this.pluginAppModel = model;
		updateEnabledActions();
	}

	/** enables/disables menu items based on the availability of some project */
	private void updateEnabledActions()
	{
		final MamutAppModel appModel = ( pluginAppModel == null ) ? null : pluginAppModel.getAppModel();
		actionImport.setEnabled( appModel != null );
		actionExport.setEnabled( appModel != null );
		actionTRAreview.setEnabled( appModel != null );
	}
	//------------------------------------------------------------------------

	/** opens the import dialog to find the tracks.txt file,
	    and runs the import on the currently viewed images
	    provided params were harvested successfully */
	private void importer()
	{
		this.getContext().getService(CommandService.class).run(
			ImporterPlugin.class, true,
			"appModel", pluginAppModel.getAppModel(),
			"logService", this.getContext().getService(LogService.class));
	}

	/** opens the export dialog, and runs the export
	    provided params were harvested successfully */
	private void exporter()
	{
		this.getContext().getService(CommandService.class).run(
			ExporterPlugin.class, true,
			"outImgVoxelType", new UnsignedShortType(),
			"appModel", pluginAppModel.getAppModel(),
			"logService", this.getContext().getService(LogService.class));
	}


	/** returns a handle on a TRA folder that exists in the given
	    input 'folder', or throws IllegalArgumentException exception */
	private File enterTRAFolder(final File folder)
	{
		//check there is a TRA sub-folder; and if not, create it
		final File traFolder = new File(folder.getPath()+File.separator+"TRA");
		if (traFolder.exists())
		{
			//"move" into the existing TRA folder
			return traFolder;
		}
		else
		{
			if (traFolder.mkdirs()) //create and enter it
				return traFolder;
			else
				throw new IllegalArgumentException("Cannot create missing subfolder TRA in the folder: "+folder.getAbsolutePath());
		}
	}


	/** opens ...  */
	private void TRAreviewer()
	{
		this.getContext().getService(CommandService.class).run(
			TRAreviewPlugin.class, true,
			"appModel", pluginAppModel.getAppModel(),
			"logService", this.getContext().getService(LogService.class));
	}
}
