package org.twak.viewTrace.franken;

import java.util.List;

import javax.swing.JComponent;

import org.twak.tweed.gen.SuperFace;
import org.twak.tweed.gen.skel.AppStore;
import org.twak.tweed.gen.skel.SkelGen;
import org.twak.utils.collections.MultiMap;
import org.twak.utils.geom.HalfMesh2.HalfFace;
import org.twak.viewTrace.franken.style.JointStyle;

public class BlockApp extends App {

	SkelGen skelGen;
	
	public BlockApp( BlockApp buildingApp ) {
		super (buildingApp);
		this.skelGen = buildingApp.skelGen;
	}

	public BlockApp( SkelGen skelGen ) {
		super ( );
		this.skelGen = skelGen;
	}

	@Override
	public App copy() {
		return new BlockApp( this );
	}

	@Override
	public App getUp(AppStore ac) {
		return null;
	}

	@Override
	public MultiMap<String, App> getDown(AppStore ac) {
		
		MultiMap<String, App> down = new MultiMap<>();

		for (HalfFace sf : skelGen.block) 
			down.put( "building", ac.get(BuildingApp.class, sf ) );
		
		return down;
	}

	@Override
	public void computeBatch( Runnable whenDone, List<App> batch, AppStore appCache ) {
		whenDone.run();
	}
	
	@Override
	public JComponent createNetUI( Runnable globalUpdate, SelectedApps apps ) {
		return super.createNetUI( globalUpdate, apps );
	}
}
