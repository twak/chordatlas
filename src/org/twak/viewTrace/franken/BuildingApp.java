package org.twak.viewTrace.franken;

import java.awt.Graphics;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;

import org.twak.tweed.gen.SuperEdge;
import org.twak.tweed.gen.SuperFace;
import org.twak.tweed.gen.skel.AppStore;
import org.twak.tweed.gen.skel.SiteplanDesigner;
import org.twak.tweed.gen.skel.SkelGen;
import org.twak.utils.collections.MultiMap;
import org.twak.utils.geom.HalfMesh2.HalfEdge;
import org.twak.utils.ui.AutoCheckbox;
import org.twak.utils.ui.ListDownLayout;

public class BuildingApp extends App {

	public SkelGen parent;
	public boolean createDormers = Math.random() < 0.5;
	public String chimneyTexture;
	public SuperFace superFace;
	public boolean isDirty;
	
	public BuildingApp( SuperFace superFace ) {
		super( );
		this.superFace = superFace;
	}

	public BuildingApp( BuildingApp buildingApp ) {
		super (buildingApp);
		this.parent = buildingApp.parent;
		this.superFace = buildingApp.superFace;
	}

	@Override
	public App copy() {
		return new BuildingApp( this );
	}

	@Override
	public App getUp(AppStore ac) {
		return ac.get ( BlockApp.class, parent );
	}

	@Override
	public MultiMap<String, App> getDown(AppStore ac) {
		
		MultiMap<String, App> down = new MultiMap<>();
		
		down.put( "roof",  ac.get( RoofGreebleApp.class, superFace.mr ) ); 
		
		for (HalfEdge e : superFace) 
			down.put ( "facade", ac.get(FacadeLabelApp.class, ((SuperEdge)e).toEdit ) );
		
		return down;
	}

	@Override
	public void computeBatch( Runnable whenDone, List<App> batch, AppStore appCache ) {
		whenDone.run();
	}
	
	@Override
	public JComponent createUI( Runnable globalUpdate, SelectedApps apps ) {
		JPanel out = new JPanel(new ListDownLayout());
		
		addRemoveButton (globalUpdate, out);
		
		out.add (new AutoCheckbox( this, "createDormers", "dormers" ) {
			@Override
			public void updated( boolean selected ) {
				updateDormers(selected, apps.ass);
				globalUpdate.run();
			}
		});
		
		JButton siteplan = new JButton( "edit plan/profile" );
		siteplan.addActionListener( e -> new SiteplanDesigner( superFace, apps.ass.get ( BlockApp.class, parent ).skelGen ) );
		out.add(siteplan);
		
		return out;
	}
	
	private void addRemoveButton( Runnable globalUpdate, JPanel out ) {
		JButton remove = new JButton( "remove building" );
		remove.addActionListener( e -> {
			parent.block.faces.remove( superFace );
			parent.calculateOnJmeThread();
		} );
		out.add( remove );
	}

	public void updateDormers(boolean dormers, AppStore ac) {
		createDormers = dormers;
	}
	
	@Override
	public void markDirty( AppStore ac ) {
		this.isDirty = true;
		super.markDirty( ac );
	}
}
