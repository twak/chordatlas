package org.twak.viewTrace.franken;

import java.awt.Graphics;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JPanel;

import org.twak.tweed.gen.SuperEdge;
import org.twak.tweed.gen.SuperFace;
import org.twak.tweed.gen.skel.SkelGen;
import org.twak.utils.collections.MultiMap;
import org.twak.utils.geom.HalfMesh2.HalfEdge;
import org.twak.utils.ui.AutoCheckbox;
import org.twak.utils.ui.ListDownLayout;

public class BuildingApp extends App {

	public SkelGen parent;
	public boolean createDormers = Math.random() < 0.5;
	public String chimneyTexture;
	
	public BuildingApp( SuperFace superFace ) {
		super( superFace );
	}

	public BuildingApp( BuildingApp buildingApp ) {
		super (buildingApp);
		this.parent = buildingApp.parent;
	}

	@Override
	public App copy() {
		return new BuildingApp( this );
	}

	@Override
	public App getUp() {
		return parent.app;
	}

	@Override
	public MultiMap<String, App> getDown() {
		
		MultiMap<String, App> down = new MultiMap<>();
		
		SuperFace sf = (SuperFace)hasA;
		
		down.put( "roof",  sf.mr.app.greebles ); 
		
		for (HalfEdge e : sf) 
			down.put ( "facade", ((SuperEdge)e).toEdit.appLabel );
		
		return down;
	}

	@Override
	public void computeBatch( Runnable whenDone, List<App> batch ) {
		whenDone.run();
	}
	
	@Override
	public JComponent createUI( Runnable globalUpdate, SelectedApps apps ) {
		JPanel out = new JPanel(new ListDownLayout());
		
		out.add (new AutoCheckbox( this, "createDormers", "dormers" ) {
			@Override
			public void updated( boolean selected ) {
				updateDormers(selected);
				globalUpdate.run();
			}
		});
		
		return out;
	}
	
	public void updateDormers(boolean dormers) {

		createDormers = dormers;
		
		for (HalfEdge e: (SuperFace)hasA ) 
			((SuperEdge)e).toEdit.app.dormer = createDormers;	
	}
}
